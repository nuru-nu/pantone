import struct


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

  values = bytearray(64)

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
  elif device == 'vak':
    # outdoor-par
    values[10] = to_value(r * brightness)
    values[11] = to_value(g * brightness)
    values[12] = to_value(b * brightness)
    # vielzuhell
    values[20] = to_value(r * brightness)
    values[21] = to_value(g * brightness)
    values[22] = to_value(b * brightness)
    # battery-par
    values[30] = to_value(r * brightness)
    values[31] = to_value(g * brightness)
    values[32] = to_value(b * brightness)
  else:
    assert ValueError(f'Unknown device={device}')

  return _write_string('/dmx/universe/0') + _write_string(",b") + _write_blob(bytes(values))
