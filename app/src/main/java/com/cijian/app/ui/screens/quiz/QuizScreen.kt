package com.cijian.app.ui.screens.quiz

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.core.QuizPairs
import com.cijian.app.core.QuizQuestion
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.InkButton
import com.cijian.app.ui.components.ToolTopBar
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper
import com.cijian.app.ui.theme.PaperDeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QuizScreen(nav: NavHostController) {
    val app = LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store

    var question by remember { mutableStateOf<QuizQuestion?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun launchNext() {
        scope.launch(Dispatchers.Default) {
            val q = engine.generateQuiz(QuizPairs.pairs)
            withContext(Dispatchers.Main) {
                question = q
                picked = null   // 关键：新题必须清空上一题的选中态，否则会自动亮出答案
            }
        }
    }

    LaunchedEffect(Unit) { launchNext() }

    Column(Modifier.fillMaxSize()) {
        ToolTopBar(title = "类比测验") { nav.popBackStack() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("A 之于 B，如同 C 之于？", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("选一个词，把第二组关系补全", color = InkSoft, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("答对 $score / $total", color = InkSoft, fontSize = 13.sp)
                    Text("连对 $streak", color = Cinnabar, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        val q = question
        if (q == null) {
            item {
                Text(
                    "出题中…",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    color = InkSoft, textAlign = TextAlign.Center
                )
            }
        } else {
            item {
                Text(
                    "${q.a} 之于 ${q.b}",
                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text(
                    "如同  ${q.c} 之于 ？",
                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 26.dp),
                    textAlign = TextAlign.Center
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (opt in q.options) {
                        val isAnswer = opt == q.answer
                        val chosen = picked != null
                        val revealed = chosen && isAnswer
                        val wrongPick = chosen && picked == opt && !isAnswer
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    when {
                                        revealed -> Cinnabar
                                        wrongPick -> PaperDeep
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        revealed -> Cinnabar
                                        wrongPick -> Cinnabar
                                        else -> InkFaint
                                    },
                                    RoundedCornerShape(5.dp)
                                )
                                .clickable(enabled = !chosen) {
                                    picked = opt
                                    total++
                                    if (isAnswer) { score++; streak++ } else streak = 0
                                    store.addHistory("测验", "类比·${q.a}", "${q.a}→${q.b} · 答案 ${q.answer}")
                                }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (revealed) {
                                    Icon(Icons.Outlined.Check, null, tint = Paper, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                } else if (wrongPick) {
                                    Icon(Icons.Outlined.Close, null, tint = Cinnabar, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    opt,
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                    color = when {
                                        revealed -> Paper
                                        wrongPick -> Cinnabar
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            item {
                if (picked != null) {
                    Text(
                        "答案「${q.answer}」 · 与目标向量的余弦相似度 %.3f\n推理式：${q.b} － ${q.a} ＋ ${q.c} ≈ ${q.answer}".format(q.answerScore),
                        color = InkSoft, fontSize = 13.sp, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CinnabarButton("下一题", onClick = { launchNext() })
                    InkButton("去算一道", onClick = { nav.navigate(com.cijian.app.ui.nav.Routes.tool("calc")) })
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    }
}
