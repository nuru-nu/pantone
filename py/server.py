import datetime
import http.server
import json
import socket
import threading


state = dict(
    started=datetime.datetime.now().strftime('%H:%M:%S'),
    connected=[],
    controlling=None,
    intensity=1.0,
)
lock = threading.Lock()


def update_state(f):
  with lock:
    state.update(f())
    return state


class Handler(http.server.BaseHTTPRequestHandler):

  def notfound(self):
    self.send_response(404)
    self.end_headers()
    self.wfile.write(b'404 - Not Found')

  def send(self, mime, content):
      self.send_response(200)
      self.send_header('Content-type', mime)
      self.end_headers()
      self.wfile.write(content)

  def do_GET(self):
    if self.path == '/':
      return self.send('text/html', open('index.html', 'rb').read())
    elif self.path == '/state':
      return self.send('application/json', json.dumps(state).encode('utf8'))
    else:
      return self.notfound()

  def do_POST(self):
    if self.path != '/state':
      return self.notfound()

    content_length = int(self.headers['Content-Length'])
    update = json.loads(self.rfile.read(content_length).decode('utf-8'))
    reply = json.dumps(update_state(lambda: update))
    return self.send('application/json', reply.encode('utf8'))


def run_http_server(port=8000):
  httpd = http.server.HTTPServer(('', port), Handler)
  print(f"Starting server on port {port}...")
  httpd.serve_forever()


def buffered_read(client_socket):
  data = b''
  while True:
    chunk = client_socket.recv(1024)
    if not chunk:
      return
    data += chunk
    while b'\n' in data:
      line, data = data.split(b'\n', 1)
      yield line


def handle_client(client_socket, client_address):
  send = lambda state: client_socket.sendall(
      (json.dumps(state) + '\n').encode('utf-8'))
  client_address = ':'.join(map(str, client_address))
  send(update_state(lambda: dict(
      connected=state['connected'] + [client_address])))
  try:
    for line in buffered_read(client_socket):
      update = json.loads(line.decode('utf8'))
      send(update_state(lambda: update))
  except Exception as e:
    print('handle_client: Exception: ' + str(e))
  finally:
    update_state(lambda: dict(
        connected=[x for x in state['connected'] if x != client_address]))
    client_socket.close()
    print(f'handle_client: closed connection to {client_address}')


def run_tcp_server(port=9000):
  with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
    server_socket.bind(('', port))
    server_socket.listen()

    print(f'Starting TCP server on port {port}...')
    while True:
      client_socket, client_address = server_socket.accept()
      print(f'Accepted connection from {client_address}')
      client_thread = threading.Thread(
          target=handle_client, args=(client_socket, client_address))
      client_thread.start()


if __name__ == '__main__':
  http_server_thread = threading.Thread(target=run_http_server)
  tcp_server_thread = threading.Thread(target=run_tcp_server)

  http_server_thread.start()
  tcp_server_thread.start()

  http_server_thread.join()
  tcp_server_thread.join()
