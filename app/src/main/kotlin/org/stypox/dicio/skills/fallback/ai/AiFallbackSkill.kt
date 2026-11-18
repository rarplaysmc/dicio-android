package org.stypox.dicio.skills.fallback.ai

import android.util.Log
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.util.RecognizeEverythingSkill

/**
 * Used when no other skill understood the request: the question goes to the on-device AI model.
 */
class AiFallbackSkill(correspondingSkillInfo: SkillInfo) :
    RecognizeEverythingSkill(correspondingSkillInfo) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: String): SkillOutput {
        val question = inputData.trim()
        if (question.isEmpty()) {
            return AiFallbackOutput.Answer(question, "Sorry, I didn't catch that.")
        }

        return when (LocalLlm.modelState(ctx.android)) {
            LocalLlm.ModelState.MISSING -> AiFallbackOutput.NeedsModel(downloading = false)
            LocalLlm.ModelState.DOWNLOADING -> AiFallbackOutput.NeedsModel(downloading = true)
            LocalLlm.ModelState.READY -> try {
                val reply = LocalLlm.ask(ctx.android, question)
                AiFallbackOutput.Answer(
                    question,
                    reply.text.ifEmpty { "Sorry, I don't have an answer for that." },
                    reply.details,
                )
            } catch (t: Throwable) {
                Log.e("AiFallbackSkill", "On-device AI failed", t)
                AiFallbackOutput.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }
}
