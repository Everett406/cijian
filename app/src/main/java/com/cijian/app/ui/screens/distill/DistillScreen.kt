package com.cijian.app.ui.screens.distill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.cijian.app.core.DistillResult
import com.cijian.app.core.Segmenter
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.InkButton
import com.cijian.app.ui.components.ScoreBar
import com.cijian.app.ui.components.ToolTopBar
import com.cijian.app.ui.components.WordChip
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper
import com.cijian.app.ui.theme.PaperDeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 两字及以上词权重 1.0，单字 0.45（单字噪声大，只做轻微牵引） */
private fun weightOf(w: String): Float = if (w.length >= 2) 1f else 0.45f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DistillScreen(nav: NavHostController) {
    val app = LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store
    val scope = rememberCoroutineScope()

    var text by androidx.compose.runtime.remember { mutableStateOf("") }
    var result by androidx.compose.runtime.remember { mutableStateOf<DistillResult?>(null) }
    var usedWords by androidx.compose.runtime.remember { mutableStateOf(listOf<String>()) }
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var note by androidx.compose.runtime.remember { mutableStateOf("") }

    fun distill() {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        val seg = app.segmenter ?: return
        busy = true; note = ""; result = null
        scope.launch(Dispatchers.Default) {
            val tokens = seg.segment(t)
            val weighted = tokens
                .filter { it.kind != Segmenter.Kind.PUNCT && it.inVocab }
                .map { it.text to weightOf(it.text) }
            val r = engine.distill(weighted)
            val used = weighted.filter { engine.contains(it.first) }.map { it.first }.distinct()
            withContext(Dispatchers.Main) {
                result = r
                usedWords = used
                busy = false
                if (r == null) {
                    note = if (used.isEmpty())
                        "这句话里没有词表内的词，换一句更完整的话试试"
                    else "没能凝出一个词，再多写几个字试试"
                } else {
                    store.addHistory(
                        "凝词",
                        t.take(14) + if (t.length > 14) "…" else "",
                        "凝成「${r.core}」"
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ToolTopBar(title = "一句话凝词") { nav.popBackStack() }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Text("把心事凝成一个词", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "写下一句话，我把它拆开、称重、取平均，凝出最指向的那个词",
                    color = InkSoft, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(14.dp))
            }
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如「深夜加班回家，一碗热汤面下肚」", color = InkFaint, fontSize = 14.sp) },
                    minLines = 2,
                    shape = RoundedCornerShape(4.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Cinnabar,
                        unfocusedIndicatorColor = InkFaint,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CinnabarButton(text = if (busy) "凝…" else "凝", onClick = { distill() })
                    Spacer(Modifier.width(10.dp))
                    if (usedWords.isNotEmpty()) {
                        Text(
                            "参与：${usedWords.joinToString(" · ")}",
                            color = InkFaint, fontSize = 11.sp, lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (note.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(note, color = InkSoft, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            val r = result
            if (r != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(Paper)
                            .border(1.dp, Cinnabar, RoundedCornerShape(6.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("这句话，凝成", color = InkSoft, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            r.core, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                            color = Cinnabar, lineHeight = 52.sp, maxLines = 1
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "与全句的余弦 %.3f".format(r.cos),
                            color = InkSoft, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        ScoreBar(r.cos.coerceIn(0f, 1f), color = Cinnabar)
                        Spacer(Modifier.height(14.dp))
                        Text("它周围的三位邻居", color = InkSoft, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            r.neighbors.forEach { n ->
                                WordChip(n.word, score = n.score, onClick = {
                                    nav.navigate(Routes.word(n.word))
                                })
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InkButton(text = "星图定位", onClick = {
                                nav.navigate(Routes.starmap(r.core))
                            })
                            InkButton(text = "看详情", onClick = {
                                nav.navigate(Routes.word(r.core))
                            })
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "小知识：词向量空间里，一句话的平均向量落在哪，哪就是这句话的重心。句子越具体，凝出的词往往越贴切。",
                        color = InkFaint, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            } else {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(PaperDeep)
                            .padding(20.dp)
                    ) {
                        Text("举几个例子", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "「高考前最后一个夏天的晚自习」\n「雨停之后，空气里有青草的味道」\n「爷爷坐在门口，摇着蒲扇讲从前」",
                            color = InkSoft, fontSize = 13.sp, lineHeight = 24.sp
                        )
                    }
                }
            }
        }
    }
}
