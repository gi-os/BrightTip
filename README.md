# LightTip

A tip calculator and receipt splitter for the **Light Phone III**, built on the official
LightOS SDK. In the toolbox it shows up as **Tip Calculator**.

Two tabs, switched from the bottom bar:

- **Tip** — punch in the bill on a POS-style keypad, pick 10 / 15 / 18 / 20 / 22% or type
  your own, and read the tip and total straight off the screen.
- **Split** — photograph the receipt. Claude Haiku reads the line items off the paper, you
  tap each item and tick off who's on it, and LightTip works out what everyone owes with
  tax and tip shared in proportion to what they actually ordered.

Vision runs on **your own Anthropic API key**, stored locally on the phone. Nothing else
leaves the device.

## Install

Every push to `main` publishes a GitHub Release with the APK attached — grab the latest
from [Releases](../../releases/latest).

```
adb install -r LightTip-<version>.apk
```

Or upload the APK in the Light dashboard under **Developer Mode**. A self-built tool
triggers the "dangerous sideload" warning; accept it.

## First run

1. Generate a QR for your Anthropic key with the companion page at
   `https://gi-os.github.io/LightTip/` (it runs entirely in your browser — the key is
   never sent anywhere).
2. On the phone: **Tip Calculator → gear → Scan API key (QR)**. Typing it by hand works too.

Only the Split tab needs a key. The Tip tab is pure arithmetic and works offline.

## Splitting a bill

1. **SPLIT** tab → **+** → *Take a photo* or *Choose from album*.
2. Wait for Haiku to read the items (a second or two, roughly a fraction of a cent).
3. **PEOPLE** → add everyone at the table.
4. Tap an item, tick everyone sharing it. Shared items divide evenly among the people ticked.
5. **TOTALS** → per-person breakdown of items, tax and tip.

Tip defaults to 20% and is taken on the pre-tax subtotal, adjustable from the bottom bar.
Items nobody claims are left out of everyone's share and reported separately at the bottom
of Totals, so a forgotten line can't quietly vanish.

Every number is held in cents and the tax and tip splits use largest-remainder allocation,
so the per-person totals always add back up to the bill exactly — no stray pennies.

## Design notes for the LPIII panel

- The screen renders **greyscale on a matte panel**. Selected tip chips invert rather than
  tint, because hue is discarded and a low-alpha fill disappears.
- Surfaces are true black with no tonal elevation, so 1dp hairlines do the work of
  separating regions.
- The display is roughly **411 x 472 dp** — normal width, about half the usual height.
  The keypad takes its height from the leftover space rather than a fixed fraction.

## Building it yourself

The Light SDK is vendored in `sdk/`. The only external credential is a GitHub token with
`read:packages`, used to pull Light's `light-keyboard` package. Set repo secrets
`GH_PACKAGES_USER` and `GH_PACKAGES_TOKEN`, or `gpr.user` / `gpr.key` in `local.properties`
for local builds.

```
./gradlew :tool:assembleDebug
```

Tool identity lives in `tool/lighttool.toml` — id `com.lighttip.calc`, label
`Tip Calculator`. CI overwrites `versionCode` with the workflow run number so
`adb install -r` upgrades in place.

## Related

Sibling tools: [LightPass](https://github.com/gi-os/LightPass) (the ticket-stub reader this
borrows its capture and QR-key flow from), [LightFastread](https://github.com/gi-os/LightFastread),
[LightNYCSubway](https://github.com/gi-os/LightNYCSubway).
