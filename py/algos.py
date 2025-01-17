import colorsys
import math


def _normalize(d):
  def normalize(v):
    total = sum(v[1::2])
    return [vv / total if i % 2 else vv for i, vv in enumerate(v)]
  return {k: normalize(v) for k, v in d.items()}


_GRADIENTS = _normalize({
    'bw': [(0, 0, 0), 1, (1, 1, 1), 1],
    'rgb': [(1, 0, 0), 1, (0, 1, 0), 1, (0, 0, 1), 1],
    'warmth': [(1, 0, 0), 2, (1, 0.5, 0), 1, (1, 0.8, 0.2), 1, (0.8, 0.3, 0.5), 2],
    'deepsea': [(0, 0.5, 1), 2, (0, 0.2, 0.8), 1, (0, 0.1, 0.4), 1, (0, 0.3, 0.7), 2],
    'neon': [(1, 0, 1), 1, (0, 1, 1), 1, (1, 1, 0), 1, (1, 0, 0.5), 1],
    'forest': [(0, 0.8, 0), 2, (0.2, 0.6, 0), 1, (0, 0.4, 0.2), 1, (0.1, 0.5, 0.1), 2],
    'aurora': [(0, 1, 0.5), 1, (0, 0.8, 1), 1, (0.5, 0, 1), 1, (0, 1, 0.8), 1],
    # https://claude.ai/chat/9c5a35bd-ebaa-4ec1-805c-87de60925506
    'noodles': [
        # red=(246, 82, 63) : max, min
        (255/255, 0/255, 204/255), 1,
        (0/255, 51/255, 0/255), 1,
        # green=(62, 173, 137) : max, min
        (0/255, 255/255, 51/255), 1,
        (51/255, 0/255, 0/255), 1,
        # blue=(10, 91, 174) : max, min
        (0/255, 0/255, 153/255), 1,
        (51/255, 0/255, 0/255), 1,
        # yellow=(253, 211, 15) : max, min
        (102/255, 153/255, 0/255), 1,
        (0/255, 0/255, 51/255), 1,
    ],
    'noodles2': [
        # red=(246, 82, 63)
        (246/246, 82/246, 63/246), 1,
        # green=(62, 173, 137)
        (62/173, 173/173, 137/173), 1,
        # blue=(10, 91, 174)
        (10/174, 91/174, 174/174), 1,
        # yellow=(253, 211, 15)
        (253/253, 211/253, 15/253), 1,
    ],
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


_hue_int = 0

def to_rgb(sd, *, gradient, algorithm, param1, param2, param3):
  global _hue_int
  if algorithm in ('gx_gy', 'gy_gz', 'gz_gx', 'gx_gy_gz'):
    a1, a2, *a3ff = algorithm.split('_')
    phi = math.atan2(getattr(sd, a1), getattr(sd, a2))
    if a3ff == ['gz']:
      _hue_int += sd.gz * 0.01 * param1
      phi += _hue_int
    value = (phi / (2.0 * math.pi) + 0.5)
  elif algorithm == 'z_rot':
    if sd.rz < -param2 or sd.rz > param2:
      _hue_int += sd.rz
    value = _hue_int * 0.1 * param1
  else:
    raise ValueError(f'Unknown algorithm: {algorithm}')

  return _get_rgb(value, gradient)
