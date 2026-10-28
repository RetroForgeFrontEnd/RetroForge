package com.retroforge.pc

/**
 * What RetroForge knows about the device GPU, from whichever probes succeeded.
 * Each field is null when its probe was unavailable or refused (e.g. SELinux blocks kgsl sysfs).
 */
data class GpuIdentity(
    val glRenderer: String?,
    val kgslModel: String?,
    val socModel: String?,
)

enum class PcSupportLevel { SUPPORTED, EXPERIMENTAL, UNSUPPORTED, UNKNOWN }

data class PcSupport(
    val level: PcSupportLevel,
    val adrenoModel: Int?,
    val reason: String,
)

/**
 * Classifies a GPU against DroidDeck's published hardware requirements (README, commit 4f58230, 2026-10-28):
 * Adreno 730+ or 8xx supported; Adreno 6xx experimental; Mali, Xclipse, PowerVR and Adreno 710 unsupported.
 * DroidDeck's own code additionally treats 650 and 725 as tested, 720/722 as test drivers, and 850 as experimental.
 * Re-check this table whenever DroidDeck changes its requirements.
 */
object GpuClassifier {

    private val adrenoNumber = Regex("""adreno\D{0,12}(\d{3})""", RegexOption.IGNORE_CASE)

    fun adrenoModel(identity: GpuIdentity): Int? =
        listOfNotNull(identity.kgslModel, identity.glRenderer)
            .firstNotNullOfOrNull { adrenoNumber.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    fun classify(identity: GpuIdentity): PcSupport {
        val model = adrenoModel(identity)
            ?: return if (identity.glRenderer.isNullOrBlank() && identity.kgslModel.isNullOrBlank()) {
                PcSupport(PcSupportLevel.UNKNOWN, null, "The GPU model could not be read on this device.")
            } else {
                PcSupport(PcSupportLevel.UNSUPPORTED, null, "DroidDeck needs a Qualcomm Adreno GPU, and this device does not have one.")
            }
        return when {
            model == 850 -> PcSupport(PcSupportLevel.EXPERIMENTAL, model, "Adreno 850 support is experimental.")
            model in 800..899 -> PcSupport(PcSupportLevel.SUPPORTED, model, "Adreno 8xx is supported.")
            model in 730..799 -> PcSupport(PcSupportLevel.SUPPORTED, model, "Adreno 730 and newer are supported.")
            model in 725..729 -> PcSupport(
                PcSupportLevel.EXPERIMENTAL, model,
                "Adreno $model has been tested but is below the official 730 minimum.",
            )
            model == 720 || model == 722 -> PcSupport(
                PcSupportLevel.EXPERIMENTAL, model, "Adreno $model only has test drivers.",
            )
            model in 700..719 -> PcSupport(PcSupportLevel.UNSUPPORTED, model, "Adreno 710 is not supported.")
            model in 600..699 -> PcSupport(
                PcSupportLevel.EXPERIMENTAL, model,
                "Adreno 6xx is experimental: DirectX 11 games may run, DirectX 12 games may crash.",
            )
            else -> PcSupport(PcSupportLevel.UNSUPPORTED, model, "Adreno $model is older than the PC runtime supports.")
        }
    }
}
