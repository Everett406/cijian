package com.cijian.app.ui.screens.bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.InkButton
import com.cijian.app.ui.components.ScoreBar
import com.cijian.app.ui.components.SectionHeader
import com.cijian.app.ui.components.ToolTopBar
import com.cijian.app.ui.components.WordChip
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper
import com.cijian.app.ui.theme.PaperDeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_STEPS = 10

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BridgeScreen(nav: NavHostController) {
    val app = LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store
    val scope = rememberCoroutineScope()

    var start by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var trail by remember { mutableStateOf(listOf<String>()) }
    var suggestions by remember { mutableStateOf(listOf<com.cijian.app.core.Scored>()) }
    var input by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("playing") }   // playing / won / lost
    var wins by remember { mutableIntStateOf(0) }
    var reference by remember { mutableStateOf(listOf<String>()) }

    fun loadSuggestions(w: String) {
        scope.launch(Dispatchers.Default) {
            val nb = engine.nearest(w, 8, exclude = trail.toSet() + start)
            withContext(Dispatchers.Main) { suggestions = nb }
        }
    }

    fun newBridge() {
        var s = engine.randomCommonWord(twoChar = true)
        var t = engine.randomCommonWord(twoChar = true)
        var sim = engine.similarity(s, t)
        var guard = 0
        while ((t == s || sim < 0.05f || sim > 0.45f) && guard < 200) {
            t = engine.randomCommonWord(twoChar = true)
            sim = engine.similarity(s, t)
            guard++
        }
        start = s; target = t; current = s
        trail = listOf(s); input = ""; hint = ""; status = "playing"; reference = emptyList()
        loadSuggestions(s)
    }

    LaunchedEffect(Unit) { newBridge() }

    fun moveTo(w: String) {
        if (status != "playing") return
        if (w == current) { hint = "你已经站在「$w」上了"; return }
        if (!engine.contains(w)) { hint = "词表里没有「$w」"; return }
        hint = ""
        val nextTrail = trail + w
        trail = nextTrail
        current = w
        input = ""
        if (w == target) {
            status = "won"
            wins++
            store.addPath(start, target, nextTrail)
            store.addHistory("词桥", "$start → $target", "${nextTrail.size - 1} 步走通")
        } else if (nextTrail.size - 1 >= MAX_STEPS) {
            status = "lost"
            store.addHistory("词桥", "$start → $target", "未走通")
            scope.launch(Dispatchers.Default) {
                val p = engine.path(current, target, 6).map { it.word }
                withContext(Dispatchers.Main) { reference = p }
            }
        } else {
            loadSuggestions(w)
        }
    }

    val cosToTarget = if (current.isEmpty() || target.isEmpty()) 0f else engine.similarity(current, target)
    val pct = engine.relevancePct(cosToTarget)
    val stepsUsed = trail.size - 1

    Column(Modifier.fillMaxSize()) {
        ToolTopBar(title = "词桥") { nav.popBackStack() }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("从「$start」走到「$target」", fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("每一步只能走到当前词的近邻，或直接跳到任意词表内的词", color = InkSoft, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("胜 $wins 局", color = Cinnabar, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("步数 $stepsUsed / $MAX_STEPS", color = InkSoft, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, InkFaint, RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(current, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = if (status == "won") Cinnabar else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("离终点", color = InkSoft, fontSize = 11.sp)
                            Text("%.2f%%".format(pct), color = Cinnabar, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ScoreBar(pct / 100f)
                }
                Spacer(Modifier.height(12.dp))
            }

            if (status != "playing") {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (status == "won") Paper else PaperDeep)
                            .border(1.dp, Cinnabar, RoundedCornerShape(6.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            if (status == "won") "走通了！${stepsUsed} 步抵达「$target」" else "步数用尽，「$target」还在前方",
                            color = Cinnabar, fontSize = 17.sp, fontWeight = FontWeight.Bold
                        )
                        if (status == "lost" && reference.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "参考走法：${current} → ${reference.joinToString(" → ")}",
                                color = InkSoft, fontSize = 13.sp, lineHeight = 19.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CinnabarButton(text = "再搭一座", onClick = { newBridge() })
                            InkButton(text = "收藏此路", onClick = {
                                store.addPath(start, target, trail)
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                item {
                    SectionHeader("从「$current」迈一步", trailing = {
                        Text("点词即走", color = InkFaint, fontSize = 11.sp)
                    })
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { s ->
                            WordChip(
                                word = s.word,
                                score = s.score,
                                accent = Indigo,
                                onClick = { moveTo(s.word) }
                            )
                        }
                        if (suggestions.isEmpty()) {
                            Text("正在寻找落脚点…", color = InkFaint, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("或直接跳到任意词", color = InkFaint, fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(4.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Cinnabar,
                                unfocusedIndicatorColor = InkFaint,
                            ),
                        )
                        Spacer(Modifier.padding(start = 10.dp))
                        InkButton(text = "跳转", onClick = { moveTo(input) })
                    }
                    if (hint.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(hint, color = Cinnabar, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                SectionHeader("足迹", trailing = {
                    Text("${trail.size} 词", color = InkSoft, fontSize = 11.sp)
                })
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    trail.forEachIndexed { i, w ->
                        Text(
                            if (i == trail.lastIndex) w else "$w →",
                            color = when {
                                i == 0 -> Indigo
                                i == trail.lastIndex && status == "won" -> Cinnabar
                                else -> InkSoft
                            },
                            fontSize = 14.sp,
                            fontWeight = if (i == 0 || i == trail.lastIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "小知识：词向量空间里「相近的词往往互相认识」，沿着近邻走，常常比直接跳更稳。",
                    color = InkFaint, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
    }
}
