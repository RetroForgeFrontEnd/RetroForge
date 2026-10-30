package com.retroforge.app.ui.vr

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import kotlinx.coroutines.flow.first

/** View preference: games start in Cardboard VR (each system can still be switched in the pause menu). */
const val VR_BY_DEFAULT = "vr_by_default"

/**
 * VR and 3D in one place: Cardboard VR for every game, Virtual Boy in real 3D, 3D films. A phone in a Cardboard-style
 * viewer; a Meta Quest runs RetroForge as a flat Android window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VrScreen(prefs: SharedPreferences, library: LibraryRepository, onOpenGame: (Long) -> Unit, onOpenMedia: () -> Unit) {
    var byDefault by remember { mutableStateOf(prefs.getBoolean(VR_BY_DEFAULT, false)) }
    val virtualBoy by produceState(emptyList<GameEntity>(), library) { value = library.gamesFor("virtualboy").first() }

    ScreenFrame(title = stringResource(R.string.vr_vr_and_3d), subtitle = stringResource(R.string.vr_play_and_watch_in_a_cardboard)) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.vr_how_it_works))
            CardBody(
                stringResource(R.string.vr_any_game_can_be_shown_once),
            )
        }
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
            byDefault = !byDefault
            prefs.edit().putBoolean(VR_BY_DEFAULT, byDefault).apply()
        }) {
            CardTitle(stringResource(R.string.vr_start_every_game_in_cardboard_vr)) { StatusPill(if (byDefault) "On" else "Off", if (byDefault) StatusTone.GOOD else StatusTone.NEUTRAL) }
            CardBody(stringResource(R.string.vr_for_a_session_in_the_viewer))
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.vr_virtual_boy_real_3d))
            if (virtualBoy.isEmpty()) {
                CardBody(stringResource(R.string.vr_no_virtual_boy_games_in_your))
            } else {
                CardBody(stringResource(R.string.vr_each_eye_gets_its_own_picture))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    virtualBoy.forEach { game -> FocusChip(game.title, onClick = { onOpenGame(game.id) }) }
                }
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.vr_3d_films))
            CardBody(
                stringResource(R.string.vr_side_by_side_and_top_and),
            )
            FocusChip(stringResource(R.string.vr_open_media), onClick = onOpenMedia)
        }
        com.retroforge.app.ui.media.ThreeDFilms(prefs)
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.vr_meta_quest))
            CardBody(stringResource(R.string.vr_on_a_quest_retroforge_runs_as))
        }
    }
}
