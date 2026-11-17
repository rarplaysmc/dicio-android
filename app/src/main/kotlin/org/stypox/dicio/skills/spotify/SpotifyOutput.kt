package org.stypox.dicio.skills.spotify

import org.dicio.skill.context.SkillContext
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput

class SpotifyOutput(
    private val what: String?,
    private val installed: Boolean,
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = when {
        !installed -> "I couldn't find Spotify on this phone."
        what == null -> "Playing Spotify."
        else -> "Playing $what on Spotify."
    }
}
