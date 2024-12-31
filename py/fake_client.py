# https://claude.ai/chat/a9267c8e-b2a5-4722-a16e-bf815801ff03

import argparse
import asyncio
import math
import random
import socket
import struct
import time


def parse_args():
  parser = argparse.ArgumentParser(
    description='UDP sender with configurable normal distribution sampling',
    formatter_class=argparse.ArgumentDefaultsHelpFormatter
  )

  parser.add_argument('--host', type=str, default='127.0.0.1',
                     help='Target host address')
  parser.add_argument('--port', type=int, default=9001,
                     help='Target UDP port')
  parser.add_argument('--mean', type=float, default=0.0,
                     help='Mean of the normal distribution for values')
  parser.add_argument('--std', type=float, default=1.0,
                     help='Standard deviation of the normal distribution for values')
  parser.add_argument('--freq', type=float, default=20.0,
                     help='Target frequency in Hz')
  parser.add_argument('--jitter', type=float, default=0.001,
                     help='Standard deviation of timing jitter in seconds')
  parser.add_argument('--stats-interval', type=int, default=100,
                     help='Number of messages between frequency stats updates')

  return parser.parse_args()

class UDPSender:
  def __init__(self, host='127.0.0.1', port=12345,
               mean_value=0.0, std_value=1.0,
               target_freq=20.0, jitter_std=0.001,
               stats_interval=100):
    self.host = host
    self.port = port
    self.mean_value = mean_value
    self.std_value = std_value
    self.target_period = 1.0 / target_freq
    self.jitter_std = jitter_std
    self.stats_interval = stats_interval

    self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    self.sent_count = 0
    self.start_time = None

  async def run(self):
    """Main run loop for sending UDP messages"""
    self.start_time = time.time()

    while True:
      try:
        x = time.time()
        g = 9.81
        values = [math.cos(x) * g, math.sin(x) * g] + [0] * 7
        noise = [random.gauss(0, 0.5) for _ in range(len(values))]

        message = struct.pack('>9f', *(value + n for value, n in zip(values, noise)))

        self.sock.sendto(message, (self.host, self.port))
        self.sent_count += 1

        if self.sent_count % self.stats_interval == 0:
          elapsed = time.time() - self.start_time
          actual_freq = self.sent_count / elapsed
          print(f"Actual frequency: {actual_freq:.2f} Hz")

        sleep_time = self.target_period + random.gauss(0, self.jitter_std)
        sleep_time = max(0.001, sleep_time)  # Ensure positive sleep time

        await asyncio.sleep(sleep_time)

      except Exception as e:
        print(f"Error in sender: {e}")
        await asyncio.sleep(1)

  def close(self):
    """Clean up resources"""
    self.sock.close()

async def main():
  args = parse_args()

  sender = UDPSender(
      host=args.host,
      port=args.port,
      mean_value=args.mean,
      std_value=args.std,
      target_freq=args.freq,
      jitter_std=args.jitter,
      stats_interval=args.stats_interval
  )

  try:
    await sender.run()
  except KeyboardInterrupt:
    print("\nStopping sender...")
  finally:
    sender.close()

if __name__ == "__main__":
  asyncio.run(main())