package com.cijian.app.ui.screens.guess

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.cijian.app.ui.components.EmptyHint
import com.cijian.app.ui.components.InkButton
import com.cijian.app.ui.components.ScoreBar
import com.cijian.app.ui.components.SectionHeader
import com.cijian.app.ui.components.ToolTopBar
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper
import com.cijian.app.ui.theme.PaperDeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Guess(val word: String, val cos: Float, val pct: Float)

private fun temperature(pct: Float): String = when {
    pct >= 82f -> "滚烫"
    pct >= 66f -> "很热"
    pct >= 50f -> "温热"
    pct >= 34f -> "微温"
    pct >= 20f -> "发凉"
    else -> "冰冷"
}

@Composable
fun GuessScreen(nav: NavHostController) {
    val app = LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store

    var target by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var guessList by remember { mutableStateOf(listOf<Guess>()) }
    var hint by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("playing") }   // playing / won / gaveup
    var rounds by remember { mutableIntStateOf(0) }        // 累计猜中局数
    var usedTargets by remember { mutableStateOf(setOf<String>()) }

    fun newGame() {
        var w = engine.randomCommonWord(twoChar = true)
        var guard = 0
        while (w in usedTargets && guard < 30) { w = engine.randomCommonWord(twoChar = true); guard++ }
        target = w
        usedTargets = usedTargets + w
        input = ""; guessList = emptyList(); hint = ""; status = "playing"
    }

    LaunchedEffect(Unit) { newGame() }

    fun submitGuess(raw: String, isHint: Boolean = false) {
        val w = raw.trim()
        if (w.isEmpty() || status != "playing") return
        if (!engine.contains(w)) { hint = "词表里没有「$w」，换一个常用的词试试"; return }
        hint = ""
        val cos = engine.similarity(w, target)
        val pct = if (w == target) 100f else engine.relevancePct(cos)
        guessList = (listOf(Guess(w, cos, pct)) + guessList).sortedByDescending { it.pct }
        input = ""
        if (w == target) {
            status = "won"
            rounds++
            store.addHistory("猜词", "猜词·$target", "第 ${guessList.size} 次猜中")
        } else if (isHint) {
            hint = "帮你随机试了一个：$w"
        }
    }

    Column(Modifier.fillMaxSize()) {
        ToolTopBar(title = "语义猜词") { nav.popBackStack() }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            item {
                Text("猜猜那个隐藏的词", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (status == "playing") "它是一个${target.length}字的常用词 · 输入任意词，我用相关度告诉你多接近"
                    else "本局已结束",
                    color = InkSoft, fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本局已猜 ${guessList.size} 次", color = InkSoft, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("累计猜中 $rounds 局", color = Cinnabar, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
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
                            if (status == "won") "猜中了！答案是「$target」" else "已放弃，答案是「$target」",
                            color = Cinnabar, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (status == "won") "用了 ${guessList.size} 次机会 · 最近的一次提示是「${guessList.firstOrNull()?.word ?: ""}」"
                            else "离得最近的一次：「${guessList.maxByOrNull { it.pct }?.word ?: "（未猜过）"}」",
                            color = InkSoft, fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CinnabarButton(text = "再来一局", onClick = { newGame() })
                            InkButton(text = "看看这个词", onClick = {
                                store.addHistory("词", target, "猜词答案")
                                nav.navigate(com.cijian.app.ui.nav.Routes.word(target))
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("输入一个猜测，如「火车」", color = InkFaint, fontSize = 14.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(4.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = Cinnabar,
                                    unfocusedIndicatorColor = InkFaint,
                                ),
                            )
                            Spacer(Modifier.width(10.dp))
                            CinnabarButton(text = "猜！", onClick = { submitGuess(input) })
                        }
                        if (hint.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(hint, color = if (hint.startsWith("帮你")) Indigo else Cinnabar, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        InkButton(text = "随便猜一个（消耗一次机会）", onClick = {
                            submitGuess(engine.randomCommonWord(), isHint = true)
                        })
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (guessList.isEmpty()) {
                item { EmptyHint("从任何一个词开始都可以 · 越接近，相关度越高") }
            } else {
                item {
                    SectionHeader("猜测记录 · 按相关度", trailing = {
                        if (status == "playing") {
                            Text(
                                "放弃本局",
                                color = InkFaint, fontSize = 12.sp,
                                modifier = Modifier.clickable { status = "gaveup" }
                            )
                        }
                    })
                }
                items(guessList.size) { i ->
                    val g = guessList[i]
                    val isAnswer = g.word == target
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            store.addHistory("词", g.word, "猜词猜测")
                            nav.navigate(com.cijian.app.ui.nav.Routes.word(g.word))
                        }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            g.word, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = if (isAnswer) Cinnabar else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(110.dp), maxLines = 1
                        )
                        Column(Modifier.weight(1f)) {
                            ScoreBar(g.pct / 100f, color = if (isAnswer) Cinnabar else Indigo)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${temperature(g.pct)} · %.2f%%".format(g.pct),
                                color = if (isAnswer) Cinnabar else InkSoft, fontSize = 11.sp
                            )
                        }
                        Text(
                            "%.2f%%".format(g.pct),
                            color = if (isAnswer) Cinnabar else InkSoft,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(84.dp).padding(start = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
