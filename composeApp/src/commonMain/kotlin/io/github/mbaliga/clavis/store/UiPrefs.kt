package io.github.mbaliga.clavis.store

import io.github.mbaliga.clavis.core.AppDirs
import io.github.mbaliga.clavis.core.state.FileIo
import io.github.mbaliga.clavis.ui.theme.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * UI-only preferences that are not part of the learner document (`Settings` in `:shared` has no
 * `theme` key yet). A tiny JSON file next to the state document; losing it costs nothing.
 */
@Serializable
data class UiPrefsData(val theme: ThemeMode = ThemeMode.SYSTEM)

class UiPrefs(private val dirs: AppDirs, private val fileName: String = "ui-prefs.json") {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val path: String get() = dirs.data.trimEnd('/') + "/" + fileName

    fun load(): UiPrefsData = try {
        FileIo.read(path)?.let { json.decodeFromString(UiPrefsData.serializer(), it.decodeToString()) } ?: UiPrefsData()
    } catch (e: Exception) {
        UiPrefsData()
    }

    fun save(data: UiPrefsData) {
        try {
            if (dirs.data.isNotEmpty()) FileIo.mkdirs(dirs.data)
            FileIo.write(path, json.encodeToString(UiPrefsData.serializer(), data).encodeToByteArray())
        } catch (_: Exception) {
        }
    }
}
