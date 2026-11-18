package org.stypox.dicio.skills.spotify

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.MediaStore
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Spotify
import org.stypox.dicio.util.AppearOnTopNeededOutput
import org.stypox.dicio.util.BackgroundLaunch

/**
 * Asks Spotify to search for something and start playing it right away, using Android's standard
 * "play from search" request. Without a search term Spotify picks up where it left off.
 */
class SpotifySkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Spotify>) :
    StandardRecognizerSkill<Spotify>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Spotify): SkillOutput {
        val what = when (inputData) {
            is Spotify.Play -> inputData.what?.trim()?.takeIf { it.isNotEmpty() }
        }

        if (BackgroundLaunch.wouldBeBlocked(ctx.android)) {
            return AppearOnTopNeededOutput("play Spotify")
        }

        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(SPOTIFY_PACKAGE)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, what ?: "")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            ctx.android.startActivity(intent)
            SpotifyOutput(what = what, installed = true)
        } catch (_: ActivityNotFoundException) {
            SpotifyOutput(what = what, installed = false)
        }
    }

    companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}
