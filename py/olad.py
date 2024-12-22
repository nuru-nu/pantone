import colorsys
import math
import struct


def to_rgb(sd):
  phi = math.atan2(sd.gy, sd.gx)
  hue = (phi / (2.0 * math.pi) + 0.5) * 360

  # Convert HSV to RGB (colorsys uses 0-1 range for hue, not 0-360)
  rgb = colorsys.hsv_to_rgb(hue/360, 1.0, 1.0)
  return rgb


def _write_string(s: str, n: int = 4) -> bytes:
  b = s.encode('utf-8')
  diff = n - (len(b) % n)
  padding = bytes(diff)
  return b + padding


def _write_blob(x: bytes, n: int = 4) -> bytes:
  sz = 4 + len(x)
  if sz % n != 0:
    sz += n - (sz % n)
  result = struct.pack('>I', len(x)) + x
  padding_size = sz - len(result)
  if padding_size > 0:
    result += bytes(padding_size)
  return result


def to_osc(r, g, b, device='eurolite'):

  values = bytearray(16)

  def to_value(x: float) -> int:
    return max(0, min(255, int(x * 255)))

  if device == 'froggy':
    values[0] = 255
    values[3] = to_value(r)
    values[4] = to_value(g)
    values[5] = to_value(b)
  elif device == 'eurolite':
    # Eurolite LED PARty TCL Spot
    values[0] = to_value(r)
    values[1] = to_value(g)
    values[2] = to_value(b)
    values[3] = 255
  else:
    assert ValueError(f'Unknown device={device}')

  return _write_string('/dmx/universe/0') + _write_string(",b") + _write_blob(bytes(values))
