package com.retroforge.app.ui.settings

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard

/** Mirrors docs/PRIVACY_POLICY.md. */
@Composable
fun PrivacyScreen() {
    ScreenFrame(title = stringResource(R.string.settings_privacy), subtitle = stringResource(R.string.privacy_retroforge_collects_nothing_about_you_no)) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.privacy_on_your_device_only))
            CardBody(stringResource(R.string.privacy_your_library_play_history_favourites_playlists))
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.privacy_internet_only_when_you_ask))
            CardBody(stringResource(R.string.privacy_libretro_thumbnails_for_box_art_system))
            CardBody(stringResource(R.string.privacy_steamgriddb_when_you_search_covers_your))
            CardBody(stringResource(R.string.privacy_screenscraper_when_you_look_a_game))
            CardBody(stringResource(R.string.privacy_hack_catalogues_you_added_when_you))
            CardBody(stringResource(R.string.privacy_links_you_open_emulator_pages_hack))
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.privacy_your_files))
            CardBody(stringResource(R.string.privacy_retroforge_reads_the_folders_you_choose))
        }
    }
}
