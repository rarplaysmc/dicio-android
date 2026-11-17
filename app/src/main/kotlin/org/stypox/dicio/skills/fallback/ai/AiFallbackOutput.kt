package org.stypox.dicio.skills.fallback.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.io.graphical.Body
import org.stypox.dicio.io.graphical.Headline

sealed interface AiFallbackOutput : SkillOutput {

    data class Answer(val question: String, val answer: String) : AiFallbackOutput {
        override fun getSpeechOutput(ctx: SkillContext) = answer

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Body(text = answer)
        }
    }

    data class NeedsModel(val downloading: Boolean) : AiFallbackOutput {
        override fun getSpeechOutput(ctx: SkillContext) =
            if (downloading) {
                "My AI model is still downloading. Please try again when it's finished."
            } else {
                "To answer questions I need to download my AI model first. " +
                    "Tap the download button on screen while you're on Wi-Fi."
            }

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            val context = LocalContext.current
            var started by remember { mutableStateOf(downloading) }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Headline(
                    text = if (started) "Downloading the AI model" else "AI model not downloaded"
                )
                Body(
                    text = if (started) {
                        "${LocalLlm.MODEL_NAME} (${LocalLlm.MODEL_SIZE_TEXT}) downloads over " +
                            "Wi-Fi. Check your notifications for progress, then ask again."
                    } else {
                        "${LocalLlm.MODEL_NAME} is an open-source model that runs entirely on " +
                            "your phone. It's a one-time ${LocalLlm.MODEL_SIZE_TEXT} download " +
                            "over Wi-Fi."
                    }
                )
                if (!started) {
                    Button(onClick = {
                        LocalLlm.startDownload(context)
                        started = true
                    }) {
                        Text("Download AI model")
                    }
                }
            }
        }
    }

    data class Failed(val reason: String) : AiFallbackOutput {
        override fun getSpeechOutput(ctx: SkillContext) =
            "Sorry, the on-device AI ran into a problem."

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Headline(text = getSpeechOutput(ctx))
                Body(text = reason)
            }
        }
    }
}
