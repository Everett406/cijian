package com.cijian.app.ui.screens.calc

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.cijian.app.core.Scored
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.EmptyHint
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Term(val word: String, val plus: Boolean)

@Composable
fun CalcScreen(nav: NavHostController) {
    val app = androidx.compose.ui.platform.LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store

    var terms by remember { mutableStateOf(listOf<Term>()) }
    var input by remember { mutableStateOf("") }
    var modePlus by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf(listOf<Scored>()) }
    var expr by remember { mutableStateOf("") }
    var errorHint by remember { mutableStateOf("") }

    fun buildExpr(): String =
        terms.joinToString(" ") { ((if (it.plus) "＋ " else "－ ") + it.word) }
            .replaceFirst("＋ ", "").replaceFirst("－ ", "")

    fun addTerm() {
        val w = input.trim()
        if (w.isEmpty()) return
        if (!engine.contains(w)) { errorHint = "「$w」不在词表中"; return }
        errorHint = ""
        terms = terms + Term(w, modePlus)
        input = ""
    }

    // 计算走协程
    var pending by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun launchCompute() {
        val plus = terms.filter { it.plus }.map { it.word }
        val minus = terms.filter { !it.plus }.map { it.word }
        if (plus.isEmpty() && minus.isEmpty()) return
        pending = true
        expr = buildExpr()
        scope.launch(Dispatchers.Default) {
            val r = engine.calc(plus, minus, 10)
            results = r
            pending = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ToolTopBar(title = "词语计算器") { nav.popBackStack() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("向量可以做算术：语义的方向，加减出来", color = InkSoft, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        // 输入行
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeButton("＋ 加", modePlus) { modePlus = true }
                Spacer(Modifier.width(8.dp))
                ModeButton("－ 减", !modePlus) { modePlus = false }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入一个词", color = InkFaint, fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = InkFaint,
                    ),
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Add, "添加",
                            tint = Cinnabar,
                            modifier = Modifier.size(26.dp).clickable { addTerm() }
                        )
                    },
                )
            }
            if (errorHint.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(errorHint, color = Cinnabar, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))

            // 项列表
            if (terms.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    terms.forEachIndexed { idx, t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (t.plus) Indigo else Cinnabar),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (t.plus) "＋" else "－", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            WordChip(t.word, onClick = { }) // 点击无动作，展示用
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Outlined.Close, "移除",
                                tint = InkSoft,
                                modifier = Modifier.size(20.dp).clickable {
                                    terms = terms.filterIndexed { i, _ -> i != idx }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CinnabarButton("启算", enabled = terms.isNotEmpty(), onClick = { launchCompute() })
                InkButton("清空", enabled = terms.isNotEmpty(), onClick = {
                    terms = emptyList(); results = emptyList(); expr = ""
                })
            }
            Spacer(Modifier.height(14.dp))
        }

        // 经典预设
        item {
            SectionHeader("经典题式")
            Spacer(Modifier.height(6.dp))
            val presets = listOf(
                listOf(Term("国王", true), Term("男人", false), Term("女人", true)),
                listOf(Term("巴黎", true), Term("法国", false), Term("意大利", true)),
                listOf(Term("北京", true), Term("中国", false), Term("日本", true)),
                listOf(Term("爸爸", true), Term("男人", false), Term("女人", true)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    InkButton(
                        text = p.joinToString(" ") { ((if (it.plus) "＋ " else "－ ") + it.word) }
                            .replaceFirst("＋ ", "").replaceFirst("－ ", "") + " ＝ ?",
                        onClick = { terms = p; }
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // 结果
        if (expr.isNotEmpty()) {
            item {
                SectionHeader("结果", trailing = {
                    if (results.isNotEmpty()) {
                        Text(
                            "收藏本次运算",
                            color = Cinnabar, fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                if (results.isNotEmpty()) store.addCalc(expr, results.first().word, results.first().score)
                            }
                        )
                    }
                })
                Text(
                    "$expr ≈ " + if (results.isNotEmpty()) results.first().word else "…",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp
                )
                Spacer(Modifier.height(12.dp))
            }
            if (pending) {
                item { Text("运算中…", color = InkSoft, fontSize = 13.sp) }
            } else if (results.isEmpty()) {
                item { EmptyHint("没有足够的信息进行运算") }
            } else {
                items(results.size) { i ->
                    val r = results[i]
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            store.addHistory("计算", r.word, "出自「$expr」")
                            nav.navigate(Routes.word(r.word))
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            r.word, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(120.dp), maxLines = 1
                        )
                        Column(Modifier.weight(1f)) {
                            ScoreBar((r.score + 1f) / 2f)
                            Spacer(Modifier.height(4.dp))
                            Text("%.3f".format(r.score), color = InkSoft, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ModeButton(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) Cinnabar else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (active) Cinnabar else InkFaint, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (active) androidx.compose.ui.graphics.Color.White else InkSoft, fontSize = 14.sp)
    }
}
