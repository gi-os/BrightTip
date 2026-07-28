# LightTip

Tip calculator and receipt splitter for the **Light Phone III**. Shows up on the phone as
**Tip Calculator**.

Two tabs, switched from the bar at the bottom:

- **Tip** — punch the bill into a POS-style keypad, pick 10 / 15 / 18 / 20 / 22% or type
  your own, read the tip and total off the screen. Works offline.
- **Split** — photograph the receipt. Claude Haiku reads the line items off the paper, you
  tap each item and tick who's on it, and LightTip works out what everyone owes with tax
  and tip shared in proportion to what they actually ordered.

Vision runs on **your own Anthropic API key**, stored on the phone. Nothing else leaves the
device.

## Screenshots

<table>
  <tr>
    <td align="center">
      <img src="docs/screenshots/tip.png" width="250" alt="LightTip tip calculator with a POS-style keypad"><br>
      <sub>Tip: keypad, presets, tip and total</sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/split-items.png" width="250" alt="Receipt line items with initials against each"><br>
      <sub>Split: line items read off the receipt, initials for who is on each</sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/totals.png" width="250" alt="Per-person totals with tax and tip shared in proportion"><br>
      <sub>Totals: tax and tip shared in proportion</sub>
    </td>
  </tr>
</table>

Taken on a Light Phone III.

## Install

Every push to `main` publishes one signed APK as a GitHub Release. Grab the newest from
[Releases](../../releases/latest):

```
adb install -r LightTip-v<version>.apk
```

Or track `https://github.com/gi-os/LightTip` in **Obtainium** — that's the repo URL, not the
GitHub Pages URL below.

The keystore is committed at `keystore/lighttip.jks`, so every build carries the same
certificate and upgrades install over the top. CI pins that certificate's SHA-256 in
`signing-fingerprint.txt` and fails the build if it ever drifts, because a changed cert
surfaces in Obtainium only as an opaque `Failure: Invalid`. Exactly one APK is attached per
release so there is nothing for an updater to pick wrongly; the debug build stays a
workflow artifact.

## First run

1. Open `https://gi-os.github.io/LightTip/`, paste your Anthropic key, and a QR appears.
   The page is entirely client-side — the key is never sent anywhere.
2. On the phone: **gear → Scan QR**. Pasting the key by hand works too.

Only the Split tab needs a key.

## Splitting a bill

1. **SPLIT** tab → **+** → photograph the bill.
2. Wait a second or two while Haiku reads it.
3. Person icon → add everyone at the table. Names are remembered between bills, so the
   regular crowd is one tap next time.
4. Tap an item, tick everyone sharing it. A shared item divides evenly among whoever is ticked.
5. **TOTALS** → per-person breakdown of items, tax and tip.

Tip defaults to 20% on the pre-tax subtotal and is adjustable per receipt. Items nobody
claims stay out of everyone's share and get reported separately at the bottom of Totals, so
a forgotten line can't quietly vanish.

Every figure is held in cents, and tax and tip are allocated by largest remainder, so the
per-person totals always add back up to the bill exactly — no stray pennies.

## Notes for the LPIII panel

- The screen is **greyscale on matte glass**. Selected tip chips invert rather than tint,
  because hue is discarded and a low-alpha fill disappears.
- Surfaces are true black with no tonal elevation, so 1dp rules separate regions and the
  dialogs get an explicit dark-grey fill — a scrim over black tints nothing.
- The display is roughly **411 × 472 dp**, normal width and about half the usual height.
  The keypad takes the leftover vertical space rather than a fixed fraction of the screen.
- Text uses Akkurat when LightOS provides it, so the app matches the system UI.

## Why this isn't a LightOS SDK tool

The [Light SDK](https://github.com/lightphone/light-sdk) sandbox rejects CameraX and blocks
`LocalContext` outright, and `READ_MEDIA_IMAGES` is not on its permission allowlist — so a
sanctioned SDK tool cannot photograph anything. LightTip is a plain sideloaded APK for the
same reason [LightPass](https://github.com/gi-os/LightPass) is.

## Building

```
./gradlew :app:assembleDebug
```

The launcher icon is generated, not hand-drawn — geometry lives in
`scripts/generate_icon.py` and is emitted as both adaptive vector layers and raster
fallbacks. Edit it there and re-run `python3 scripts/generate_icon.py`; the script
asserts the mark stays inside the adaptive safe zone.

## The gi-os Light App collection

Twelve tools for the Light Phone III, all open source, all built in one run.

| Tool | What it does | Built on |
| --- | --- | --- |
| [LightPass](https://github.com/gi-os/LightPass) | Photograph a movie ticket, keep the stub | Plain Android |
| [LightQR](https://github.com/gi-os/LightQR) | QR scanner, plus a browser generator | Plain Android |
| [LightRSS](https://github.com/gi-os/LightRSS) | RSS and Atom reader with images and QR subscribe | light-sdk, fork of [zachattack323/LightRSS](https://github.com/zachattack323/LightRSS) |
| [LightNYCSubway](https://github.com/gi-os/LightNYCSubway) | Live MTA subway arrivals | light-sdk fork |
| [chat](https://github.com/gi-os/chat) | iMessage over a self-hosted BlueBubbles server | Fork of [craigeley/chat](https://github.com/craigeley/chat) |
| [LightFog](https://github.com/gi-os/LightFog) | Fog of World companion, GPS recorder and fog map | Fork of [garado/light-topographic](https://github.com/garado/light-topographic) |
| [LightNonogram](https://github.com/gi-os/LightNonogram) | Picross, plus a generator that only ships solvable puzzles | Kotlin generator, light-sdk tool |
| [LightSolitaire](https://github.com/gi-os/LightSolitaire) | Klondike, draw one, unlimited redeals | light-sdk |
| [LightFastread](https://github.com/gi-os/LightFastread) | RSVP speed reader for EPUB and MOBI | Fork of [fluffyspace/FastRead](https://github.com/fluffyspace/FastRead) |
| **LightTip** (this repo) | Tip calculator, plus a receipt splitter that reads the line items | Plain Android |
| [LightNoise](https://github.com/gi-os/LightNoise) | Twelve synthesized sounds, a two-layer mixer and a sleep timer | Plain Android |
| [LightPods](https://github.com/gi-os/LightPods) | AirPods battery, in-ear and lid status | Plain Android, ports [LibrePods](https://github.com/kavishdevar/librepods) |

The Light Phone does not sponsor or endorse any of these. Licences vary per repo.
