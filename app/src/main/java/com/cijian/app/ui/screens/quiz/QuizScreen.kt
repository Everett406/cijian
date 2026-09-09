package com.cijian.app.ui.screens.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.cijian.app.core.QuizKind
import com.cijian.app.core.QuizLevel
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
    var kind by remember { mutableStateOf(QuizKind.MIX) }
    var level by remember { mutableStateOf(QuizLevel.NORMAL) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun launchNext(k: QuizKind) {
        val real = if (k == QuizKind.MIX)
            listOf(QuizKind.ANALOGY, QuizKind.CALC, QuizKind.ODD, QuizKind.CLOSEST).random()
        else k
        scope.launch(Dispatchers.Default) {
            // 首选题型生成失败时退到最近词题，保证总能出题
            val q = engine.generateQuiz(QuizPairs.pairs, real, level)
                ?: engine.generateQuiz(QuizPairs.pairs, QuizKind.CLOSEST, level)
            withContext(Dispatchers.Main) {
                question = q
                picked = null   // 关键：新题必须清空上一题的选中态，否则会自动亮出答案
            }
        }
    }

    LaunchedEffect(kind, level) { launchNext(kind) }

    Column(Modifier.fillMaxSize()) {
        ToolTopBar(title = "类比测验") { nav.popBackStack() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("词与词的关系，试着推理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("四种题型，看穿语义的方向", color = InkSoft, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("答对 $score / $total", color = InkSoft, fontSize = 13.sp)
                    Text("连对 $streak", color = Cinnabar, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 题型选择
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuizKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { if (kind != k) kind = k },
                        label = { Text(k.label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cinnabar,
                            selectedLabelColor = Paper,
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 难度分档：控制答案与干扰项的语义距离
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("难度", color = InkFaint, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp))
                QuizLevel.entries.forEach { l ->
                    FilterChip(
                        selected = level == l,
                        onClick = { if (level != l) level = l },
                        label = { Text(l.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Indigo,
                            selectedLabelColor = Paper,
                        )
                    )
                }
                Text(
                    level.sub, color = InkFaint, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
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
            // 题目（多行展示，行距独立控制，绝不重叠）
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    q.promptLines.forEach { line ->
                        Text(
                            line,
                            fontSize = 29.sp, fontWeight = FontWeight.Bold,
                            lineHeight = 44.sp,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                        )
                    }
                }
                // 运算思路提示（不剧透答案）
                Text(
                    q.hint,
                    color = InkSoft, fontSize = 13.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 22.dp)
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
                                    store.addHistory("测验", "${q.kind.label} · ${q.answer}", q.explain)
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
                        "答案「${q.answer}」\n${q.explain}",
                        color = InkSoft, fontSize = 13.sp, lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CinnabarButton("下一题", onClick = { launchNext(kind) })
                    InkButton("去算一道", onClick = { nav.navigate(com.cijian.app.ui.nav.Routes.tool("calc")) })
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    }
}
