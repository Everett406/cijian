package com.cijian.app.ui.screens.word

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.InkSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WordDetailScreen(nav: NavHostController, word: String) {
    val app = androidx.compose.ui.platform.LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store

    var neighbors by remember { mutableStateOf(listOf<Scored>()) }
    LaunchedEffect(word) {
        neighbors = withContext(Dispatchers.Default) { engine.nearest(word, 24) }
    }
    // 收藏态订阅：状态提升到 Composable 顶层，星标点击后立即刷新
    val favWords by store.favorites.collectAsState()
    val isFav = word in favWords.words

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(word, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (neighbors.isNotEmpty()) "最近的语义邻居" else "词表之外的字词",
                        color = InkSoft, fontSize = 13.sp
                    )
                }
                Icon(
                    if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "收藏",
                    tint = Cinnabar,
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { store.toggleWord(word) }
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CinnabarButton("词义之路", onClick = {
                    nav.navigate(Routes.tool("path", from = word))
                }, modifier = Modifier.weight(1f))
                InkButton("星图定位", onClick = {
                    nav.navigate(Routes.starmap(word))
                }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
        }

        if (neighbors.isEmpty()) {
            item { EmptyHint("该词不在腾讯 14 万词表中。\n可以试试它附近的常用词，或在分词工坊里看看别的词。") }
        } else {
            item { SectionHeader("语义邻居 · 余弦相似度") }
            items(neighbors.size) { i ->
                val n = neighbors[i]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            app.store.addHistory("词", n.word, "自「$word」的邻居")
                            nav.navigate(Routes.word(n.word))
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        n.word,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(120.dp),
                        maxLines = 1
                    )
                    Column(Modifier.weight(1f)) {
                        ScoreBar(((n.score + 1f) / 2f))
                        Spacer(Modifier.height(4.dp))
                        Text("%.3f".format(n.score), color = InkSoft, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
