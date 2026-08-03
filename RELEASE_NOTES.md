## LightTip v1.2 — The shake asks instead of interrupting, and the plumbing moved out

**Two things: shaking the phone no longer throws a sheet over what you were doing, and the
reporting code it belongs to is now a shared library rather than a copy that lives in this app.**

### The shake offers a chip, not a sheet

The first version got the shape of the question wrong. A shake is a gesture the phone can
misread — and the cost of misreading it was paid every single time, because a full-screen sheet
landed on top of whatever you were reading to ask about a problem that may not have existed. On a
3.92" panel that is a bad trade against a report that might not be real.

So the offer is small, it sits out of the way, and **silence is an answer**. A shake puts a
"SEND ERROR?" chip in the bottom corner; ignore it for four seconds and it fades. Nothing is lost
by ignoring it: an unsent crash log stays on disk and is offered again on the next launch, and a
failure the app noticed itself will not ask again for an hour. Only the tap costs anything, and
only the tap opens the sheet.

A crash offer stands for eight seconds rather than four. It is the one offer that cannot be
reconstructed from nothing if you miss it.

The chip is drawn in its own window rather than placed in the layout, so it lands in the same
corner in every app regardless of how that app is built, and it cannot swallow a tap meant for
what is underneath it.

Issue titles now follow the same convention as every other app — `LightTip v1.2.x — <headline>`,
labelled `tip` — instead of the `tip: <headline>` this app had invented.

### Reporting is a library now

The eight files under `com.gios.lighttip.report` are gone. They are `com.gios:light-common:1.0.1`,
resolved from GitHub Packages, and shared with the other apps that had their own copy of the same
code. LightTip is the first app to use it.

Nothing about this is visible on the phone. It matters because a fix to the reporter used to mean
editing it in ten places and getting eight of them subtly wrong, which is how the sheet-instead-of
-chip mistake reached ten apps before anyone saw it once.

One thing did have to change shape. `BuildConfig` does not cross a library boundary, so the app
hands its name, its triage label and its report key to `LightReport.install()` at startup rather
than the reporter reading them out of the build. Skip that call and reporting is simply inert,
which is a better failure than a reporter that files issues with a blank app name.

Same note field, same queue-to-disk-first behaviour, same gesture tuning. R8 stays on: the keep
rules the reporter needs now arrive with the library instead of being written out here.
