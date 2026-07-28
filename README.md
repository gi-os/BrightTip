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

## Install

Every push to `main` publishes a signed APK as a GitHub Release. Grab the newest from
[Releases](../../releases/latest):

```
adb install -r LightTip-v<version>.apk
```

The keystore is committed, so every build is signed with the same key and upgrades install
over the top instead of erroring.

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

## Related

[LightPass](https://github.com/gi-os/LightPass) · [LightFastread](https://github.com/gi-os/LightFastread) · [LightNYCSubway](https://github.com/gi-os/LightNYCSubway)
