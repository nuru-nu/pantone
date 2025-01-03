import ipaddress

import netifaces



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
