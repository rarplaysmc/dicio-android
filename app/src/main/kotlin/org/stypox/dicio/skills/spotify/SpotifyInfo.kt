package org.stypox.dicio.skills.spotify

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.sentences.Sentences

object SpotifyInfo : SkillInfo("spotify") {
    override fun name(context: Context) = "Spotify"

    override fun sentenceExample(context: Context) = "Play Taylor Swift on Spotify"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.LibraryMusic)

    override fun build(ctx: SkillContext): Skill<*>? {
        val data = Sentences.Spotify[ctx.sentencesLanguage] ?: return null
        return SpotifySkill(SpotifyInfo, data)
    }
}
