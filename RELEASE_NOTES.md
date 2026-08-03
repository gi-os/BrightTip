## LightTip v1.1 — Shake to report, and a much smaller APK

**Two changes: the app can file its own bug reports, and the release build is finally shrunk.**

### R8 is on

A tip calculator was shipping Room, CameraX, zxing and OkHttp completely unshrunk, because
`isMinifyEnabled` was false and `proguard-rules.pro` contained the single line
`proguard placeholder` — which is not a valid ProGuard option, and would have failed the build the
moment anyone switched minification on.

Minification and resource shrinking are both on now, against real keep rules. Most of what this
app depends on ships its own rules inside the AAR; what is written by hand is the part no library
can know about. The important one is Room: `Room.databaseBuilder` takes the abstract class and
then finds its implementation by string — "TipDatabase" + "_Impl" — so renaming either half makes
the builder throw the first time you open a saved bill. zxing is kept for the same reason, since
its capture activity is named from the manifest.

Line numbers are kept, so a stack trace in a shake report is still readable.

### Shake the phone to report a bug

Shake twice — there and back, twice — and a sheet comes up. Pick what happened from five chips and
add a note in your own words if you have something to add. The note is optional but it is the part
that carries anything: "Something looks wrong" is a shrug, and what you type becomes the title of
the issue. The report brings the screen you were on, app and firmware versions, free space, heap,
and the stack trace if the app died the last time you had it open.

Reports queue on disk before anything is sent, always, so a report survives the crash that
prompted it. The gesture counts reversals rather than force, so walking never fires it.
