import colorsys
import math
import struct


def _normalize(d):
  def normalize(v):
    total = sum(v[1::2])
    return [vv / total if i % 2 else vv for i, vv in enumerate(v)]
  return {k: normalize(v) for k, v in d.items()}


_GRADIENTS = _normalize({
    'bw': [(0, 0, 0), 1, (1, 1, 1), 1],
    'rgb': [(1, 0, 0), 1, (0, 1, 0), 1, (0, 0, 1), 1],
})


def _interpolate_rgb(c1, c2, t):
  return tuple(a + (b - a) * t for a, b in zip(c1, c2))


def _get_rgb(value, gradient):
  if gradient == 'hue':
    return colorsys.hsv_to_rgb(value, 1.0, 1.0)
  if gradient not in _GRADIENTS:
    raise ValueError(f'Unknown gradient: {gradient}')
  gradient = _GRADIENTS[gradient]
  value %= 1

  colors = gradient[::2]
  distances = gradient[1::2]
  accumulated = 0
  for i, dist in enumerate(distances):
    next_accumulated = accumulated + dist
    if value <= next_accumulated or i == len(distances) - 1:
      color1 = colors[i]
      color2 = colors[(i + 1) % len(colors)]
      segment_value = (value - accumulated) / dist
      return _interpolate_rgb(color1, color2, segment_value)

    accumulated = next_accumulated

  raise ValueError(f'Invalid value: {value}')


_zr_int = 0

def to_rgb(sd, *, gradient, algorithm, param1, param2, param3):
  if algorithm == 'gx_gy':
    phi = math.atan2(sd.gy, sd.gx)
    value = (phi / (2.0 * math.pi) + 0.5)
  elif algorithm == 'z_rot':
    global _zr_int
    if sd.rz < param2 or sd.rz > param2:
      _zr_int += sd.rz
    value = _zr_int * 0.1 * param1
  else:
    raise ValueError(f'Unknown algorithm: {algorithm}')

  return _get_rgb(value, gradient)


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


def to_osc(r, g, b, *, brightness, device):

  values = bytearray(16)

  def to_value(x: float) -> int:
    return max(0, min(255, int(x * 255)))

  if device == 'froggy':
    values[0] = to_value(brightness)
    values[3] = to_value(r)
    values[4] = to_value(g)
    values[5] = to_value(b)
  elif device == 'eurolite':
    # Eurolite LED PARty TCL Spot
    values[0] = to_value(r)
    values[1] = to_value(g)
    values[2] = to_value(b)
    values[3] = to_value(brightness)
  else:
    assert ValueError(f'Unknown device={device}')

  return _write_string('/dmx/universe/0') + _write_string(",b") + _write_blob(bytes(values))
