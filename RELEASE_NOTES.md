## BrightTip v1.5 — the keypad is sized from the key

**"Keypad scaling is off - buttons are super small."** The word doing the work in that sentence
is *scaling*, and it is the right one.

The keys were never small. The keypad already claims every pixel left over after whatever is
above it, and nothing else on the screen competes for that space — so on the Tip tab each key is
a large box, and on the Currency tab, which carries two amount rows and a rate line, it is a much
smaller one. What was fixed was the digit printed in the middle: 26sp, the same numeral in a box
of any height. In the tall case that is a small label floating in an empty rectangle. A keypad
that is technically large and legibly tiny.

### The digits grow with the key

The numeral is now measured off the key box — roughly half its height — with both ends clamped.
The floor keeps digits readable where the keypad is tightest, so nothing gets smaller than it was
in the worst case. The ceiling stops a nearly empty screen producing a numeral so large it reads
as a mistake. Between them, a key looks like a key: mostly digit.

C and DEL are sized proportionally rather than to the same number. They are words, and three
letters set at a numeral's size would be wider than the key holding them.

Fixes [light-reports#127].
