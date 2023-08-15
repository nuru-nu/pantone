from pythonosc import udp_client

ip = '192.168.0.120'
port = 7770
client = udp_client.SimpleUDPClient(ip, port)  # Create client

import time

i = 0
while True:
  values = [0] * 128
  for idx in range(10):
    offset = idx * 8
    values[offset] = 255
    values[offset + 1 + i % 3] = 255
  client.send_message("/dmx/universe/0", bytes(values))
  time.sleep(1)
  i += 1