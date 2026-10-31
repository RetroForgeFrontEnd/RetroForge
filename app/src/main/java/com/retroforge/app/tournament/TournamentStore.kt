package com.retroforge.app.tournament

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Tournaments kept in one JSON file in the app's own storage, newest first. */
class TournamentStore(private val file: File) {
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Tournament>> = _all

    fun byId(id: String) = _all.value.firstOrNull { it.id == id }

    fun save(t: Tournament) {
        // A tournament is finished when it has a winner; changing a result can take that away again.
        val won = Tournaments.champion(t) != null
        val saved = t.copy(finishedAt = if (won) t.finishedAt ?: System.currentTimeMillis() else null)
        write(listOf(saved) + _all.value.filter { it.id != t.id })
    }

    fun delete(id: String) = write(_all.value.filter { it.id != id })

    private fun write(list: List<Tournament>) {
        _all.value = list.sortedByDescending { it.createdAt }
        val array = JSONArray()
        _all.value.forEach { t ->
            val results = JSONObject()
            t.results.forEach { (key, s) -> results.put(key, JSONArray().put(s.a).put(s.b)) }
            array.put(
                JSONObject().put("id", t.id).put("name", t.name).put("game", t.game).put("format", t.format.name)
                    .put("players", JSONArray(t.players)).put("rules", t.rules).put("results", results)
                    .put("createdAt", t.createdAt).put("finishedAt", t.finishedAt ?: JSONObject.NULL),
            )
        }
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".new")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
    }

    private fun load(): List<Tournament> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { array.getJSONObject(it) }.mapNotNull { o ->
            runCatching {
                val players = o.getJSONArray("players").let { p -> (0 until p.length()).map { p.getString(it) } }
                val r = o.optJSONObject("results") ?: JSONObject()
                val results = r.keys().asSequence().associateWith { k -> r.getJSONArray(k).let { Score(it.getInt(0), it.getInt(1)) } }
                Tournament(
                    o.getString("id"), o.getString("name"), o.optString("game"), TournamentFormat.valueOf(o.getString("format")),
                    players, o.optString("rules"), results, o.optLong("createdAt"),
                    if (o.isNull("finishedAt")) null else o.optLong("finishedAt"),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(8)
    }
}
