# Hub end-to-end check

Runs the hub the way a car head unit would: one host app with network access, several apps without
it, and a server that shows how many TCP connections reached it.

The demo apps:

| Module | Package | Network access | Role |
|---|---|---|---|
| `app-hub-host` | `ly.count.android.demo.hub.host` | INTERNET | runs the hub (`HostHubService`) |
| `app-hub-client` flavor `nav` | `ly.count.android.demo.hub.client.nav` | none | allowed, app key `NAV_APP_KEY` |
| `app-hub-client` flavor `music` | `ly.count.android.demo.hub.client.music` | none | allowed, app key `MUSIC_APP_KEY` |
| `app-hub-client` flavor `rogue` | `ly.count.android.demo.hub.client.rogue` | none | not allowed, tries to send under `NAV_APP_KEY` |

## Steps

1. Start the stand-in server on the development machine. The emulator reaches it as `10.0.2.2`.

   ```
   python sdk-hub/e2e/fake_countly_server.py 8080
   ```

2. Build and install the four apps on a running emulator.

   ```
   ./gradlew :app-hub-host:installDebug :app-hub-client:installNavDebug :app-hub-client:installMusicDebug :app-hub-client:installRogueDebug
   ```

3. Check that the client apps hold no network permission. Only the host should list `android.permission.INTERNET`.

   ```
   adb shell dumpsys package ly.count.android.demo.hub.client.nav | grep -A3 "requested permissions"
   ```

4. Start the host once, then let each client record and send an event. Give each client a few seconds
   before starting the next one, otherwise its activity is covered before it is created.

   ```
   adb shell am start -n ly.count.android.demo.hub.host/.HostActivity
   adb shell am start --activity-single-top -n ly.count.android.demo.hub.client.nav/ly.count.android.demo.hub.client.ClientActivity --es action record
   adb shell am start --activity-single-top -n ly.count.android.demo.hub.client.music/ly.count.android.demo.hub.client.ClientActivity --es action record
   adb shell am start --activity-single-top -n ly.count.android.demo.hub.client.rogue/ly.count.android.demo.hub.client.ClientActivity --es action record
   adb shell am start --activity-single-top -n ly.count.android.demo.hub.client.nav/ly.count.android.demo.hub.client.ClientActivity --es action direct
   ```

5. Check the results.

   - The server prints one line per request. `nav_demo_event` and `music_demo_event` arrive, every
     line shows the same `conn=` address, and `tcp_connections_so_far` stays at 1.
   - Nothing arrives from the rogue app. The hub reports it as an unknown caller:

     ```
     adb shell dumpsys activity service ly.count.android.demo.hub.host/.HostHubService
     ```

   - The direct connection attempt of the nav app fails:

     ```
     adb logcat -d -s HubDemoClient:I
     ... direct connection refused: java.lang.SecurityException: Permission denied (missing INTERNET permission?)
     ```

## On an Android Automotive emulator

The Android 15 automotive image with Google APIs is a userdebug build, so it allows `adb root` for the
socket checks below. It needs Java 17 or newer for the SDK command line tools.

```
sdkmanager "system-images;android-35-ext15;android-automotive;x86_64"
avdmanager create avd -n AAOS35_HUB -k "system-images;android-35-ext15;android-automotive;x86_64" -d automotive_1408p_landscape_with_google_apis
emulator -avd AAOS35_HUB
```

Automotive images run a headless system user 0, and the driver is user 10 (`adb shell am get-current-user`).
`adb install` installs for all users, and `am start` targets the current one, so steps 2 to 5 work unchanged.
A host app that is not part of the system image runs once per user, which means one hub and one server
connection per active driver profile.

Extra checks the automotive image allows:

- The kernel's socket filter knows the clients have no network access. Their app ids are listed as `PERMISSION_NONE`:

  ```
  adb shell pm list packages -U --user 10 | grep demo.hub
  adb shell dumpsys connectivity trafficcontroller | grep -A2000 sUidPermissionMap | grep PERMISSION_NONE
  ```

- Only the host owns a connection to the server, and there is only one:

  ```
  adb root
  adb shell "ss -tnpe | grep :8080"
  ```

On Android 17, an app that targets API level 37 needs the `ACCESS_LOCAL_NETWORK` permission to reach
local addresses such as `10.0.2.2` or a server on the LAN. The demo host targets 37, so this applies when
running it on an Android 17 image against a local server.

## Against a real Countly server

Build all apps with the server address and the app keys of two apps on that server:

```
./gradlew -PhubServerUrl=https://countly.example.com -PhubNavAppKey=<key> -PhubMusicAppKey=<key> :app-hub-host:installDebug :app-hub-client:installNavDebug :app-hub-client:installMusicDebug :app-hub-client:installRogueDebug
```

The clients keep `https://countly.example.com` as their own server URL. The hub ignores it and always
sends to the server it was built with.
