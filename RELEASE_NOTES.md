## LightTip v1.3 — The wheel comes from the library too, backups, and R8 in full mode

**Three things: the last of the copy-pasted plumbing is gone, LightTip now backs itself up to
BasilNet, and the release build shrinks properly.**

### The wheel is shared code now

v1.2 moved reporting into `light-common` and left `hw/` behind, because the library did not have
a wheel yet. It does, so this app's copy is deleted — 190 lines, two files, and one more place
the wheel could have drifted from everywhere else.

Nothing about turning it changes. The library's `WheelScroll` is a superset of what was here:
same `active` guard, plus `reverse` and a gate for whole subtrees that this app does not use yet.

### Backups

LightTip offers itself to LightSync, so every bill the receipt splitter has parsed is on BasilNet
instead of only on the phone. Two stores, because the halves are worth different amounts:

- **settings** — the default tip, the rounding preference, the API key. Seconds to retype.
- **bills** — every receipt ever split. Not recoverable from anywhere.

The key travels with the settings, and that is deliberate. The blob is sealed with AES-GCM before
it leaves the phone, and a restore that brings back everything except the one credential the app
needs is the kind of backup you discover at the worst possible moment.

Restore is two taps in LightSync, and the app is killed afterwards — Room caches in memory, so an
app still running over swapped files will write the old rows back.

### R8 full mode

`android.enableR8.fullMode=true`. Compat mode was already shrinking; full mode also merges
classes, drops unused arguments, and stops assuming a class it cannot see allocated might still
be instantiated. On a phone this slow to start, that is most of the win.

Room and zxing already had the keep rules that full mode makes mandatory rather than merely wise,
and `light-common` now ships its own — including keeping the line numbers, without which a crash
report sent by shaking the phone arrives as a wall of one-letter names.

The baseline profile in `light-common` is now actually installed, via `profileinstaller`. It was
being packaged and ignored: below API 31 nothing reads a profile on its own. It covers the wheel
and the crash handler, which is to say the first notch after a cold start and the code that runs
in `onCreate` of every launch.

### Known

Full mode is a first for this app. If a saved bill fails to open or the QR scanner comes up blank,
that is a missing keep rule rather than a data problem — shake the phone and it will be in
light-reports with the class name in it.
