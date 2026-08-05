package com.gios.lighttip.backup

import com.gios.light.common.sync.Contents
import com.gios.light.common.sync.FileStore
import com.gios.light.common.sync.LightSyncBackup

/**
 * What LightTip hands to LightSync.
 *
 * Two stores rather than one, because the two halves are worth different amounts. `settings` is
 * a default tip percentage and a rounding preference — a few seconds to retype. `bills` is every
 * receipt the Haiku splitter has ever parsed, which is not recoverable at all, and which is the
 * only reason this app is in the backup set.
 *
 * Both are plain files on disk, so both are [FileStore]s. The Claude API key lives in `lighttip`
 * prefs and travels with `settings`; that is deliberate — the blob is AES-GCM sealed before it
 * leaves the phone, and a backup that restores everything except the one credential you need to
 * use the app is a backup you find out about at the worst moment.
 */
class Backup : LightSyncBackup() {

    override fun label() = "Tip"

    override fun stores() = listOf(
        FileStore("settings", Contents(prefs = listOf("lighttip"))),
        FileStore("bills", Contents(databases = listOf("lighttip.db"))),
    )
}
