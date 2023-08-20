import socket
import struct
import sys
import time
# from pythonosc import udp_client

ip = sys.argv[1]
port = 7770

# client = udp_client.SimpleUDPClient(ip, port)  # Create client
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)


def write_string(s, n=4):
  assert isinstance(s, str), s
  b = s.encode()
  diff = n - (len(b) % n)
  b += (b'\x00' * diff)
  return b


def write_blob(x, n=4):
  assert isinstance(x, bytes), x
  b = b''
  b += struct.pack('>i', len(x))
  b += x
  while len(b) % n:
    b += b'\x00'
  return b


def send_message(address, bs):
  b = b''
  b += write_string(address)
  b += write_string(',b')
  b += write_blob(bs)
  sock.sendto(b, (ip, port))


i = 0
while True:
  values = [0] * 128
  for idx in range(10):
    offset = idx * 8
    values[offset] = 255
    values[offset + 1 + i % 3] = 255
  # client.send_message("/dmx/universe/0", bytes(values))
  send_message("/dmx/universe/0", bytes(values))
  time.sleep(1)
  i += 1
