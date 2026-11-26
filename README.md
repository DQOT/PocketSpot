# PocketSpot

English | [한국어](README.ko.md)

An Android app that turns on the hotspot of the phone in your pocket with one tap on your tablet, then joins that hotspot right away. No root needed.

## Why

- Using a tablet without a SIM on the go means taking out the phone, turning on its hotspot, and connecting the tablet, every single time
- Android's built-in Instant Hotspot is fairly unreliable

## Features

- **One tile to turn it on and off**: tap the "Phone hotspot" tile in the tablet's quick settings and the phone's hotspot turns on. The tile stays on while the tablet is connected to the phone's hotspot; tap it again to turn the hotspot off
- **Prefer phone hotspot**: the tablet moves to the phone's hotspot even when it is on another Wi-Fi network
- **The phone stays in your pocket**: a phone with its screen off picks up the signal within 1-2 seconds. The app does not need to be open
- **No Bluetooth pairing**: the devices only exchange short signed signals. Just enter the same pairing code on both
- **Works with ADB or Shizuku**: regular apps cannot turn on the hotspot, so PocketSpot borrows the permissions of one of the two
- **Uses the phone's own hotspot settings**: name, password and band stay exactly as set on the phone

## How it works

```
Tablet                                   Phone
[Tap tile] ──BLE signal 10 s (signed)──▶ [Bluetooth chip filters and wakes the app]
                                        │ Verify signature
                                        ▼
                                   [Permission helper (ADB or Shizuku)]
                                        │ Turn on hotspot
[Permission helper] ◀──── Wi-Fi ──── [Hotspot]
 Starts joining after 3 s
```

## Requirements

| | Requirement |
|---|---|
| Phone | Android 11 or later, Bluetooth LE, mobile data and hotspot available |
| Tablet | Android 11 or later, supports sending Bluetooth LE signals (advertising) |
| Permission helper | Phone: ADB or Shizuku required. Tablet: only needed for preferring the hotspot and for turning it off from the tile |

- Tested with: Pixel 9 Pro (Android 17) + OPPO Pad mini (ColorOS 17)
- The two devices must be within Bluetooth range of each other

## Install

Download `PocketSpot.apk` from [Releases](../../releases) and install it on **both the phone and the tablet**. Allow the Bluetooth (Nearby devices) permission when you first open it.

## Setup

### 1. Phone

1. Open PocketSpot and choose the role **Hotspot provider (phone)**
2. A pairing code is created automatically. You can change it to a code of your own (8 or more letters and digits)
3. Turn on one permission helper (one of the two below)
4. Allow **Exclude from battery optimization**. The app asks when you choose the role, and there is also a button for it (the ADB helper excludes the app automatically when it starts). Without it, a phone with its screen off may receive the signal but fail to turn on the hotspot
5. Setup is complete when the app shows `ADB helper: Running` or `Shizuku: Permission granted`, `Bluetooth listener: Listening`, and `Battery optimization: Excluded`
6. Recommended: in the phone's hotspot settings, turn on **Turn off hotspot automatically** (when no devices are connected)

#### Permission helper A: ADB

Connect to a PC and run this once. Open the app on the phone once beforehand (the app writes the start script).

```sh
adb shell sh /sdcard/Android/data/io.github.dqot.pocketspot/files/start.sh
```

- The **Copy command** button in the app copies the same command
- Run it again after the phone reboots
- Turning off USB debugging or switching the PC connection to file transfer mode restarts ADB, which may stop the helper

#### Permission helper B: Shizuku

1. Install and start [Shizuku](https://shizuku.rikka.app/) (it can start without a PC through wireless debugging)
2. Tap **Request Shizuku permission** in PocketSpot, then allow

- After a reboot, Shizuku can be restarted on the phone itself, no PC needed
- When both are running, the ADB helper is used first

### 2. Tablet

1. Open PocketSpot and leave the role on **Caller (tablet)**
2. Enter the pairing code shown on the phone exactly as is and tap **Save**
3. Add the **Phone hotspot** tile in Quick Settings edit mode
4. Optional: **Prefer phone hotspot**
   - Turn on a permission helper (ADB or Shizuku) on this tablet as well
   - Turn on "Enable" and enter the phone hotspot's name and password (saved as you type)
   - With this on, the tile shows the connection state, and tapping it while connected turns the hotspot off

Without a helper, the tablet joins one of its saved Wi-Fi networks automatically as usual. If it is already on another Wi-Fi network, at home for example, it stays there.

## Battery

| | Idle | When tapped |
|---|---|---|
| Tablet | Nothing runs | Bluetooth signal for 10 seconds |
| Phone | Low-power filtered scan in the Bluetooth chip (0.5 s about every 5 s), scan registration checked every 15 minutes, helper waiting | The app wakes briefly and sends a request to the helper |

No noticeable battery drain.

## Security

- Signals and helper requests are signed (HMAC-SHA256) with a key derived from the pairing code. Only devices with the same code recognize each other
- Each signal carries a timestamp and a one-time number, so recording it and replaying it later does nothing
- The ADB helper listens on 127.0.0.1 only and handles only correctly signed requests. It reads the key from the app's `KeyProvider`, which answers the shell user only
- The longer and more random the pairing code, the safer. The app's **New code** (16 characters) is recommended. With an easy code such as `1111-1111`, someone nearby could turn your hotspot on and off (they still cannot join without the hotspot password)
- The internet permission is used only to talk to the helper on 127.0.0.1. Nothing is sent anywhere else
- The hotspot password saved on the tablet stays in the app's internal storage and is passed only to the helper's `cmd wifi connect-network`

## Known limitations

- The permission helper stops on reboot (ADB: run it again from a PC, Shizuku: restart it on the phone)
- Turning off USB debugging (developer options) stops the ADB helper and, in most cases, Shizuku too. Some banking apps refuse to run while developer options are on
- It may not work on devices where the manufacturer blocks the hotspot or background Bluetooth scanning
- The signal uses the Bluetooth test company ID (0xFFFF)
- The UI is available in Korean and English only (Korean when the system language is Korean, English otherwise)

## License

[MIT](LICENSE). The bundled Shizuku client libraries are MIT as well ([libs/README.md](libs/README.md)).
