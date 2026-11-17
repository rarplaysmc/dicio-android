package org.stypox.dicio.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.MainActivity
import org.stypox.dicio.io.graphical.Body
import org.stypox.dicio.io.graphical.Headline

/**
 * Android blocks apps from opening other apps while they are in the background (e.g. after the
 * wake word, when the assistant screen is not shown). The "Appear on top" permission
 * (SYSTEM_ALERT_WINDOW) lifts that block.
 */
object BackgroundLaunch {
    fun isAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Whether a launch right now would probably be silently blocked by Android. */
    fun wouldBeBlocked(context: Context): Boolean =
        MainActivity.isInForeground <= 0 && !isAllowed(context)

    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** Shown instead of a skill's result when Android would block the app from opening. */
class AppearOnTopNeededOutput(private val what: String) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext) =
        "I can't $what from the background yet. Please allow me to appear on top, " +
            "using the button on screen."

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        val context = LocalContext.current
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Headline(text = "Permission needed to $what")
            Body(
                text = "Android only lets me open apps after the wake word if I'm allowed to " +
                    "appear on top. Turn it on for Dicio AI, then try again."
            )
            Button(onClick = { BackgroundLaunch.openSettings(context) }) {
                Text("Allow appear on top")
            }
        }
    }
}
