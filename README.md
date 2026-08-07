# BrightTip

Tip calculator and AI receipt splitter for the **Light Phone III**. Shows up on the phone
as **Tip Calculator**, package `com.gios.lighttip`. Current released version: **v1.0.13**.

## Install via BrightMarket

<p align="center">
  <img src="https://gi-os.github.io/brightmarket-index/assets/brightmarket-qr.png" alt="Scan to open BrightMarket" width="180" />
</p>

Scan the code above, or visit
**[gi-os.github.io/brightmarket-index/browse.html](https://gi-os.github.io/brightmarket-index/browse.html)**, to install
and keep this app updated through **BrightMarket** — no Play Store, no PC
required.

## Why this exists

LightOS has no calculator built for splitting a bill, and every figure here is money —
which is the whole design constraint. **All amounts are held in cents as `Long`, never
`Double`.** Floating point can't represent most cent amounts exactly, and repeated
allocation (tax and tip spread across N diners) compounds that error into totals that
don't add back up to the bill. Tax and tip are instead allocated by **largest-remainder
apportionment**, verified against the invariant "per-person totals sum to the bill
exactly" across 20,000 randomised trials. This is not a detail to refactor away later.

## Quick start

Every push to `main` publishes one signed APK as a GitHub Release — **a push is a release
trigger**, not a cosmetic action.

1. Grab the newest APK from [Releases](../../releases/latest):
   ```
   adb install -r LightTip-v<version>.apk
   ```
   or track `https://github.com/gi-os/BrightTip` in **Obtainium** (the repo URL, not the
   GitHub Pages URL below).
2. Only the **Split** tab needs a key. Open
   [gi-os.github.io/BrightTip](https://gi-os.github.io/BrightTip/), paste an Anthropic API
   key, and a QR appears — the page is entirely client-side, the key never leaves the
   browser. On the phone: **gear → Scan QR** (or type the key by hand).
3. **Tip** tab works offline immediately: punch the bill into the keypad, pick a preset
   (10/15/18/20/22%) or type your own, read tip and total.

<table>
  <tr>
    <td align="center">
      <img src="docs/screenshots/tip.png" width="250" alt="BrightTip tip calculator with a POS-style keypad"><br>
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

## Configuration and usage

### Splitting a bill

1. **SPLIT** tab → **+** → photograph the bill.
2. Wait a second or two while Claude Haiku reads the line items off the photo. Vision runs
   on your own Anthropic key, stored on the phone; nothing else leaves the device, and
   quantities are expanded (2x Beer becomes two rows) so two people can each claim one.
3. Person icon → add everyone at the table. Names are remembered between bills.
4. Tap an item, tick everyone sharing it — a shared item divides evenly among whoever is
   ticked. Items nobody claims are excluded from every share and reported separately in
   Totals, rather than silently absorbed into someone's bill.
5. **TOTALS** → per-person breakdown of items, tax and tip. Tip defaults to 20% on the
   pre-tax subtotal and is adjustable per receipt.

### The wheel

Turning the phone's wheel scrolls the receipt list, the line items, the people list, the
per-person totals and the tick-list in the assign dialog — the reason it's wired up at
all: on a 472dp-tall panel a table of six shows about four, and reaching over the list to
drag it is the one gesture a bill-splitting app should never need mid-conversation.

BrightTip alone is the whole install for this — no service, no permission, no root. The
wheel arrives as ordinary key events (LightOS relabels an optical sensor's scancodes as
`WHEEL_CCW`/`WHEEL_CW`, and nothing in the system intercepts them), and the Activity claims
them in `dispatchKeyEvent`, before the view hierarchy gets a look, so a focused name field
can't swallow them. Notches arrive faster than a frame, so each is paid off a fraction per
frame; the first notch after a pause is held back, since the wheel sits under a thumb. The
assign dialog is its own window and picks the wheel up separately, so the list underneath
stands down while it's open.

The wheel *click* and the camera button do nothing here. Optional, separate install for
those: [BrightControl](https://github.com/gi-os/BrightControl) gives the whole phone
brightness (hold wheel + turn), flashlight (tap), and camera (camera button), each
rebindable. It passes bare turns straight through to `com.gios.*`, so it doesn't cost this
app the scrolling above.

```bash
adb install -r LightControl-v1.0.x.apk

# NOTE: this replaces the accessibility-service list — colon-join if you also run
# LightVoice's push-to-talk.
adb shell settings put secure enabled_accessibility_services \
  com.gios.lightcontrol/com.gios.lightcontrol.keys.ControlService
adb shell settings put secure accessibility_enabled 1

adb shell appops set com.gios.lightcontrol WRITE_SETTINGS allow
adb shell appops set com.gios.lightcontrol SYSTEM_ALERT_WINDOW allow
```

### Notes for the LPIII panel

- The screen is **greyscale on matte glass**. Selected tip chips invert rather than tint,
  because hue is discarded and a low-alpha fill disappears.
- Surfaces are true black with no tonal elevation, so 1dp rules separate regions and
  dialogs get an explicit dark-grey fill — a scrim over black tints nothing.
- The display is roughly **411 × 472 dp**, normal width and about half the usual height;
  the keypad takes the leftover vertical space rather than a fixed fraction of the screen.
- Text uses Akkurat when LightOS provides it, so the app matches the system UI.

### Why this isn't a Light SDK tool

The [Light SDK](https://github.com/lightphone/light-sdk) sandbox rejects CameraX and
blocks `LocalContext` outright, and `READ_MEDIA_IMAGES` is not on its permission allowlist
— so a sanctioned SDK tool cannot photograph anything. BrightTip is a plain sideloaded APK
for the same reason [BrightPasses](https://github.com/gi-os/BrightPasses) is; it started as a
light-sdk fork and had to be rebuilt from scratch as one.

## Building

```
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

CI (`.github/workflows/build.yml`) publishes a signed GitHub Release on every push to
`main`: `versionCode` = workflow run number, `versionName` = `1.0.<run>`, tag
`v1.0.<run>`, exactly one APK named `LightTip-v<version>.apk`, marked latest — the debug
build stays a workflow artifact. The keystore is committed at `keystore/lighttip.jks` so
`adb install -r` upgrades in place, and CI pins its cert SHA-256 in
`signing-fingerprint.txt`.

Two CI gotchas worth not relitigating:

- **AGP shortens resource paths in release builds**, so the icon can land in the APK as
  something like `res/o-.png` — grepping the zip for `ic_launcher` finds nothing; verify
  with `aapt2 dump badging` instead.
- Parse `apksigner verify --print-certs` output with
  `sed -E 's/.*[Dd]igest:[[:space:]]*//'`.

### The launcher icon is generated, not hand-drawn

The mark is a percent sign — the tip on one tab, two parties with a line between them on
the other — white line art on black, matching the icon language of the sibling Light
Phone III tools. Geometry lives once in `scripts/generate_icon.py` and is emitted twice:
as Android adaptive vector layers and as raster fallbacks for every mipmap density,
everything inside the 18–90 safe zone (of a 108×108 canvas) so no launcher mask clips it.
Re-running the script reproduces the shipped assets byte for byte, apart from vector
formatting:

```
python3 scripts/generate_icon.py   # needs Pillow; rewrites app/src/main/res/{drawable,mipmap-*}
```

It shipped without any `android:icon` at first, which is why Obtainium and the launcher
both showed the default Android robot until `ic_launcher`/`ic_launcher_round` were wired
up — verified afterward through `aapt2 dump badging`, not by grepping the APK zip (see
above).

Companion QR page for the Anthropic key is GitHub Pages from `/docs` at
<https://gi-os.github.io/BrightTip/>; it is client-side only.

## Contributing

Issues and PRs welcome.

- Money math changes need to keep the largest-remainder invariant — run the randomised
  trial suite, don't just eyeball a couple of examples.
- `material-icons-extended` is banned here — it alone was ~30 MB of the debug APK.
  Material3's core icon set plus a stock `Checkbox` covers everything used; `abiFilters`
  is arm64 only and the debug APK sits around 28 MB.
- If you touch the launcher icon, edit the geometry in `scripts/generate_icon.py`, not the
  generated XML/PNGs directly, and re-run it.
- CI publishes a release on every push to `main` — verify locally before pushing there.

## Version history

| Version | Change |
| --- | --- |
| v1.0.13 | Say plainly that the wheel works with BrightTip alone (docs) |
| v1.0.12 | Scroll with the wheel |
| v1.0.11 | docs: add BrightNoise and LightPods to the collection table |
| v1.0.10 | Make the icon reproducible from a script |
| v1.0.9 | Check the icon through aapt2 badging, not the zip listing |
| — | Add a launcher icon |
| v1.0.7 | docs: add screenshots and the collection table |
| v1.0.6 | Publish a signed release APK alongside the debug one |
| v1.0.5 | Drop material-icons-extended |
| v1.0.4 | Ship arm64 only and use the non-deprecated LocalLifecycleOwner |
| v1.0.3 | Rebuild as a plain sideloaded APK |
| — | Camera-only capture: READ_MEDIA_IMAGES is not on the SDK permission allowlist |
| — | BrightTip 1.0 — tip calculator and AI receipt splitter for the Light Phone III (initial commit) |

## The gi-os Light App collection

Twelve tools for the Light Phone III, all open source, all built in one run.

| Tool | What it does | Built on |
| --- | --- | --- |
| [BrightPasses](https://github.com/gi-os/BrightPasses) | Photograph a movie ticket, keep the stub | Plain Android |
| [LightQR](https://github.com/gi-os/LightQR) | QR scanner, plus a browser generator | Plain Android |
| [BrightNews](https://github.com/gi-os/BrightNews) | RSS and Atom reader with images and QR subscribe | light-sdk, fork of [zachattack323/LightRSS](https://github.com/zachattack323/LightRSS) |
| [BrightTransit](https://github.com/gi-os/BrightTransit) | Live MTA subway arrivals | light-sdk fork |
| [chat](https://github.com/gi-os/chat) | iMessage over a self-hosted BlueBubbles server | Fork of [craigeley/chat](https://github.com/craigeley/chat) |
| [FogLight](https://github.com/gi-os/FogLight) | Fog of World companion, GPS recorder and fog map | Fork of [garado/light-topographic](https://github.com/garado/light-topographic) |
| [BrightNonogram](https://github.com/gi-os/BrightNonogram) | Picross, plus a generator that only ships solvable puzzles | Kotlin generator, light-sdk tool |
| [BrightSolitaire](https://github.com/gi-os/BrightSolitaire) | Klondike, draw one, unlimited redeals | light-sdk |
| [BrightLibrary](https://github.com/gi-os/BrightLibrary) | RSVP speed reader for EPUB and MOBI | Fork of [fluffyspace/FastRead](https://github.com/fluffyspace/FastRead) |
| **BrightTip** (this repo) | Tip calculator, plus a receipt splitter that reads the line items | Plain Android |
| [BrightNoise](https://github.com/gi-os/BrightNoise) | Twelve synthesized sounds, a two-layer mixer and a sleep timer | Plain Android |
| [LightPods](https://github.com/gi-os/LightPods) | AirPods battery, in-ear and lid status | Plain Android, ports [LibrePods](https://github.com/kavishdevar/librepods) |

The Light Phone does not sponsor or endorse any of these. Licences vary per repo; this one
is MIT.
