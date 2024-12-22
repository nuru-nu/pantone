# Hello Gravity

Simple Android app that streams sensor data.


## Raspbian Server

Targeting Bookworm. The server receives sensor data from the Android application
and converts it to OSC messages that are sent to `olad` on port 7770.

Installation:

```bash
./install_raspbian.sh
cd py
python3 -m venv env
. env/bin/activate
pip install -r requirements.txt
```

Running the server

```bash
cd py
. env/bin/activate
python server.py
# WIP
python server2.py
```

Then navigate to http://localhost:8000 to see server status and stats.


## DMX: OLA Server

OLA Server can be installed on a Raspberry Pi, and comes shipped with an OSC
plugin. We use a very simple setup, where we send a binary blob via OSC and
those values directly update the DMX channels via a connected Enttec interface
(note that "Enttec DMX USB Pro" interface works out of the box and contains a
chip that emulates a serial interface, while the cheaper "Enttec Opendmx USB"
requires some extra driver and does *not* work out of the box on Linux!)

The setup is as follows:

1. Connect to Rasperry Pi and set up wifis via `raspi-config`.
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

1. Get Raspberry Pi address via `ping -c1 dmxserver.local` and update this in
   app's UI.
2. Check http://dmxserver.local:9090 that we have enttec universe with OSC
   input port.
3. Observe DMX Monitor while running app. Make sure parcan colors match.
4. Main app runs at http://dmxserver.local:8000


## Deprecated

Note that previous versions supported directly sending data to `olad` or even
lights via Bluetooth. Check out the tag `v1` in this repository.
