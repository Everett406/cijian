package com.cijian.app.ui.screens.blitz

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.InkButton
import com.cijian.app.ui.components.ToolTopBar
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper
import com.cijian.app.ui.theme.PaperDeep
import kotlinx.coroutines.delay

private const val THRESHOLD = 0.50f
private const val START_TIME = 90
private const val WRONG_PENALTY = 3
private const val CLEAR_BONUS = 15

@Composable
fun BlitzScreen(nav: NavHostController) {
    val app = LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store

    var cells by remember { mutableStateOf(listOf<String?>()) }   // null = 已消除
    var selected by remember { mutableStateOf(-1) }
    var wrongIdx by remember { mutableStateOf(setOf<Int>()) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var bestCombo by remember { mutableIntStateOf(0) }
    var pairsCleared by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(START_TIME) }
    var status by remember { mutableStateOf("playing") }   // playing / over
    var note by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf(false) }
    val noteAlpha by animateFloatAsState(if (pulse) 1f else 0.25f, tween(300), label = "note")

    fun newBoard() {
        val pool = LinkedHashSet<String>()
        var guard = 0
        while (pool.size < 8 && guard < 200) {
            val s = engine.randomCommonWord(twoChar = true)
            if (s in pool) { guard++; continue }
            val nb = engine.nearest(s, 6).firstOrNull { it.score >= 0.55f }?.word
            if (nb != null && nb !in pool) { pool.add(s); guard++ } else guard++
        }
        if (pool.size < 8) {   // 词池不足时补种子自身近邻的近邻
            for (s in pool.toList()) {
                if (pool.size >= 8) break
                val nb = engine.nearest(s, 10).firstOrNull { it.score >= 0.55f && it.word !in pool }?.word
                if (nb != null) pool.add(nb)
            }
        }
        val words = pool.flatMap { w ->
            val nb = engine.nearest(w, 8).firstOrNull { it.score >= 0.52f && it.word !in pool }?.word
            listOf(w, nb ?: w)
        }.shuffled()
        cells = words
        selected = -1; wrongIdx = emptySet(); combo = 0
        note = "找出相似度过半的词对"
    }

    fun newGame() {
        score = 0; pairsCleared = 0; bestCombo = 0
        timeLeft = START_TIME; status = "playing"
        newBoard()
    }

    LaunchedEffect(Unit) { newGame() }

    // 倒计时
    LaunchedEffect(status) {
        while (status == "playing" && timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        if (status == "playing" && timeLeft <= 0) {
            status = "over"
            store.addHistory("消消", "词海消消", "得分 $score · 消除 $pairsCleared 对 · 最高连击 $bestCombo")
        }
    }

    // 判错闪烁自动消退
    LaunchedEffect(wrongIdx) {
        if (wrongIdx.isNotEmpty()) {
            delay(450)
            wrongIdx = emptySet()
        }
    }

    fun flashNote(t: String) {
        note = t; pulse = !pulse
    }

    fun tapCell(i: Int) {
        if (status != "playing") return
        val w = cells.getOrNull(i) ?: return
        if (selected == i) { selected = -1; return }
        if (selected < 0) { selected = i; return }
        val a = cells[selected] ?: return
        val sim = engine.similarity(a, w)
        if (sim >= THRESHOLD) {
            combo++
            if (combo > bestCombo) bestCombo = combo
            val mult = minOf(combo, 4)
            val gain = (10 + ((sim - THRESHOLD) * 40).toInt()) * mult
            score += gain
            pairsCleared++
            val nc = cells.toMutableList(); nc[selected] = null; nc[i] = null
            cells = nc
            flashNote("「$a」×「$w」 相似 %.2f · +$gain${if (mult > 1) "（连击 ×$mult）" else ""}")
            if (nc.all { it == null }) {
                timeLeft += CLEAR_BONUS
                flashNote("一片词海退去 · 时间 +${CLEAR_BONUS}s")
                newBoard()
            }
        } else {
            combo = 0
            timeLeft = (timeLeft - WRONG_PENALTY).coerceAtLeast(0)
            wrongIdx = setOf(selected, i)
            flashNote("「$a」与「$w」相似度仅 %.2f · -${WRONG_PENALTY}s".format(sim))
            if (timeLeft <= 0) {
                status = "over"
                store.addHistory("消消", "词海消消", "得分 $score · 消除 $pairsCleared 对 · 最高连击 $bestCombo")
            }
        }
        selected = -1
    }

    Column(Modifier.fillMaxSize()) {
        ToolTopBar(title = "词海消消") { nav.popBackStack() }

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // 计分板
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$score", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Cinnabar, lineHeight = 32.sp)
                    Text("得分", color = InkSoft, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$timeLeft", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (timeLeft <= 15) Cinnabar else MaterialTheme.colorScheme.primary, lineHeight = 26.sp)
                    Text("秒", color = InkSoft, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (combo >= 2) "连击 ×${minOf(combo, 4)}" else "—",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (combo >= 2) Indigo else InkFaint, lineHeight = 24.sp
                    )
                    Text("消除 $pairsCleared 对", color = InkSoft, fontSize = 11.sp)
                }
            }
            Text(
                note, color = InkSoft, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = noteAlpha },
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))

            if (status == "over") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(PaperDeep)
                        .border(1.dp, Cinnabar, RoundedCornerShape(6.dp))
                        .padding(20.dp)
                ) {
                    Text("潮水退去", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Cinnabar)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "得分 $score · 消除 $pairsCleared 对 · 最高连击 ×$bestCombo",
                        color = InkSoft, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CinnabarButton(text = "再来一局", onClick = { newGame() })
                        InkButton(text = "离开", onClick = { nav.popBackStack() })
                    }
                }
            } else {
                // 4×4 词格
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 4) {
                                val i = row * 4 + col
                                val w = cells.getOrNull(i)
                                val isSel = selected == i
                                val isWrong = i in wrongIdx
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(0.9f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            when {
                                                isWrong -> Cinnabar.copy(alpha = 0.14f)
                                                isSel -> Paper
                                                w == null -> PaperDeep.copy(alpha = 0.35f)
                                                else -> MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isWrong -> Cinnabar
                                                isSel -> Cinnabar
                                                else -> InkFaint
                                            },
                                            RoundedCornerShape(5.dp)
                                        )
                                        .clickable(enabled = w != null) { tapCell(i) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (w != null) {
                                        Text(
                                            w, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                            color = if (isSel) Cinnabar else MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center, maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "玩法：点两个词，相似度 ≥ 0.50 即消除；越贴切分越高，连击最多 ×4。猜错 -${WRONG_PENALTY}s，清空全场 +${CLEAR_BONUS}s。",
                    color = InkFaint, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                InkButton(text = "洗牌（重新排列剩余词）", onClick = {
                    val remain = cells.filterNotNull().shuffled()
                    var k = 0
                    cells = cells.map { if (it == null) null else remain[k++] }
                    selected = -1
                    flashNote("词海重新排布")
                })
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
