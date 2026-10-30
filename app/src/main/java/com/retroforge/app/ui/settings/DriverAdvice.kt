package com.retroforge.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.pc.Confidence
import com.retroforge.pc.DriverPick
import com.retroforge.pc.DriverReason
import com.retroforge.pc.DriverRecommendations
import com.retroforge.pc.DriverTarget
import com.retroforge.pc.GpuFamily
import com.retroforge.pc.GpuProbe
import com.retroforge.player.CoreChoices
import com.retroforge.player.GpuDriver
import com.retroforge.player.PlayerCores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * This device's GPU family, looked at once and kept in files/player/gpu.properties, so the player (its own process)
 * can read it without starting a GL context of its own.
 */
object DeviceGpu {

    private fun file(context: Context) = File(context.filesDir, "player/gpu.properties")

    /** The family found earlier, or null when Settings or a system page hasn't looked yet. */
    fun cached(context: Context): GpuFamily? =
        runCatching { CoreChoices(file(context)).get("family")?.let(GpuFamily::valueOf) }.getOrNull()

    /** The family, looking at the GPU the first time. Call off the main thread. */
    fun family(context: Context): GpuFamily = cached(context) ?: GpuFamily.of(GpuProbe.probe()).also {
        // An emulator or a probe that read nothing may read something later: only a known family is kept.
        if (it != GpuFamily.UNKNOWN) CoreChoices(file(context)).set("family", it.name)
    }
}

/** The player's per-system settings file, where a system's driver choice ("driver.<system>") is kept. */
fun videoChoices(context: Context) = CoreChoices(File(context.filesDir, "player/video.properties"))

@Composable
fun pickText(pick: DriverPick) = stringResource(if (pick == DriverPick.TURNIP) R.string.gpu_rec_turnip else R.string.gpu_rec_phone)

@Composable
fun confidenceText(confidence: Confidence) = stringResource(
    when (confidence) {
        Confidence.TESTED -> R.string.gpu_rec_tested
        Confidence.COMMUNITY -> R.string.gpu_rec_community
        Confidence.GUESS -> R.string.gpu_rec_guess
    },
)

@Composable
fun reasonText(reason: DriverReason) = stringResource(
    when (reason) {
        DriverReason.NOT_ADRENO -> R.string.gpu_rec_why_not_adreno
        DriverReason.OLD_ADRENO -> R.string.gpu_rec_why_old_adreno
        DriverReason.UNKNOWN_GPU -> R.string.gpu_rec_why_unknown
        DriverReason.OPENGL_ONLY -> R.string.gpu_rec_why_opengl
        DriverReason.APP_CANT_LOAD -> R.string.gpu_rec_why_app_cant
        DriverReason.TESTED_RENDERS -> R.string.gpu_rec_why_tested_renders
        DriverReason.TURNIP_MORE_COMPLETE -> R.string.gpu_rec_why_turnip_complete
        DriverReason.TURNIP_LIKELY -> R.string.gpu_rec_why_turnip_likely
        DriverReason.SMALL_GAIN -> R.string.gpu_rec_why_small_gain
        DriverReason.A8XX_NEW -> R.string.gpu_rec_why_a8xx
        DriverReason.PC_NEEDS_TURNIP -> R.string.gpu_rec_why_pc
    },
)

/** "Eden: Turnip (developers' advice)" for one emulator, and why when [why]. */
@Composable
fun RecommendationLines(name: String, family: GpuFamily, target: DriverTarget, why: Boolean) {
    val rec = DriverRecommendations.recommend(family, target)
    StatusPill(
        stringResource(R.string.gpu_rec_line, name, pickText(rec.pick), confidenceText(rec.confidence)),
        if (rec.pick == DriverPick.TURNIP) StatusTone.GOOD else StatusTone.NEUTRAL,
    )
    if (why) CardBody(reasonText(rec.reason))
}

/**
 * Many emulators at once, in two lines: "Turnip: Built-in Dolphin (tested), Eden …" and the same for the
 * phone's driver; with [why], each one's line and reason instead.
 */
@Composable
fun RecommendationSummary(family: GpuFamily, entries: List<Pair<String, DriverTarget>>, why: Boolean) {
    if (why) return entries.forEach { (name, target) -> RecommendationLines(name, family, target, why = true) }
    for (pick in DriverPick.entries) {
        val names = entries.mapNotNull { (name, target) ->
            DriverRecommendations.recommend(family, target).takeIf { it.pick == pick }?.let { name to it.confidence }
        }
        if (names.isEmpty()) continue
        // Developers' advice is the usual case; only tested and best-guess entries are marked.
        val list = names.map { (name, confidence) ->
            if (confidence == Confidence.COMMUNITY) name else stringResource(R.string.gpu_rec_name_confidence, name, confidenceText(confidence))
        }
        StatusPill(stringResource(R.string.gpu_rec_group, pickText(pick), list.joinToString(", ")), if (pick == DriverPick.TURNIP) StatusTone.GOOD else StatusTone.NEUTRAL)
    }
}

/** Why? / Hide reasons. */
@Composable
fun WhyChip(why: Boolean, onToggle: () -> Unit) =
    FocusChip(stringResource(if (why) R.string.gpu_rec_hide_why else R.string.gpu_rec_why), onClick = onToggle)

/**
 * The driver choice for one system in RetroForge's own player: what it uses now, and Use recommended, Phone's
 * driver and Turnip. [whereToGet]: say where to get Turnip when it's recommended and missing (not on the page that has it).
 */
@Composable
fun BuiltInDriverChoice(system: String, family: GpuFamily, target: DriverTarget, whereToGet: Boolean = true) {
    val context = LocalContext.current
    val video = remember { videoChoices(context) }
    var changes by remember { mutableIntStateOf(0) }
    val choice = remember(changes, system) { video.get(DriverRecommendations.choiceKey(system)) }
    val installed = remember { GpuDriver.current(context) != null }
    val rec = DriverRecommendations.recommend(family, target)
    val using = DriverRecommendations.useCustomDriver(choice, family, target, installed)
    CardBody(stringResource(R.string.gpu_rec_retroforge_uses, pickText(if (using) DriverPick.TURNIP else DriverPick.PHONE)))
    if (whereToGet && rec.pick == DriverPick.TURNIP && !installed) CardBody(stringResource(R.string.gpu_rec_no_turnip_yet))
    fun set(value: String?) {
        video.set(DriverRecommendations.choiceKey(system), value)
        changes++
    }
    ChipRow {
        FocusChip((if (choice == null) "● " else "") + stringResource(R.string.gpu_rec_use_recommended), onClick = { set(null) })
        FocusChip((if (choice == DriverRecommendations.CHOICE_PHONE) "● " else "") + stringResource(R.string.gpu_rec_phone), onClick = { set(DriverRecommendations.CHOICE_PHONE) })
        if (installed) FocusChip((if (choice == DriverRecommendations.CHOICE_TURNIP) "● " else "") + stringResource(R.string.gpu_rec_turnip), onClick = { set(DriverRecommendations.CHOICE_TURNIP) })
    }
}

/**
 * A system page's GPU driver card: the best driver on this device for the built-in player's emulator and for each
 * standalone app that runs the system, where the driver matters. Nothing is downloaded from here.
 */
@Composable
fun SystemDriverCard(system: String, builtInCore: PlayerCores.PlayerCore?, apps: List<Pair<String, String>>) {
    val context = LocalContext.current
    var family by remember { mutableStateOf(DeviceGpu.cached(context)) }
    LaunchedEffect(Unit) { if (family == null) family = withContext(Dispatchers.IO) { DeviceGpu.family(context) } }
    val f = family ?: return
    val video = remember { videoChoices(context) }
    val builtIn = builtInCore?.let { core ->
        val target = DriverTarget.builtIn(core.id, PlayerCores.usesVulkan(core.id, video.get("graphics.$system")))
        // Cores that never draw with Vulkan have nothing to choose; a Vulkan core on OpenGL says so.
        if (core.id in PlayerCores.vulkanCores) core to target else null
    }
    val appTargets = apps.mapNotNull { (id, name) -> DriverTarget.standalone(id)?.let { name to it } }.distinct()
    if (builtIn == null && appTargets.isEmpty()) return
    var why by remember(system) { mutableStateOf(false) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.gpu_rec_title))
        if (!f.canUseTurnip) {
            // Mali, Xclipse, PowerVR, old Adrenos: one answer for everything.
            CardBody(reasonText(DriverRecommendations.recommend(f, DriverTarget.EDEN).reason))
            return@FocusCard
        }
        builtIn?.let { (core, target) ->
            RecommendationLines(stringResource(R.string.gpu_rec_built_in, core.name), f, target, why)
            if (target != DriverTarget.BUILT_IN_OTHER) BuiltInDriverChoice(system, f, target)
        }
        appTargets.forEach { (name, target) ->
            RecommendationLines(name, f, target, why)
            if (why && target != DriverTarget.NETHERSX2) CardBody(stringResource(R.string.gpu_rec_set_in_app, name))
        }
        WhyChip(why) { why = !why }
    }
}
