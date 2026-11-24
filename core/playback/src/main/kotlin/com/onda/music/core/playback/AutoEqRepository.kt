package com.onda.music.core.playback

import android.content.Context
import com.onda.music.core.model.AutoEqCodec
import com.onda.music.core.model.AutoEqProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AutoEqRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val loadMutex = Mutex()

    @Volatile
    private var cachedProfiles: List<AutoEqProfile>? = null

    @Volatile
    private var profilesById: Map<String, AutoEqProfile> = emptyMap()

    suspend fun getAllProfiles(): List<AutoEqProfile> {
        cachedProfiles?.let { return it }
        return loadMutex.withLock {
            cachedProfiles?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    context.resources.openRawResource(R.raw.autoeq_index).use { stream ->
                        AutoEqCodec.decodeGzip(stream)
                    }
                }.getOrDefault(emptyList())
            }
            profilesById = loaded.associateBy { it.id }
            cachedProfiles = loaded
            loaded
        }
    }

    suspend fun search(query: String, limit: Int = 40): List<AutoEqProfile> {
        val all = getAllProfiles()
        return searchProfiles(all, query, limit)
    }

    fun findCachedByPresetId(presetId: String): AutoEqProfile? {
        if (!presetId.startsWith(AutoEqProfile.AUTO_EQ_PREFIX)) return null
        val id = presetId.removePrefix(AutoEqProfile.AUTO_EQ_PREFIX)
        return profilesById[id]
    }

    companion object {
        fun loadFromStream(inputStream: InputStream): List<AutoEqProfile> {
            return AutoEqCodec.decodeGzip(inputStream)
        }

        fun searchProfiles(
            profiles: List<AutoEqProfile>,
            query: String,
            limit: Int = 40
        ): List<AutoEqProfile> {
            val trimmed = query.trim().lowercase(Locale.ROOT)
            if (trimmed.isEmpty()) {
                return FEATURED_IDS.mapNotNull { id ->
                    profiles.firstOrNull { it.id == id }
                }.ifEmpty { profiles.take(limit) }.take(limit)
            }
            val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return profiles.take(limit)

            return profiles.asSequence()
                .mapNotNull { profile ->
                    val nameLower = profile.displayName.lowercase(Locale.ROOT)
                    val brandLower = profile.brand.lowercase(Locale.ROOT)
                    val modelLower = profile.model.lowercase(Locale.ROOT)
                    val typeLower = profile.formFactor.lowercase(Locale.ROOT)
                    val haystack = "$nameLower $typeLower"
                    if (!tokens.all { haystack.contains(it) }) return@mapNotNull null

                    var score = 0
                    if (nameLower == trimmed || modelLower == trimmed) score += 120
                    if (modelLower.startsWith(trimmed) || nameLower.startsWith(trimmed)) score += 60
                    if (brandLower == tokens.first()) score += 30
                    if (!profile.model.contains("(")) score += 15
                    score -= (profile.displayName.length / 8)
                    profile to score
                }
                .sortedWith(compareByDescending<Pair<AutoEqProfile, Int>> { it.second }.thenBy { it.first.displayName })
                .take(limit)
                .map { it.first }
                .toList()
        }

        private val FEATURED_IDS = listOf(
            "sony_wh_1000xm5",
            "sony_wf_1000xm5",
            "apple_airpods_pro_2_usb_c",
            "samsung_galaxy_buds2_pro",
            "sennheiser_hd_600",
            "sennheiser_hd_650",
            "moondrop_blessing_3",
            "moondrop_chu_ii",
            "truthear_hexa",
            "truthear_zero_red",
            "tangzu_wan_er_s_g",
            "7hz_salnotes_zero_2",
            "audio_technica_ath_m50x",
            "beyerdynamic_dt_770_pro_80_ohm",
            "hifiman_sundara_2020",
            "koss_porta_pro",
            "simgot_ea500_lm",
            "letshuoer_s12_pro",
            "fiio_ft1",
            "akg_k371"
        )
    }
}
