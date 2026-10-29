package com.retroforge.app.ui.vr

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

    ScreenFrame(title = "VR and 3D", subtitle = "Play and watch in a Cardboard-style viewer: put your phone in it sideways.") {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("How it works")
            CardBody(
                "Any game can be shown once per eye: pause it (or swipe in from an edge) and switch on Cardboard VR. The phone turns " +
                    "sideways and the touch buttons hide, so a Bluetooth controller is the way to play. Most games look like a big " +
                    "floating screen; Virtual Boy games are real 3D.",
            )
        }
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
            byDefault = !byDefault
            prefs.edit().putBoolean(VR_BY_DEFAULT, byDefault).apply()
        }) {
            CardTitle("Start every game in Cardboard VR")
            StatusPill(if (byDefault) "On" else "Off", if (byDefault) StatusTone.GOOD else StatusTone.NEUTRAL)
            CardBody("For a session in the viewer. Any game can still be switched back in its pause menu.")
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Virtual Boy: real 3D")
            if (virtualBoy.isEmpty()) {
                CardBody("No Virtual Boy games in your library. Add them to a virtualboy folder and they show up here.")
            } else {
                CardBody("Each eye gets its own picture, as on the original console.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    virtualBoy.forEach { game -> FocusChip(game.title, onClick = { onOpenGame(game.id) }) }
                }
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenMedia) {
            CardTitle("3D films")
            CardBody(
                "Side-by-side and top-and-bottom 3D films play one half per eye; any 2D film can play on a big screen per eye " +
                    "(cinema mode). Select to open Media; 3D films there have a 3D badge.",
            )
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Meta Quest")
            CardBody("On a Quest, RetroForge runs as a flat Android window: games and films play on a big virtual screen.")
        }
    }
}
