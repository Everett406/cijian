package com.cijian.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CalcItem(val expr: String, val result: String, val score: Float, val time: Long)

@Serializable
data class PathItem(val from: String, val to: String, val stops: List<String>, val time: Long)

@Serializable
data class Favorites(
    val words: List<String> = emptyList(),
    val calcs: List<CalcItem> = emptyList(),
    val paths: List<PathItem> = emptyList()
)

@Serializable
data class HistoryItem(val type: String, val title: String, val detail: String, val time: Long)

@Serializable
data class GuessStats(val streak: Int = 0, val best: Int = 0, val totalWins: Int = 0)

/**
 * 收藏与历史：kotlinx.serialization + JSON 文件持久化（filesDir）。
 * 不引入 Room，保持工程极简。
 */
class Store(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val favFile = context.filesDir.resolve("favorites.json")
    private val histFile = context.filesDir.resolve("history.json")

    private val _favorites = MutableStateFlow(Favorites())
    val favorites: StateFlow<Favorites> = _favorites

    private val _history = MutableStateFlow(listOf<HistoryItem>())
    val history: StateFlow<List<HistoryItem>> = _history

    private val _guessStats = MutableStateFlow(GuessStats())
    val guessStats: StateFlow<GuessStats> = _guessStats
    private val statsFile = context.filesDir.resolve("guess_stats.json")

    init {
        scope.launch {
            runCatching {
                if (favFile.exists()) _favorites.value = json.decodeFromString<Favorites>(favFile.readText())
            }
            runCatching {
                if (histFile.exists()) _history.value = json.decodeFromString<List<HistoryItem>>(histFile.readText())
            }
            runCatching {
                if (statsFile.exists()) _guessStats.value = json.decodeFromString<GuessStats>(statsFile.readText())
            }
        }
    }

    // ── 收藏 ──

    fun isWordFav(word: String): Boolean = _favorites.value.words.contains(word)

    fun toggleWord(word: String) {
        val cur = _favorites.value
        val words = if (cur.words.contains(word)) cur.words - word else listOf(word) + cur.words
        _favorites.value = cur.copy(words = words.take(300))
        persistFav()
    }

    fun addCalc(expr: String, result: String, score: Float) {
        val cur = _favorites.value
        _favorites.value = cur.copy(calcs = (listOf(CalcItem(expr, result, score, System.currentTimeMillis())) + cur.calcs).take(100))
        persistFav()
    }

    fun addPath(from: String, to: String, stops: List<String>) {
        val cur = _favorites.value
        _favorites.value = cur.copy(paths = (listOf(PathItem(from, to, stops, System.currentTimeMillis())) + cur.paths).take(100))
        persistFav()
    }

    fun removeCalc(item: CalcItem) {
        _favorites.value = _favorites.value.copy(calcs = _favorites.value.calcs - item)
        persistFav()
    }

    fun removePath(item: PathItem) {
        _favorites.value = _favorites.value.copy(paths = _favorites.value.paths - item)
        persistFav()
    }

    fun clearFavorites() {
        _favorites.value = Favorites()
        persistFav()
    }

    // ── 历史 ──

    fun addHistory(type: String, title: String, detail: String = "") {
        _history.value = (listOf(HistoryItem(type, title, detail, System.currentTimeMillis())) + _history.value).take(150)
        persistHist()
    }

    fun clearHistory() {
        _history.value = emptyList()
        persistHist()
    }

    // ── 猜词战绩 ──

    /** 猜中一局：连胜 +1，更新最佳与累计 */
    fun recordGuessWin() {
        val cur = _guessStats.value
        _guessStats.value = cur.copy(
            streak = cur.streak + 1,
            best = maxOf(cur.best, cur.streak + 1),
            totalWins = cur.totalWins + 1
        )
        persistStats()
    }

    /** 弃局或猜错一整局：连胜清零（最佳保留） */
    fun resetGuessStreak() {
        if (_guessStats.value.streak == 0) return
        _guessStats.value = _guessStats.value.copy(streak = 0)
        persistStats()
    }

    private fun persistStats() {
        val snap = _guessStats.value
        scope.launch { runCatching { statsFile.writeText(json.encodeToString(snap)) } }
    }

    // ── 持久化 ──

    private fun persistFav() {
        val snap = _favorites.value
        scope.launch { runCatching { favFile.writeText(json.encodeToString(snap)) } }
    }

    private fun persistHist() {
        val snap = _history.value
        scope.launch { runCatching { histFile.writeText(json.encodeToString(snap)) } }
    }
}
