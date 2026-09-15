package io.github.mbaliga.clavis.core.state

import io.github.mbaliga.clavis.core.AppDirs
import io.github.mbaliga.clavis.core.BanditState
import io.github.mbaliga.clavis.core.ClavisState
import io.github.mbaliga.clavis.core.Clock
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.LanguageRegistry
import io.github.mbaliga.clavis.core.StateStore
import io.github.mbaliga.clavis.core.SystemClock
import io.github.mbaliga.clavis.core.freshState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One JSON document per profile in the app data directory (D-12), written atomically
 * (temp file + rename). `load()` reproduces the web build's merge-on-load (template-spec §1.4):
 * a shallow overlay of the stored keys on a fresh document, `settings` merged one level,
 * `coach = stored.coach || {}`, the schema version never rejected, unknown top-level keys kept
 * and written back. On top of that, missing per-lane maps are filled from the registry
 * (`// D-12` — the documented fix of the web build's non-merging).
 *
 * `importJson` accepts a v0.10 export (`v:2`, lowercase lane modes, lazily created keys, extra
 * keys) — it requires a `cards` key, like the web import — and `exportJson` writes the full
 * document with lowercase lane modes so the web build can read it back.
 */
@OptIn(ExperimentalSerializationApi::class)
class JsonStateStore(
    private val dirs: AppDirs,
    private val registry: LanguageRegistry,
    private val clock: Clock = SystemClock,
    private val fileName: String = "clavis-state.json",
) : StateStore {

    val path: String get() = dirs.data.trimEnd('/') + "/" + fileName
    private val tmpPath: String get() = "$path.tmp"

    /** The document last loaded, saved or imported — the object the UI mutates in place. */
    var current: ClavisState? = null
        private set

    /** Top-level keys of the stored document that the model does not know; preserved across saves (§1.4 c). */
    var extras: Map<String, JsonElement> = emptyMap()
        private set

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true // "prod" → PROD
        encodeDefaults = true // the export carries every key, like JSON.stringify(S)
        explicitNulls = false
    }

    private val knownKeys: Set<String> = ClavisState.serializer().descriptor.elementNames.toSet()

    /**
     * True when the last [load] met a document it could not decode. That file was moved to
     * [corruptPath] before the fresh document took its place, so no later save can overwrite it;
     * the UI turns this into the [Copy.STATE_UNREADABLE] notice.
     */
    var lastLoadWasCorrupt: Boolean = false
        private set

    /**
     * Directory holding unreadable-document backups (`corrupt/`, a sibling of the state file), kept
     * out of Android cloud backup as a single directory-level exclude — see
     * `composeApp/src/androidMain/res/xml/data_extraction_rules.xml` — since Android's backup rules
     * have no wildcard support and these files carry a timestamp in their name.
     */
    private val corruptDir: String get() = dirs.data.trimEnd('/') + "/corrupt"

    /** Where an unreadable document is moved: `corrupt/clavis-state.json.corrupt-<epochMillis>` (`.2`, `.3`… when taken). */
    fun corruptPath(epochMillis: Long): String {
        val base = "$corruptDir/$fileName.corrupt-$epochMillis"
        var p = base
        var n = 2
        while (FileIo.exists(p)) p = "$base.${n++}"
        return p
    }

    override suspend fun load(): ClavisState? {
        val bytes = FileIo.read(path) ?: return null
        lastLoadWasCorrupt = false
        val state = try {
            merge(json.parseToJsonElement(bytes.decodeToString()).jsonObject)
        } catch (e: Exception) {
            // Web parity: any failure yields a fresh document — but the unreadable bytes are kept beside it,
            // never replaced by the next save.
            extras = emptyMap()
            lastLoadWasCorrupt = true
            runCatching { FileIo.mkdirs(corruptDir); FileIo.rename(path, corruptPath(clock.now())) }
            freshState(registry, clock.now())
        }
        current = state
        return state
    }

    /** `load()` or a fresh document when nothing is stored yet. */
    suspend fun loadOrFresh(): ClavisState = load() ?: freshState(registry, clock.now()).also { current = it; extras = emptyMap() }

    /** §1.4 merge of a stored/imported document onto a fresh one, plus the D-12 lane fill. Public for tests. */
    fun merge(stored: JsonObject): ClavisState {
        val fresh = json.encodeToJsonElement(ClavisState.serializer(), freshState(registry, clock.now())).jsonObject
        val merged = LinkedHashMap<String, JsonElement>(fresh)
        for ((k, v) in stored) merged[k] = v // shallow: a stored top-level key replaces the default wholesale
        val storedSettings = stored["settings"] as? JsonObject ?: JsonObject(emptyMap())
        merged["settings"] = JsonObject(fresh.getValue("settings").jsonObject + storedSettings) // one level deep
        merged["coach"] = stored["coach"] as? JsonObject ?: JsonObject(emptyMap())
        normalise(merged)
        extras = merged.filterKeys { it !in knownKeys }
        val state = json.decodeFromJsonElement(ClavisState.serializer(), JsonObject(merged.filterKeys { it in knownKeys }))
        fillLanes(state)
        return state
    }

    /** Tolerances for web-build documents that the typed model would otherwise reject. */
    private fun normalise(m: MutableMap<String, JsonElement>) {
        // log entries: `ok` may be a boolean in hand-edited exports; the model stores 0/1
        (m["log"] as? JsonArray)?.let { arr ->
            if (arr.any { (it as? JsonObject)?.get("ok")?.let { ok -> ok is JsonPrimitive && ok.booleanOrNull != null } == true }) {
                m["log"] = JsonArray(arr.map { e ->
                    val o = e as? JsonObject ?: return@map e
                    val ok = o["ok"] as? JsonPrimitive
                    if (ok != null && ok.booleanOrNull != null) JsonObject(o + ("ok" to JsonPrimitive(if (ok.booleanOrNull == true) 1 else 0))) else o
                })
            }
        }
        // cosmos v3 migration (template L870; parity item 4)
        (m["cosmos"] as? JsonObject)?.let { c ->
            val v3 = (c["v3"] as? JsonPrimitive)?.booleanOrNull == true
            if (!v3) {
                val mode = (c["mode"] as? JsonPrimitive)?.contentOrNull
                m["cosmos"] = JsonObject(
                    c + mapOf(
                        "mode" to JsonPrimitive(if (mode == "top") "explore" else "depth"),
                        "exlens" to JsonPrimitive("off"),
                        "exsets" to JsonPrimitive(false),
                        "v3" to JsonPrimitive(true),
                    ),
                )
            }
        }
        // explicit JSON nulls for non-nullable maps would fail to decode; drop them so defaults apply
        for (k in listOf("lanes", "cards", "tests", "seen", "bandit", "log", "inter", "routes", "newToday", "coach", "lastScene", "pathLog", "story", "cosmos", "media", "settings")) {
            if (m[k] is JsonNull) m.remove(k)
        }
    }

    /** D-12: every registry language gets its per-lane maps; nothing already stored is touched. */
    private fun fillLanes(state: ClavisState) {
        for (info in registry.all) {
            val l = info.iso.code
            if (state.lanes[l] == null) state.lanes[l] = if (info.enabledByDefault) info.defaultLane else LaneMode.OFF
            if (state.cards[l] == null) state.cards[l] = mutableMapOf()
            if (state.bandit[l] == null) state.bandit[l] = BanditState()
            if (state.inter[l] == null) state.inter[l] = mutableMapOf()
            if (state.routes[l] == null) state.routes[l] = mutableMapOf()
            if (state.newToday[l] == null) state.newToday[l] = 0
        }
    }

    /** Full document as JSON: lane modes lowercased for the web build, unknown keys carried along. */
    fun encode(state: ClavisState): String {
        val obj = LinkedHashMap(json.encodeToJsonElement(ClavisState.serializer(), state).jsonObject)
        obj["lanes"]?.let { lanes ->
            obj["lanes"] = JsonObject(lanes.jsonObject.mapValues { (_, v) -> JsonPrimitive(v.jsonPrimitive.content.lowercase()) })
        }
        for ((k, v) in extras) if (k !in obj) obj[k] = v
        return json.encodeToString(JsonObject.serializer(), JsonObject(obj))
    }

    /**
     * Writes an already-encoded document atomically: temp file, fsync, rename over the previous
     * one. Blocking; the UI runs it on its single writer thread after encoding on the UI thread.
     */
    fun writeEncoded(jsonText: String) {
        val dir = dirs.data
        if (dir.isNotEmpty()) FileIo.mkdirs(dir)
        FileIo.write(tmpPath, jsonText.encodeToByteArray())
        FileIo.rename(tmpPath, path)
    }

    override suspend fun save(state: ClavisState) {
        current = state
        writeEncoded(encode(state))
    }

    override suspend fun exportJson(): String = encode(current ?: loadOrFresh())

    /**
     * Web-build import (template L2323): requires a `cards` key, otherwise throws
     * [IllegalArgumentException] carrying [Copy.NOT_A_BACKUP] for the UI's toast. On success the
     * merged document becomes [current], is saved, and is returned. Every failure path throws
     * before anything is written, so neither [current] nor the stored file is touched by a bad
     * backup.
     */
    override suspend fun importJson(json: String): ClavisState {
        val obj = try { this.json.parseToJsonElement(json) as? JsonObject } catch (e: Exception) { null }
            ?: throw IllegalArgumentException(Copy.NOT_A_BACKUP)
        val cards = obj["cards"]
        if (cards == null || cards is JsonNull) throw IllegalArgumentException(Copy.NOT_A_BACKUP)
        val state = try { merge(obj) } catch (e: Exception) { throw IllegalArgumentException(Copy.NOT_A_BACKUP, e) }
        current = state
        save(state)
        return state
    }

    override suspend fun reset() {
        FileIo.delete(path)
        FileIo.delete(tmpPath)
        current = null
        extras = emptyMap()
    }
}
