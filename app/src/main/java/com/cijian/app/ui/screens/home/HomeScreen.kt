package com.cijian.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.EmptyHint
import com.cijian.app.ui.components.InkDivider
import com.cijian.app.ui.components.SectionHeader
import com.cijian.app.ui.components.SealMark
import com.cijian.app.ui.components.WordChip
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.PaperDeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class FeatureCard(val title: String, val sub: String, val icon: ImageVector, val route: String)

@Composable
fun HomeScreen(nav: NavHostController) {
    val app = androidx.compose.ui.platform.LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store

    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(query) {
        if (query.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        suggestions = withContext(Dispatchers.Default) { engine.prefixSearch(query.trim(), 10) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
    ) {
        // ── 头部 ──
        item {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SealMark(40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("词间", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                        Text(
                            "词与词之间，有路可走 · ${engine.size} 词 × ${engine.dim} 维",
                            color = InkSoft, fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // ── 搜索 ──
        item {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("寻一个词，如「时间」「孤独」…", color = InkFaint, fontSize = 15.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = InkFaint,
                    ),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.take(5).forEach { w ->
                            WordChip(w, filled = false) {
                                query = ""
                                store.addHistory("词", w, "搜索")
                                nav.navigate(Routes.word(w))
                            }
                        }
                    }
                    if (suggestions.size > 5) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.drop(5).forEach { w ->
                                WordChip(w, filled = false) {
                                    query = ""
                                    store.addHistory("词", w, "搜索")
                                    nav.navigate(Routes.word(w))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CinnabarButton(
                        text = "手气不错 · 随机一词",
                        onClick = {
                            val w = engine.randomWord()
                            store.addHistory("词", w, "随机探索")
                            nav.navigate(Routes.word(w))
                        }
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // ── 功能入口 ──
        item {
            SectionHeader("四般玩法")
            Spacer(Modifier.height(4.dp))
        }
        item {
            val cards = listOf(
                FeatureCard("词义之路", "从一个词，走到另一个词", Icons.Outlined.Route, Routes.tools("path")),
                FeatureCard("词语计算器", "国王 − 男人 + 女人 ≈ ?", Icons.Outlined.Calculate, Routes.tools("calc")),
                FeatureCard("类比测验", "猜猜语义空间填什么", Icons.Outlined.Quiz, Routes.tools("quiz")),
                FeatureCard("分词工坊", "一句话，拆成可玩的词", Icons.Outlined.TextFields, Routes.tools("segment")),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.chunked(2).forEach { rowCards ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in rowCards) {
                            FeatureCardView(c.title, c.sub, c.icon) { nav.navigate(c.route) }
                        }
                        if (rowCards.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── 最近足迹 ──
        item { SectionHeader("最近足迹") }
        val history = store.history.value.take(12)
        if (history.isEmpty()) {
            item { EmptyHint("走过的路，会留在这里") }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.take(6).forEach { h ->
                        WordChip(h.title, filled = false) {
                            if (h.type == "词") nav.navigate(Routes.word(h.title))
                            else nav.navigate(Routes.tools("calc"))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (history.size > 6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.drop(6).forEach { h ->
                            WordChip(h.title, filled = false) {
                                if (h.type == "词") nav.navigate(Routes.word(h.title))
                                else nav.navigate(Routes.tools("calc"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCardView(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, InkFaint, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = title, tint = com.cijian.app.ui.theme.Cinnabar, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(3.dp))
        Text(sub, color = InkSoft, fontSize = 11.sp, lineHeight = 15.sp)
    }
}
