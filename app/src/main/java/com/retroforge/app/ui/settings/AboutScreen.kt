package com.retroforge.app.ui.settings

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.retroforge.app.BuildConfig
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.Wordmark

@Composable
fun AboutScreen() {
    ScreenFrame(title = null) {
        Wordmark()
        CardBody("Version ${BuildConfig.VERSION_NAME}")

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.about_who_made_it))
            CardBody(stringResource(R.string.about_made_by_darren_glancy))
            CardBody(
                stringResource(R.string.about_built_with_ai_the_code_the),
            )
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.about_retroforge_stands_for))
            CardBody(stringResource(R.string.about_don_t_forget_to_like_and))
            CardBody(stringResource(R.string.about_sorry_wrong_youtube_channel_a_nod))
            CardBody(
                stringResource(R.string.about_retroforge_doesn_t_stand_for_anything),
            )
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.about_what_it_does))
            CardBody(stringResource(R.string.about_finds_the_games_you_already_have))
            CardBody(stringResource(R.string.about_plays_them_in_its_own_built))
            CardBody(stringResource(R.string.about_plays_windows_pc_games_itself_in))
            CardBody(stringResource(R.string.about_streams_the_games_on_your_own))
            CardBody(stringResource(R.string.about_on_snapdragon_devices_can_use_the))
            CardBody(stringResource(R.string.about_keeps_your_saves_in_step_between))
            CardBody(stringResource(R.string.about_patches_rom_hacks_and_translations_and))
            CardBody(stringResource(R.string.about_tidies_your_collection_removes_duplicates_compresses))
            CardBody(stringResource(R.string.about_works_with_a_controller_from_the))
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.about_one_app_instead_of_ten))
            CardBody(
                stringResource(R.string.about_retro_gaming_on_android_usually_means),
            )
            CardBody(
                stringResource(R.string.about_it_works_alongside_what_you_already),
            )
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.about_thanks))
            CardBody(stringResource(R.string.about_bezels_in_the_built_in_player))
            CardBody(
                stringResource(R.string.about_the_pc_player_stands_on_wine),
            )
        }

        SupportCard()

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.about_your_games_your_bios))
            CardBody(stringResource(R.string.about_retroforge_comes_with_no_games_and))
        }
    }
}
