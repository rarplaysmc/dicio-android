package org.stypox.dicio.skills.fallback.ai

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo

object AiFallbackInfo : SkillInfo("ai_fallback") {
    override fun name(context: Context) = "On-device AI"

    override fun sentenceExample(context: Context) = "Why is the sky blue?"

    @Composable
    override fun icon() =
        rememberVectorPainter(Icons.Default.AutoAwesome)

    override fun build(ctx: SkillContext): Skill<*> {
        return AiFallbackSkill(AiFallbackInfo)
    }
}
