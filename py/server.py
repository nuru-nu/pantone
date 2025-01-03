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
import ipaddress
import os
import pathlib
import socket
import struct
import tempfile
import weakref

import aiofiles
import aiohttp.web
import netifaces

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
    alpha=1.0,
    brightness=1.0,
    device='eurolite',
    gradient='hue',
    algorithm='gx_gy',
    param1=1.0, param2=1.0, param3=1.0,
)
PRESERVED_STATE = {'alpha', 'brigthness', 'device', 'gradient', 'algorithm', 'param1', 'param2', 'param3'}
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


class UDPProtocol:
  def __init__(self, queue):
    self.queue = queue
    self.transport = None
    self.logger = logging.getLogger('UDPProtocol')
    self._closed = False
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
      except:  # noqa: E722
        pass
      self.transport = None

  def datagram_received(self, data, addr):
    if self._closed:
      return
    if len(data) != 36:
      self.logger.warning(f'Received invalid packet size from {addr}: {len(data)} bytes')
      return

    t = int(1000 * (datetime.datetime.now().timestamp() - t0))
    addr = '{}:{}'.format(*addr)

    try:
      values = struct.unpack('>9f', data)  # Android: big-endian
      sd = SensorData(*values)
    except Exception as e:
      self.logger.error(f'Error processing packet from {addr}: {e}')

    asyncio.create_task(self.queue.put((t, addr, sd)))


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


active_ws_connections = set()


async def data_ws(request):
  logger = logging.getLogger('DataWs')
  ws = aiohttp.web.WebSocketResponse()
  await ws.prepare(request)

  data_manager = request.app['data_manager']
  data_manager.add_client(ws)

  active_ws_connections.add(ws)
  try:
    async for msg in ws:
      del msg
  except Exception as e:
    logger.error(f'DataWs error: {e}')
  finally:
    active_ws_connections.remove(ws)
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

  active_ws_connections.add(ws)
  try:
    async for msg in ws:
      del msg
  except Exception as e:
    logger.error(f'StateWs error: {e}')
  finally:
    active_ws_connections.remove(ws)
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


def find_wireless_interface():
  for iface in netifaces.interfaces():
    addrs = netifaces.ifaddresses(iface)
    if netifaces.AF_INET in addrs:  # Has IPv4
      inet_info = addrs[netifaces.AF_INET][0]
      if 'addr' in inet_info:
        if any(pattern in iface.lower() for pattern in [
            # e.g. "wlan" on Raspbian, "en" on OS X
            'wlan', 'en', 'wifi', 'wireless', 'wl',
        ]):
          return iface
  return None


def get_broadcast_addr(interface=None):
  if interface is None:
    interface = find_wireless_interface()
    if not interface:
      return None

  addrs = netifaces.ifaddresses(interface)
  if netifaces.AF_INET in addrs:
    inet_info = addrs[netifaces.AF_INET][0]
    ip = inet_info['addr']
    netmask = inet_info['netmask']
    network = ipaddress.IPv4Network(f'{ip}/{netmask}', strict=False)
    return str(network.broadcast_address)
  return None


async def osc_handler(running, queue, data_manager, state_manager, data_file, hz=60):
  logger = logging.getLogger('osc_handler')
  osc_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  osc_address = ('localhost', 7770)

  emas = {}
  rgbs = {}
  sds = {}
  ts = {}
  active = None

  def get_ema(value, ema):
    # return value * 0.5 + ema * 0.5
    return value * state['alpha'] + (1 - state['alpha']) * ema

  while running.is_set():
    loop_t0 = datetime.datetime.now().timestamp()

    # first update rgbs etc from sensor data
    updated = set()
    while not queue.empty():
      t, addr, sd = await queue.get()
      if addr in updated:
        logger.warning('discarding message from %s', addr)
      updated.add(addr)

      if addr not in state['clients']:
        state['clients'].append(addr)
        d = dict(clients=state['clients'])
        asyncio.create_task(state_manager.broadcast(json.dumps(d).encode()))

      sds[addr] = sd
      rgbs[addr] = olad.to_rgb(
          sd,
          gradient=state['gradient'],
          algorithm=state['algorithm'],
          param1=state['param1'],
          param2=state['param2'],
          param3=state['param3'],
      )
      ts[addr] = t
      active = get_active(addr, t)
      if active != state['active']:
        state['active'] = active
        d = dict(active=state['active'])
        asyncio.create_task(state_manager.broadcast(json.dumps(d).encode()))

    # then sync update of emas, ws, and olad if active sensor
    for addr, sd in sds.items():
      rgb = rgbs[addr]
      rgb = emas[addr] = tuple(map(get_ema, rgb, emas.get(addr, rgb)))
      t = ts[addr]

      ws_msg = struct.pack('>L', t)  # 32 bits = 49.71 days of milliseconds
      ws_msg += struct.pack('B', state['clients'].index(addr))
      ws_msg += struct.pack('>7f', sd.gx, sd.gy, sd.gz, sd.rz, *rgb)
      asyncio.create_task(data_manager.broadcast(ws_msg))
      if data_file:
        asyncio.create_task(data_file.write(ws_msg))

      if active == addr:
        msg = olad.to_osc(*rgb, brightness=state['brightness'], device=state['device'])
        try:
          osc_socket.sendto(msg, osc_address)
        except Exception as e:
          logger.error(f'Error forwarding OSC packet: {e}')

    wait_dt = 1 / hz - (datetime.datetime.now().timestamp() - loop_t0)
    if wait_dt > 0:
      await asyncio.sleep(wait_dt)
    else:
      logger.warning('wait_dt = %.2fms < 0', wait_dt * 1e3)

  logger.inf('stopping')

  try:
    osc_socket.close()
  except:  # noqa: E722
    pass


async def periodic_handler(running):
  logger = logging.getLogger('periodic_handler')
  broadcast_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  broadcast_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
  broadcast_sock.setblocking(False)

  broadcast_addr = get_broadcast_addr()
  if not broadcast_addr:
    logger.error('No valid broadcast address found')
    return
  logger.info('Using broadcast address %s', broadcast_addr)

  try:
    while running.is_set():
      msg = b'PANTONE1'
      broadcast_sock.sendto(msg, (broadcast_addr, UDP_BROADCAST_PORT))
      logger.debug(f'Broadcast ping {msg} sent')

      with tempfile.NamedTemporaryFile(mode='w', delete=False, dir=os.path.dirname(STATE_FILE)) as tmp:
        tmp_name = tmp.name
        json.dump(serialized(state), tmp, indent=2)
        tmp.flush()
        os.fsync(tmp.fileno())
      os.replace(tmp_name, STATE_FILE)

      await asyncio.sleep(5.0)

    logger.info('stopping')
  except Exception as e:
    logger.error(f"Broadcast error: {e}")
    broadcast_sock.close()


async def main():
  args = parse_args()

  timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
  setup_logging(timestamp, debug=args.debug)
  logger = logging.getLogger(__name__)
  logger.info('Starting server')

  if os.path.exists(STATE_FILE):
    logger.info('Loading state from %s', STATE_FILE)
    try:
      state.update(json.load(open(STATE_FILE)))
    except json.JSONDecodeError as e:
      logger.error('Could not load state: %s', e)

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
  queue = asyncio.Queue()

  transport, protocol = await loop.create_datagram_endpoint(
      lambda: UDPProtocol(queue),
      local_addr=('0.0.0.0', UDP_IMU_PORT)
  )
  del protocol

  app_runner = aiohttp.web.AppRunner(app)
  await app_runner.setup()
  site = aiohttp.web.TCPSite(app_runner, '0.0.0.0', HTTP_PORT)
  await site.start()

  logger.info('All servers started')

  running = asyncio.Event()
  running.set()
  try:
    await asyncio.gather(
        asyncio.Event().wait(),  # run forever
        periodic_handler(running),
        osc_handler(running, queue, data_manager, state_manager, data_file),
    )
  finally:
    running.clear()
    transport.close()
    for ws in active_ws_connections.copy():
        await ws.close(code=aiohttp.WSCloseCode.GOING_AWAY,  message='Server shutdown')
    await app_runner.cleanup()
    await data_file.close()
    logger.info('Server shutdown complete')


if __name__ == '__main__':
  os.chdir(os.path.dirname(os.path.abspath(__file__)))
  os.makedirs('logs', exist_ok=True)
  asyncio.run(main())
