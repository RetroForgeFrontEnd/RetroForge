package com.retroforge.pc

import com.retroforge.core.ui.str

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
                PcSupport(PcSupportLevel.UNKNOWN, null, str(R.string.gpu_support_the_gpu_model_could_not_be))
            } else {
                PcSupport(PcSupportLevel.UNSUPPORTED, null, str(R.string.gpu_support_droiddeck_needs_a_qualcomm_adreno_gpu))
            }
        return when {
            model == 850 -> PcSupport(PcSupportLevel.EXPERIMENTAL, model, str(R.string.gpu_support_adreno_850_support_is_experimental))
            model in 800..899 -> PcSupport(PcSupportLevel.SUPPORTED, model, str(R.string.gpu_support_adreno_8xx_is_supported))
            model in 730..799 -> PcSupport(PcSupportLevel.SUPPORTED, model, str(R.string.gpu_support_adreno_730_and_newer_are_supported))
            model in 725..729 -> PcSupport(
                PcSupportLevel.EXPERIMENTAL, model,
                str(R.string.gpu_support_adreno_has_been_tested_but_is, model),
            )
            model == 720 || model == 722 -> PcSupport(
                PcSupportLevel.EXPERIMENTAL, model, str(R.string.gpu_support_adreno_only_has_test_drivers, model),
            )
            model in 700..719 -> PcSupport(PcSupportLevel.UNSUPPORTED, model, str(R.string.gpu_support_adreno_710_is_not_supported))
            model in 600..699 -> PcSupport(
                PcSupportLevel.EXPERIMENTAL, model,
                str(R.string.gpu_support_adreno_6xx_is_experimental_directx_11),
            )
            else -> PcSupport(PcSupportLevel.UNSUPPORTED, model, str(R.string.gpu_support_adreno_is_older_than_the_pc, model))
        }
    }
}
