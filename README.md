# Hello Gravity

Simple Android app that streams sensor data.


## Server

Targeting Bookworm. The server receives sensor data from the Android application
and converts it to OSC messages that are sent to `olad` on port 7770.

Tested with

- OS X: `olad` can be installed with `brew install olad`
- Raspbian Bookworm: dependencies can be installed with `./install_raspbian.sh`

Then install Python dependencies:

```bash
./install_raspbian.sh
cd py
python3 -m venv env
. env/bin/activate
pip install -r requirements.txt
```

And run the server:

```bash
./py/env/bin/python py/server.py
```

Then navigate to http://localhost:8000 to see server status and stats.


## Gravity Sensors

There are two implementations:

- Android: Run `nu.nuru.hellogravity.MyApplication` from `app/src/main/java`.
- M5Stack Core2: Install `arduino/m5stack_core2/m5stack_core2.ino` via Arduino.
  Note that you need to `cp wifi_credentials.h.example wifi_credentials.h` and
  update the network credentials for this to work.
- Fake client for development: `py/fake_client.py`

In either case, the sensor will connect to WLAN, wait for a UDP broadcast
message sent by `py/server.py` and then start streaming UDP messages with sensor
measurements to the server.


## DMX: OLA Server

OLA Server can be installed on a Raspberry Pi, and comes shipped with an OSC
plugin. We use a very simple setup, where we send a binary blob via OSC and
those values directly update the DMX channels via a connected Enttec interface
(note that "Enttec DMX USB Pro" interface works out of the box and contains a
chip that emulates a serial interface, while the cheaper "Enttec Opendmx USB"
requires some extra driver and does *not* work out of the box on Linux!)

The setup is as follows:

1. Connect to Raspberry Pi and set up wifis via `raspi-config`.
2. For debugging (e.g. checking for OSC messages), OLA server can be started
   manually via `sudo systemctl stop olad` followed by
   `/usr/bin/olad --config-dir /etc/ola --log-level 4`
3. OLA web UI: Make sure USB Serial plugin is working, since this is the plugin
   handling the "Enttec DMX USB Pro" (`/etc/ola/ola-usbserial.conf`).
4. OLA web UI: Create universe 0, select **OSC as input, and Enttec as output**.
   If for some reason the Enttec output device disappears, restart with
   `sudo systemctl restart olad`
5. Configure Parcans to be at `d0001` (or `d0009` etc).

Run it:

1. Run server
2. Verify http://nurupi2.local:8000 is up.
3. Start sensor(s)
4. Check http://nurupi2.local:9090 that we have enttec universe with OSC
   input port.
5. Observe DMX Monitor while running app. Make sure parcan colors match.


## Set up as service (Raspbian)

Make sure to execute this in the repo, after verifying that
`./py/env/bin/python py/server.py` works

```bash
cat <<EOF | sudo tee /etc/systemd/system/pantone.service
[Unit]
Description=Pantone Server
After=network.target

[Service]
Nice=-10
ExecStart=$(pwd)/py/env/bin/python $(pwd)/py/server.py
WorkingDirectory=$(pwd)
User=$USER
Restart=always
RestartSec=3
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=pantone
PAMName=login

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable pantone.service
sudo systemctl start pantone.service
```

Then follow logs with `sudo journalctl -f -u pantone`


## Configure wifi (Raspbian)

### Join existing network

Using Network Manager on Raspbian Bookworm:

```bash
# makes networks visible
sudo iwlist wlan0 scan
# shows visible networks
nmcli device wifi list
# registers a new connection
sudo nmcli device wifi connect "pantone" password "password" name "pantone"
# higher priority means it's preferred ...
sudo nmcli connection modify "pantone" connection.autoconnect-priority 200
# verify networks priorities
nmcli -f name,type,device,autoconnect,autoconnect-priority connection show
# should try network with highest priority first when rebooting...
```

### Set up serial console

Before trying to set the device in hotspot mode it's a good idea to enable USB
serial access to the console:

1. Add `dtoverlay=dwc2` under the `[all]` section in the file
   `/boot/firmware/config.txt`
2. Add `modules-load=dwc2,g_serial` at the end of the line (NOT on a new line!)
   in `/boot/firmware/cmdline.txt`
3. Enable terminal:
   `sudo systemctl enable serial-getty@ttyGS0.service` and then
   `sudo systemctl start serial-getty@ttyGS0.service`
4. After restart, the USB serial device should show up when plugged into a
   host (e.g. at `/dev/tty.usbmodem*` on OS X)
5. Connect via `minicom -c on -D /dev/tty.usbmodem101` and log in.

### Hotspot mode

NOTE: Interface must support `AP` mode (e.g. Raspberry Pi 4 does, but some USB
wifi connectors on Raspberry Pi 2 do not)

```bash
sudo iw list | grep "Supported interface modes" -A 8
```

Install packages and set up config files:

```bash
sudo apt install -y tmux hostapd dnsmasq

cat <<EOF | sudo tee /etc/hostapd/hostapd.conf
interface=wlan0
driver=nl80211
ssid=$(hostname)
hw_mode=g
channel=7
wmm_enabled=0
macaddr_acl=0
auth_algs=1
ignore_broadcast_ssid=0
wpa=2
wpa_passphrase=opensecret
wpa_key_mgmt=WPA-PSK
rsn_pairwise=CCMP
EOF

cat <<EOF | sudo tee /etc/dnsmasq.conf
interface=wlan0
dhcp-range=192.168.4.2,192.168.4.20,255.255.255.0,24h
domain=wlan
address=/gw.wlan/192.168.4.1
EOF
```

Create script to start/stop hotspot mode

```bash
cat <<'EOF' | sudo tee /usr/local/bin/wifi-mode.sh
#!/bin/bash
start_hotspot() {
    # Stop NetworkManager from managing wlan0
    nmcli radio wifi off
    sleep 1  # makes sure `rfkill list wifi` does show "Soft blocked: no"
    rfkill unblock wlan
    # Configure interface manually
    ip link set wlan0 down
    ip addr flush dev wlan0
    ip addr add 192.168.4.1/24 dev wlan0
    ip link set wlan0 up
    # seems we have to stop it first?
    systemctl stop hostapd
    # Start hotspot services
    systemctl start hostapd
    systemctl start dnsmasq
}
stop_hotspot() {
    # Stop hotspot services
    systemctl stop hostapd
    systemctl stop dnsmasq
    # Reset interface
    ip link set wlan0 down
    ip addr flush dev wlan0
    ip link set wlan0 up
    # Let NetworkManager take control again
    nmcli radio wifi on
}
case "$1" in
    start)
        start_hotspot
        ;;
    stop)
        stop_hotspot
        ;;
    *)
        echo "Usage: $0 {start|stop}"
        exit 1
esac
EOF
sudo chmod u+x /usr/local/bin/wifi-mode.sh

# test
sudo /usr/local/bin/wifi-mode.sh start
sudo /usr/local/bin/wifi-mode.sh stop

# debug
sudo hostapd -d /etc/hostapd/hostapd.conf
sudo systemctl status hostapd --no-pager -l
sudo journalctl -u hostapd --no-pager  # would complain if e.g. blocked
```

Finally set up service that enables hotspot at startup

```bash
cat <<'EOF' | sudo tee /etc/systemd/system/wifi-hotspot.service
[Unit]
Description=WiFi Hotspot Service
After=network.target NetworkManager.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/local/bin/wifi-mode.sh start
ExecStop=/usr/local/bin/wifi-mode.sh stop

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl status wifi-hotspot
sudo systemctl start wifi-hotspot
sudo systemctl stop wifi-hotspot
sudo systemctl enable wifi-hotspot
sudo systemctl disable wifi-hotspot
```


## Deprecated

Note that previous versions supported directly sending data to `olad` or even
lights via Bluetooth. Check out the tag `v1` in this repository.

An in-between version was connecting to a TCP server for coordination. This was
eventually replaced by the server announcing itself via UDP broadcast and all
the logic being moved from the sensor application to the Python server.
