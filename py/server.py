"""Server converting IMU packets to OSC messages, with web simple app.

1. Listens on UDP port UDP_IMU_PORT for raw IMU messages.
2. Converts data to light OSC UDP messages and sends them to localhost:7770
3. Listens on TCP port TCP_SERVER_PORT for client connections. Simple protocol
   with bidirectional JSONL state updates.
4. Web server at port HTTP_PORT with streaming UI.
"""

# https://claude.ai/chat/793e8562-ecef-458d-baee-39f5f397cf61

import argparse
import asyncio
import collections
import datetime
import json
import logging
import os
import pathlib
import socket
import struct
import weakref

import aiofiles
import aiohttp.web

import olad


HTTP_PORT = 8000
UDP_IMU_PORT = 9001
UDP_BROADCAST_PORT = 9002

t0 = datetime.datetime.now().timestamp()

SensorData = collections.namedtuple('SensorData', 'gx, gy, gz, ax, ay, az, rx, ry, rz'.split(', '))

log_file = None

STATE_FILE = 'state.json'
state = dict(
    started=datetime.datetime.now().strftime('%H:%M:%S'),
    clients=[],
    active='',
    algorithm='xy_hue',
    param1=1.0,
)
PRESERVED_STATE = {'algorithm', 'param1'}
serialized = lambda s: {k: v for k, v in s.items() if k in PRESERVED_STATE}  # noqa: E731


last_by_addr = {}
def get_active(addr, ms, limit=1000):
  last_by_addr[addr] = ms
  for k, v in last_by_addr.items():
    if ms - v < limit:
      return k


def parse_args():
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('--debug', action='store_true', help='Enable debug logging')
  return parser.parse_args()


def setup_logging(timestamp, debug=False):
  global log_file
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


smoothened = None
def smooth(values):
  global smoothened
  if not smoothened:
    smoothened = values
  a = state['param1']
  f = lambda s, x: x * a + (1 - a) * s  # noqa: E731
  smoothened = tuple(map(f, smoothened, values))
  return smoothened


class UDPProtocol:
  def __init__(self, data_manager, state_manager, data_file):
    self.data_manager = data_manager
    self.state_manager = state_manager
    self.data_file = data_file
    self.transport = None
    self.logger = logging.getLogger('UDPProtocol')
    self._closed = False
    self.osc_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    self.osc_address = ('localhost', 7770)
    self.logger.info('Created UDPProtocol')

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
      try:
        self.osc_socket.close()
      except:
        pass

  def datagram_received(self, data, addr):
    if self._closed:
      return
    if len(data) != 36:
      self.logger.warning(f'Received invalid packet size from {addr}: {len(data)} bytes')
      return

    t = int(1000 * (datetime.datetime.now().timestamp() - t0))

    addr = '{}:{}'.format(*addr)
    if addr not in state['clients']:
      state['clients'].append(addr)
      d = dict(clients=state['clients'])
      asyncio.create_task(self.state_manager.broadcast(json.dumps(d).encode()))
    client_i = state['clients'].index(addr)

    try:
      values = struct.unpack('>9f', data)  # Android: big-endian
      sd = SensorData(*values)
      rgb = olad.to_rgb(sd, state['algorithm'])
      rgb = smooth(rgb)

      active = get_active(addr, t)
      if active != state['active']:
        state['active'] = active
        d = dict(active=state['active'])
        asyncio.create_task(self.state_manager.broadcast(json.dumps(d).encode()))
      if active == addr:
        msg = olad.to_osc(*rgb)
        try:
          self.osc_socket.sendto(msg, self.osc_address)
        except Exception as e:
          self.logger.error(f'Error forwarding OSC packet: {e}')

      ws_msg = struct.pack('>L', t)  # 32 bits = 49.71 days of milliseconds
      ws_msg += struct.pack('B', client_i)
      ws_msg += struct.pack('>6f', sd.gx, sd.gy, sd.gz, *rgb)
      asyncio.create_task(self.data_manager.broadcast(ws_msg))
      asyncio.create_task(self.data_file.write(ws_msg))
      # await self.data_file.flush()

      # self.logger.debug(f'Received packet from {addr}: {values}')
    except Exception as e:
      self.logger.error(f'Error processing packet from {addr}: {e}')


class WebSocketManager:
  def __init__(self, name):
    self.clients = weakref.WeakSet()
    self.logger = logging.getLogger(f'WebSocketManager[{name}]')

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


async def data_ws(request):
  logger = logging.getLogger('DataWs')
  ws = aiohttp.web.WebSocketResponse()
  await ws.prepare(request)

  data_manager = request.app['data_manager']
  data_manager.add_client(ws)

  try:
    async for msg in ws:
      del msg
  except Exception as e:
    logger.error(f'DataWs error: {e}')
  finally:
    data_manager.remove_client(ws)
    logger.info('DataWs connection closed')
  return ws


async def state_ws(request):
  logger = logging.getLogger('StateWs')
  ws = aiohttp.web.WebSocketResponse()
  await ws.prepare(request)
  await ws.send_bytes(json.dumps(state).encode())

  state_manager = request.app['state_manager']
  state_manager.add_client(ws)

  try:
    async for msg in ws:
      del msg
  except Exception as e:
    logger.error(f'StateWs error: {e}')
  finally:
    state_manager.remove_client(ws)
    logger.info('StateWs connection closed')
  return ws


async def state_post(request):
  state_manager = request.app['state_manager']
  try:
    payload = await request.json()
    if not isinstance(payload, dict):
        raise aiohttp.web.HTTPBadRequest(text='Payload must be a dictionary')
    state.update(payload)
    await state_manager.broadcast(json.dumps(payload).encode())
    return aiohttp.web.json_response(state)
  except json.JSONDecodeError:
      raise aiohttp.web.HTTPBadRequest(text='Invalid JSON payload')


async def logs_get(request):
  return aiohttp.web.Response(
      text=await (await aiofiles.open(log_file)).read(),
      content_type='text/plain',
      charset='utf-8'
  )


async def index_handler(request):
  raise aiohttp.web.HTTPFound('/static/index.html')


async def periodic_handler(logger):
  broadcast_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  broadcast_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
  broadcast_sock.setblocking(False)

  try:
    while True:
      msg = b'PANTONE1'
      broadcast_sock.sendto(msg, ('255.255.255.255', UDP_BROADCAST_PORT))
      logger.debug(f'Broadcast ping {msg} sent')
      async with aiofiles.open(STATE_FILE, 'w') as f:
          await f.write(json.dumps(state, indent=2))
      await asyncio.sleep(5.0)
  except Exception as e:
    logger.error(f"Broadcast error: {e}")
    broadcast_sock.close()


async def main():
  args = parse_args()

  timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
  setup_logging(timestamp, debug=args.debug)
  logger = logging.getLogger(__name__)
  logger.info('Starting server')

  data_file = await aiofiles.open(f'logs/{timestamp}.bin', 'wb')

  data_manager = WebSocketManager('data')
  state_manager = WebSocketManager('state')

  app = aiohttp.web.Application()
  app['data_manager'] = data_manager
  app['state_manager'] = state_manager
  app.router.add_get('/', index_handler)
  app.router.add_get('/logs', logs_get)
  app.router.add_get('/state', state_ws)
  app.router.add_post('/state', state_post)
  app.router.add_get('/data', data_ws)
  app.router.add_static('/static', pathlib.Path('static'))

  loop = asyncio.get_event_loop()

  transport, protocol = await loop.create_datagram_endpoint(
      lambda: UDPProtocol(data_manager, state_manager, data_file),
      local_addr=('0.0.0.0', UDP_IMU_PORT)
  )
  del protocol

  runner = aiohttp.web.AppRunner(app)
  await runner.setup()
  site = aiohttp.web.TCPSite(runner, '0.0.0.0', HTTP_PORT)
  await site.start()

  logger.info('All servers started')

  try:
    await asyncio.gather(
        asyncio.Event().wait(),  # run forever
        periodic_handler(logger),
    )
  finally:
    transport.close()
    await runner.cleanup()
    data_file.close()
    logger.info('Server shutdown complete')


if __name__ == '__main__':
  os.chdir(os.path.dirname(os.path.abspath(__file__)))
  os.makedirs('logs', exist_ok=True)
  if os.path.exists(STATE_FILE):
    state.update(json.load(open(STATE_FILE)))
  asyncio.run(main())
