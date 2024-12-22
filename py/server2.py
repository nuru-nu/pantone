"""Server converting IMU packets to OSC messages, with web simple app.

1. Listens on UDP port UDP_IMU_PORT for raw IMU messages.
2. Congerts data to light OSC UDP messages and sends them to localhost:7770
3. Listes on TCP port TCP_SERVER_PORT for client connections. Simple protocol
   with bidirectional JSONL state updates.
4. Web server at port HTTP_PORT with streaming UI.
"""

# https://claude.ai/chat/793e8562-ecef-458d-baee-39f5f397cf61

HTTP_PORT = 8000
TCP_SERVER_PORT = 9000
UDP_IMU_PORT = 9001


import argparse
import asyncio
import datetime
import logging
import os
import struct
import weakref

import aiohttp.web
from pathlib import Path


def parse_args():
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('--debug', action='store_true', help='Enable debug logging')
  return parser.parse_args()


def setup_logging(timestamp, debug=False):
  log_file = f'logs/{timestamp}.log'

  logging.basicConfig(
      level=logging.DEBUG if debug else logging.INFO,
      format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
      handlers=[
          logging.FileHandler(log_file),
          logging.StreamHandler()
      ]
  )
  logging.getLogger(__name__).info(f"Logging level set to: {'DEBUG' if debug else 'INFO'}")


class UDPProtocol:
  def __init__(self, websocket_manager, data_file):
    self.websocket_manager = websocket_manager
    self.data_file = data_file
    self.transport = None
    self.logger = logging.getLogger('UDPProtocol')
    self._closed = False

  def connection_made(self, transport):
    self.transport = transport
    self.logger.info('UDP Server started')

  def connection_lost(self, exc):
    self._closed = True
    if exc:
      self.logger.error(f"UDP connection lost with error: {exc}")
    else:
      self.logger.info("UDP connection closed")

    if self.transport:
      try:
        self.transport.close()
      except:
        pass
      self.transport = None

  def datagram_received(self, data, addr):
    if self._closed:
      return
    if len(data) != 36:
      self.logger.warning(f'Received invalid packet size from {addr}: {len(data)} bytes')
      return

    try:
      values = struct.unpack('>9f', data)  # Android: big-endian
      asyncio.create_task(self.websocket_manager.broadcast(struct.pack('<9f', *values)))
      self.data_file.write(data)
      self.data_file.flush()

      # self.logger.debug(f'Received packet from {addr}: {values}')
    except Exception as e:
      self.logger.error(f'Error processing packet from {addr}: {e}')


async def handle_tcp(reader, writer):
  logger = logging.getLogger('TCPServer')
  addr = writer.get_extra_info('peername')
  logger.info(f'New TCP connection from {addr}')

  try:
    while True:
      data = await reader.read(1024)
      if not data:
        break
      logger.debug(f'Received TCP data from {addr}: {data}')
      writer.write(data)
      await writer.drain()
  except Exception as e:
    logger.error(f'Error handling TCP connection from {addr}: {e}')
  finally:
    writer.close()
    await writer.wait_closed()
    logger.info(f'TCP connection closed from {addr}')


class WebSocketManager:
  def __init__(self):
    self.clients = weakref.WeakSet()
    self.logger = logging.getLogger('WebSocketManager')

  def add_client(self, ws):
    self.clients.add(ws)
    self.logger.info(f'New WebSocket client added. Total clients: {len(self.clients)}')

  def remove_client(self, ws):
    self.clients.remove(ws)
    self.logger.info(f'WebSocket client removed. Total clients: {len(self.clients)}')

  async def broadcast(self, data):
    if not self.clients:
      return

    tasks = []
    for ws in self.clients:
      if not ws.closed:
        # self.logger.info(f'relying data to {ws}: {len(data)} bytes')
        tasks.append(asyncio.create_task(ws.send_bytes(data)))

    if tasks:
      await asyncio.gather(*tasks, return_exceptions=True)


async def websocket_handler(request):
  logger = logging.getLogger('WebSocket')
  ws = aiohttp.web.WebSocketResponse()
  await ws.prepare(request)

  websocket_manager = request.app['websocket_manager']
  websocket_manager.add_client(ws)

  try:
    async for msg in ws:
      del msg
  except Exception as e:
    logger.error(f'WebSocket error: {e}')
  finally:
    websocket_manager.remove_client(ws)
    logger.info('WebSocket connection closed')
  return ws


async def index_handler(request):
  return aiohttp.web.FileResponse(Path('static/index.html'))


async def main():
  args = parse_args()

  timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
  setup_logging(timestamp, debug=args.debug)
  logger = logging.getLogger(__name__)
  logger.info('Starting server')

  data_file = open(f'logs/{timestamp}.bin', 'wb')

  websocket_manager = WebSocketManager()

  app = aiohttp.web.Application()
  app['websocket_manager'] = websocket_manager
  app.router.add_get('/', index_handler)
  app.router.add_get('/ws', websocket_handler)
  app.router.add_static('/static', Path('static'))

  loop = asyncio.get_event_loop()

  transport, protocol = await loop.create_datagram_endpoint(
      lambda: UDPProtocol(websocket_manager, data_file),
      local_addr=('0.0.0.0', UDP_IMU_PORT)
  )
  del protocol

  tcp_server = await asyncio.start_server(
      handle_tcp, '0.0.0.0', TCP_SERVER_PORT
  )

  runner = aiohttp.web.AppRunner(app)
  await runner.setup()
  site = aiohttp.web.TCPSite(runner, '0.0.0.0', HTTP_PORT)
  await site.start()

  logger.info('All servers started')

  try:
    await asyncio.Event().wait()  # run forever
  finally:
    transport.close()
    tcp_server.close()
    await tcp_server.wait_closed()
    await runner.cleanup()
    data_file.close()
    logger.info('Server shutdown complete')


if __name__ == '__main__':
  os.chdir(os.path.dirname(os.path.abspath(__file__)))
  os.makedirs('logs', exist_ok=True)
  asyncio.run(main())
