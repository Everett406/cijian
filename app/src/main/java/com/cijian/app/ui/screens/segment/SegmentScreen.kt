package com.cijian.app.ui.screens.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.core.Segmenter
import com.cijian.app.ui.components.InkButton
import com.cijian.app.ui.components.SectionHeader
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentScreen(nav: NavHostController) {
    val app = LocalContext.current.ciJian
    val segmenter = app.segmenter

    var text by remember {
        mutableStateOf("人是万物的尺度，也是万物的量尺。语言是存在之家。")
    }

    val tokens = remember(text, segmenter) {
        segmenter?.segment(text) ?: emptyList()
    }
    val inVocab = tokens.count { it.kind != Segmenter.Kind.PUNCT && it.inVocab }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("分词工坊", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("词典即词向量词表：切出来的每个词，都可以直接拿去玩", color = InkSoft, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("粘贴或输入一段话…", color = InkFaint) },
                shape = RoundedCornerShape(4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = InkFaint,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                InkButton("示例", onClick = {
                    text = "国王披上星光的斗篷，从旧日的战场走回春天的花园，爱情与战争在此相遇。"
                })
                Text("共 ${tokens.size} 片 · $inVocab 个词在词表中", color = InkSoft, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
        item { SectionHeader("切分结果") }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tokens.forEach { tk ->
                    val isPunct = tk.kind == Segmenter.Kind.PUNCT
                    val playable = tk.kind != Segmenter.Kind.PUNCT && tk.inVocab
                    val bg = when {
                        isPunct -> Color.Transparent
                        playable -> MaterialTheme.colorScheme.surface
                        else -> Color.Transparent
                    }
                    val border = when {
                        isPunct -> Color.Transparent
                        playable -> Indigo
                        else -> InkFaint
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(bg)
                            .border(if (isPunct) 0.dp else 1.dp, border, RoundedCornerShape(4.dp))
                            .then(if (playable) Modifier.clickable {
                                app.store.addHistory("词", tk.text, "分词")
                                nav.navigate(Routes.word(tk.text))
                            } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            tk.text,
                            fontSize = 16.sp,
                            color = when {
                                isPunct -> InkSoft
                                playable -> MaterialTheme.colorScheme.primary
                                else -> InkFaint
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "说明：墨青框为词表中的词（点按即入词详情）；灰框为词表外字词；标点自动剔除。" +
                        "切分采用双向最大匹配，词典直接复用 143,613 词的向量词表。",
                color = InkSoft, fontSize = 12.sp, lineHeight = 19.sp
            )
        }
    }
}
