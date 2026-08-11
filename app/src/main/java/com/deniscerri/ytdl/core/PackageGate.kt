package com.deniscerri.ytdl.core

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.PackageItem
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Stands between a user action and the runtime it needs.
 *
 * Python and FFmpeg are no longer bundled in the APK, so the first action a user takes on a fresh
 * install may arrive before the interpreter exists. Rather than gating the whole app behind a setup
 * screen, [ensure] is transparent when everything is present — the overwhelmingly common case — and
 * only surfaces a prompt when something is actually missing.
 *
 * Installing reuses the existing package flow ([UiUtil.showNewReleaseUpdateDialog]) so there is one
 * download/install path in the app, not two.
 */
object PackageGate {

    /**
     * Runs [onReady] if the runtime can execute commands, otherwise prompts for the missing
     * package. [onReady] is not invoked when a prompt is shown: installing restarts the runtime, so
     * the user re-triggers the action once it is available.
     */
    fun ensure(
        activity: Activity,
        lifecycleOwner: LifecycleOwner,
        activityView: View,
        snackbarAnchorView: View?,
        installLauncher: ActivityResultLauncher<Intent>,
        onReady: () -> Unit
    ) {
        val missing = RuntimeManager.missingRequired.firstOrNull()
        if (missing == null) {
            onReady()
            return
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.package_required_title, missing.title))
            .setMessage(activity.getString(R.string.package_required_desc, missing.title))
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.install) { d, _ ->
                d.dismiss()
                startInstall(missing, activity, lifecycleOwner, activityView, snackbarAnchorView, installLauncher)
            }
            .show()
    }

    /** Resolves the newest release built for this device's ABI and hands it to the install dialog. */
    private fun startInstall(
        item: PackageItem,
        activity: Activity,
        lifecycleOwner: LifecycleOwner,
        activityView: View,
        snackbarAnchorView: View?,
        installLauncher: ActivityResultLauncher<Intent>
    ) {
        lifecycleOwner.lifecycleScope.launch {
            val release = item.getInstance().getReleases().getOrNull()?.firstOrNull()
            if (release == null) {
                Snackbar.make(activityView, R.string.package_unavailable, Snackbar.LENGTH_LONG).apply {
                    anchorView = snackbarAnchorView
                    show()
                }
                return@launch
            }

            UiUtil.showNewReleaseUpdateDialog(
                release,
                item,
                activity,
                lifecycleOwner,
                activityView,
                snackbarAnchorView,
                installLauncher
            )
        }
    }
}
