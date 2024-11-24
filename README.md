# Hello Gravity

Simple Android app to control light by motion.

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
   manually via `sudo service olad stop` followed by
   `/usr/bin/olad --config-dir /etc/ola --log-level 4`
3. OLA web UI: Make sure USB Serial plugin is working, since this is the plugin
   handling the "Enttec DMX USB Pro" (`/etc/ola/ola-usbserial.conf`).
4. OLA web UI: Create universe 0, select OSC as input, and Enttec as output. 
5. Configure Parcans to be at `d0001` (or `d0009` etc).

Run it:

1. Get Raspberry Pi address via `ping -c1 dmxserver.local` and update this in
   app's UI.
2. Check `http://dmxserver.local:9090` that we have enttec universe with OSC
   input port.
3. Observe DMX Monitor while running app. Make sure parcan colors match.

## Bluetooth

WARNING: It turned out that Philips Hue style lamps controlled via Bluetooth
scale really poorly. We also tried using a Philips Hue bridge, but that system
also failed to deliver anything resembling real time updates. There is still
some old code for controlling a system via bluetooth, but it has not been kept
up to date with later changes.

Connect lights

1. Activate Bluetooth
2. Connect to lights through Philips Hue app
3. Hue lights now show up as "paired" devices in the Android Bluetooth device list
4. Close Philips Hue app
5. Open Hello Gravity
6. The app should show n=2 if connected to two Philips Hue lights
