# Philips Hue Bridge

How to

1. Find bridge address (e.g. from cell phone app: Bridge -> Info)
2. Create "hueser": go to http://192.168.0.119/debug/clip.html,
   press button,  POST `{"devicetype": "my_hue_app#android"}` to "/api";
   should get answer `[{"success": {"username": $hueser"}}]`
3. Verify "hueser": GET `/api/$hueser/lights`, or
   `curl -X PUT -d '{"on":true}' http://$ADDR/api/$HUESER/lights/4/state`


working js web ui: `cd js && python3 -m http.server`

wip ts web ui: `cd ts && npm run`
