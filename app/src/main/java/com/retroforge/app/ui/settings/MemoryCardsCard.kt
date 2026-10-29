package com.retroforge.app.ui.settings

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
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("couldn't open the file")
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Card file"
            opened = name to MemoryCard.read(bytes)
            shown = null
            copying = null
            null
        }.getOrElse { "That file isn't a PlayStation memory card." }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val card = shown ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        status = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(card.readBytes()) } ?: error("couldn't write")
            "Saved ${card.nameWithoutExtension}'s card as a file."
        }.getOrElse { "That didn't work: ${it.message}" }
    }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("PlayStation memory cards")
        CardBody(
            "Each PlayStation game in the built-in player has its own memory card. Pick one to see its saves, then copy a save " +
                "to another game's card (handy for sequels that read the first game's save) or delete it. You can also open a " +
                "card file from another emulator (.mcr, .mcd, .mc, .gme) and copy its saves across. Quit the game first: a " +
                "game that's running writes its own card back. A game gets its card the first time it's played.",
        )
        if (cards.isEmpty()) CardBody("No memory cards yet.")
        ChipRow {
            cards.forEach { file ->
                FocusChip((if (file == shown && opened == null) "▸ " else "") + file.nameWithoutExtension, onClick = {
                    shown = file; opened = null; copying = null; status = null
                })
            }
            FocusChip("Open a card file", onClick = { openFile.launch(arrayOf("*/*")) })
        }
        if (source != null && sourceName != null) {
            CardBody("$sourceName: ${source.saves.size} save${if (source.saves.size == 1) "" else "s"}, ${source.freeBlocks} of 15 blocks free.")
            source.saves.forEach { save ->
                CardBody("• ${save.title} (${save.blocks.size} block${if (save.blocks.size == 1) "" else "s"})")
                ChipRow {
                    FocusChip("Copy to another game", onClick = { copying = save; status = null })
                    if (opened == null) FocusChip("Delete", onClick = { deleting = save })
                }
            }
            if (opened == null) FocusChip("Save this card as a file (.mcr)", onClick = { saveFile.launch("$sourceName.mcr") })
        }
        copying?.let { save ->
            val targets = cards.filter { it != shown }
            CardBody(if (targets.isEmpty()) "No other game has a card yet: play it once first." else "Copy \"${save.title}\" to:")
            ChipRow {
                targets.forEach { target ->
                    FocusChip(target.nameWithoutExtension, onClick = {
                        status = runCatching {
                            val card = MemoryCard(target.readBytes())
                            if (!card.copy(source!!, save)) {
                                "${target.nameWithoutExtension}'s card is full (${card.freeBlocks} blocks free, ${save.blocks.size} needed)."
                            } else {
                                target.writeBytes(card.data)
                                "Copied to ${target.nameWithoutExtension}."
                            }
                        }.getOrElse { "That didn't work: ${it.message}" }
                        copying = null
                        changes++
                    })
                }
                FocusChip("Cancel", onClick = { copying = null })
            }
        }
        status?.let { CardBody(it) }
    }

    deleting?.let { save ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this save?") },
            text = { Text("\"${save.title}\" will be removed from ${shown?.nameWithoutExtension}'s memory card. This can't be undone (unless you have a backup).") },
            confirmButton = {
                TextButton(onClick = {
                    val file = shown
                    status = runCatching {
                        val card = MemoryCard(file!!.readBytes())
                        card.saves.firstOrNull { it.firstBlock == save.firstBlock }?.let(card::delete)
                        file.writeBytes(card.data)
                        "Deleted."
                    }.getOrElse { "That didn't work: ${it.message}" }
                    deleting = null
                    changes++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } },
        )
    }
}
