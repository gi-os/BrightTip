## v1.4 — four modes, one picker

BrightTip was a tip calculator with a receipt splitter bolted to a second tab. It is now
four tools behind a mode list in the title bar: **Calculator**, **Currency**, **Tip**,
**Receipt**. Tap the name at the top, pick one, and the choice sticks across launches — a
phone that only ever gets used as a calculator opens as a calculator.

The bottom TIP/SPLIT bar is gone. Four modes do not fit in one, and the ~64dp it was
holding is worth more as keypad on a 472dp-tall panel.

### Calculator

Four functions, `%`, `±`, backspace, and chaining that folds as it goes — `2 + 3 +` shows
5 before you type the 4. `=` alone repeats the last operation, so `2 + 3 = =` walks 5, 8,
11. The armed operator inverts, which is the only state change that reads on a matte
greyscale panel.

The arithmetic is `BigDecimal`, not `Double`. `0.1 + 0.2` is `0.3` here. That is the same
rule the rest of this app has always run on — every amount is cents in a `Long` because
binary floats cannot hold most cent amounts — and a calculator that gets handed a bill
total is no place to start making an exception. Division by zero says so and freezes every
key but `C`, so a poisoned figure cannot travel into a total.

**USE AS BILL → TIP** sends the readout to the Tip screen as the bill, rounded half-up to
the cent.

### Currency

Rates from open.er-api.com: no key, no account, ~160 currencies. The whole table is
fetched at once and cached, so every pair converts offline afterwards — which is the
normal case, not the error case. You need this screen most in the shop where you have no
data plan. It refetches at most once a day, on entry to the mode.

The rate and its age are on screen permanently, and the age *brightens* past three days
rather than dimming. A converter that shows a figure without saying how old it is invites
you to trust one from three weeks ago.

Exponents are ISO 4217, not assumed to be two. Yen has no minor unit and dinars have
three, so ¥1,200 is twelve hundred yen — a converter that assumed hundredths everywhere
would be wrong by 100x on half of Asia.

### Tested rather than tapped

The calculator and the converter are pure functions over immutable state with no Android
imports, so the whole keypad is driven from plain JVM tests: `./gradlew
:app:testDebugUnitTest` runs 37 of them, including the exponent and rounding cases above.

Nothing changed in the receipt splitter, the largest-remainder allocation, or the wheel.

## light-common 1.2.1 — the baseline profile arrives

A one-line dependency bump, and the only reason it needs a release of its own is that the last
one did not do what it said.

The previous version added `profileinstaller` on the strength of light-common shipping a baseline
profile in its AAR. It was not in the AAR. The file had been put in `src/main/baselineProfiles/`,
which is the app-module directory; a library ships one as `src/main/baseline-prof.txt`, and AGP
packages nothing and warns about nothing when it is in the wrong place. So `profileinstaller` was
installed, ran, and found no profile.

1.2.1 fixes the packaging, and this build is the first that actually gets it: the wheel and the
crash handler are compiled ahead of time instead of being interpreted on the way to the first
frame. That is the first turn of the wheel after a cold start, and the code that runs in
`onCreate` of every single launch.

Nothing else changed — no code, no keep rules, no behaviour.
