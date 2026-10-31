package com.retroforge.app.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.core.library.CoverBatch
import com.retroforge.core.library.CoverRepository
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.str

/**
 * Scrape all artwork: box art for every game in the library without any, every system at once. Shows the button,
 * then how far it has got with Stop, then what it found. Used on the Retro tab and in Settings → Covers and bezels.
 */
@Composable
fun ScrapeAllArtwork(covers: CoverRepository) {
    val batch by covers.batch.collectAsStateWithLifecycle()
    when (val b = batch.takeIf { it.isAllSystems() }) {
        is CoverBatch.Running -> {
            CardBody(stringResource(R.string.library_downloading_box_art_of_found, b.done, b.total, plural(b.found, "cover")))
            LinearProgressIndicator(progress = { if (b.total == 0) 0f else b.done.toFloat() / b.total }, modifier = Modifier.fillMaxWidth())
            FocusChip(stringResource(R.string.library_stop), onClick = covers::cancelBatch)
        }
        is CoverBatch.Finished -> {
            CardBody(
                if (b.found + b.missing + b.failed == 0) str(R.string.library_every_game_has_box_art)
                else str(R.string.library_box_art_added, plural(b.found, "cover")) +
                    (if (b.missing > 0) str(R.string.library_not_found, b.missing) else "") +
                    (if (b.failed > 0) str(R.string.library_failed, b.failed) else "") + ".",
            )
            FocusChip(stringResource(R.string.library_scrape_all_artwork), onClick = covers::downloadAllMissing)
        }
        is CoverBatch.Failed -> CardBody(b.reason)
        null -> FocusChip(stringResource(R.string.library_scrape_all_artwork), onClick = covers::downloadAllMissing)
    }
}

private fun CoverBatch?.isAllSystems() = when (this) {
    is CoverBatch.Running -> system == CoverRepository.ALL_SYSTEMS
    is CoverBatch.Finished -> system == CoverRepository.ALL_SYSTEMS
    is CoverBatch.Failed -> system == CoverRepository.ALL_SYSTEMS
    null -> false
}
