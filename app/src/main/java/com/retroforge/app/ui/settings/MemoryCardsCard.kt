package com.retroforge.app.ui.settings

import com.retroforge.core.ui.pluralStr
import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.player.MemoryCard
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import java.io.File

/**
 * PlayStation memory cards: each game's card in the built-in player (saves/psx/<game>.srm), the saves on it, and
 * copying a save to another game's card or deleting it. Card files from other emulators (.mcr, .mcd, .mc, .gme) can be
 * opened to copy saves from, and a game's card can be saved as a .mcr file for other emulators.
 */
@Composable
fun MemoryCardsCard() {
    val context = LocalContext.current
    val folder = remember { File(context.getExternalFilesDir(null) ?: context.filesDir, "saves/psx") }
    var changes by remember { mutableIntStateOf(0) }
    val cards = remember(changes) {
        folder.listFiles().orEmpty().filter { it.isFile && it.extension == "srm" && it.length() == MemoryCard.SIZE.toLong() }
            .sortedBy { it.name.lowercase() }
    }
    // The card being looked at: a game's card file, or one opened from elsewhere (name and contents, read-only here).
    var shown by remember { mutableStateOf<File?>(null) }
    var opened by remember { mutableStateOf<Pair<String, MemoryCard>?>(null) }
    var copying by remember { mutableStateOf<MemoryCard.Save?>(null) }
    var deleting by remember { mutableStateOf<MemoryCard.Save?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val source: MemoryCard? = remember(shown, opened, changes) {
        opened?.second ?: shown?.let { runCatching { MemoryCard(it.readBytes()) }.getOrNull() }
    }
    val sourceName = opened?.first ?: shown?.nameWithoutExtension

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        status = runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error(str(R.string.built_in_player_couldn_t_open_the_file))
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: str(R.string.memory_cards_card_file)
            opened = name to MemoryCard.read(bytes)
            shown = null
            copying = null
            null
        }.getOrElse { str(R.string.memory_cards_that_file_isn_t_a_playstation) }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val card = shown ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        status = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(card.readBytes()) } ?: error(str(R.string.memory_cards_couldn_t_write))
            str(R.string.memory_cards_saved_s_card_as_a_file, card.nameWithoutExtension)
        }.getOrElse { str(R.string.built_in_player_that_didn_t_work, it.message) }
    }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.memory_cards_playstation_memory_cards))
        CardBody(
            stringResource(R.string.memory_cards_each_playstation_game_in_the_built),
        )
        if (cards.isEmpty()) CardBody(stringResource(R.string.memory_cards_no_memory_cards_yet))
        ChipRow {
            cards.forEach { file ->
                FocusChip((if (file == shown && opened == null) "▸ " else "") + file.nameWithoutExtension, onClick = {
                    shown = file; opened = null; copying = null; status = null
                })
            }
            FocusChip(stringResource(R.string.memory_cards_open_a_card_file), onClick = { openFile.launch(arrayOf("*/*")) })
        }
        if (source != null && sourceName != null) {
            CardBody(pluralStr(R.plurals.memory_cards_saves_blocks_free, source.saves.size, sourceName, source.saves.size, source.freeBlocks))
            source.saves.forEach { save ->
                CardBody(pluralStr(R.plurals.memory_cards_save_blocks, save.blocks.size, save.title, save.blocks.size))
                ChipRow {
                    FocusChip(stringResource(R.string.memory_cards_copy_to_another_game), onClick = { copying = save; status = null })
                    if (opened == null) FocusChip(stringResource(R.string.memory_cards_delete), onClick = { deleting = save })
                }
            }
            if (opened == null) FocusChip(stringResource(R.string.memory_cards_save_this_card_as_a_file), onClick = { saveFile.launch("$sourceName.mcr") })
        }
        copying?.let { save ->
            val targets = cards.filter { it != shown }
            CardBody(if (targets.isEmpty()) stringResource(R.string.memory_cards_no_other_game_has_a_card) else stringResource(R.string.memory_cards_copy_to, save.title))
            ChipRow {
                targets.forEach { target ->
                    FocusChip(target.nameWithoutExtension, onClick = {
                        status = runCatching {
                            val card = MemoryCard(target.readBytes())
                            if (!card.copy(source!!, save)) {
                                str(R.string.memory_cards_s_card_is_full_blocks_free, target.nameWithoutExtension, card.freeBlocks, save.blocks.size)
                            } else {
                                target.writeBytes(card.data)
                                str(R.string.memory_cards_copied_to, target.nameWithoutExtension)
                            }
                        }.getOrElse { str(R.string.built_in_player_that_didn_t_work, it.message) }
                        copying = null
                        changes++
                    })
                }
                FocusChip(stringResource(R.string.network_drives_cancel), onClick = { copying = null })
            }
        }
        status?.let { CardBody(it) }
    }

    deleting?.let { save ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.memory_cards_delete_this_save)) },
            text = { Text(stringResource(R.string.memory_cards_will_be_removed_from_s_memory, save.title, shown?.nameWithoutExtension.toString())) },
            confirmButton = {
                TextButton(onClick = {
                    val file = shown
                    status = runCatching {
                        val card = MemoryCard(file!!.readBytes())
                        card.saves.firstOrNull { it.firstBlock == save.firstBlock }?.let(card::delete)
                        file.writeBytes(card.data)
                        str(R.string.memory_cards_deleted)
                    }.getOrElse { str(R.string.built_in_player_that_didn_t_work, it.message) }
                    deleting = null
                    changes++
                }) { Text(stringResource(R.string.memory_cards_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.memory_cards_keep_it)) } },
        )
    }
}
