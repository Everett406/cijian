package com.cijian.app.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.ui.components.EmptyHint
import com.cijian.app.ui.components.InkDivider
import com.cijian.app.ui.components.WordChip
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen(nav: NavHostController) {
    val app = LocalContext.current.ciJian
    val store = app.store
    var tab by remember { mutableIntStateOf(0) }
    var managing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val fav by store.favorites.collectAsState()
    val favCount = fav.words.size + fav.calcs.size + fav.paths.size
    val hasFav = favCount > 0

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("收藏夹", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (tab == 0 && hasFav) {
                    Text(
                        if (managing) "完成" else "管理",
                        color = Cinnabar,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { managing = !managing }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TabRow(
                selectedTabIndex = tab,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[tab]),
                        color = Cinnabar
                    )
                }
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0; managing = false },
                    text = { Text("收藏", fontSize = 15.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1; managing = false },
                    text = { Text("历史足迹", fontSize = 15.sp) })
            }
        }

        if (tab == 0) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                item { SectionTitle("收藏的词（${fav.words.size}）") }
                if (fav.words.isEmpty()) item { EmptyHint("在词详情页点右上角的星，把它收进来") }
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fav.words.forEach { w ->
                            if (managing) {
                                RemovableChip(w, onRemove = { store.toggleWord(w) })
                            } else {
                                WordChip(w, onClick = { nav.navigate(Routes.word(w)) })
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                item { SectionTitle("收藏的运算（${fav.calcs.size}）") }
                if (fav.calcs.isEmpty()) item { EmptyHint("词语计算器的结果可以在这里留存") }
                fav.calcs.forEach { item0 ->
                    item {
                        FavRow(
                            managing = managing,
                            onClick = { nav.navigate(Routes.tool("calc")) },
                            onDelete = { store.removeCalc(item0) }
                        ) {
                            Text(item0.expr, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "≈ ${item0.result} · %.3f".format(item0.score),
                                color = Cinnabar, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(fmtTime(item0.time), color = InkSoft, fontSize = 11.sp)
                        }
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }
                item { SectionTitle("收藏的路（${fav.paths.size}）") }
                if (fav.paths.isEmpty()) item { EmptyHint("词义之路的行程可以在这里重温") }
                fav.paths.forEach { p ->
                    item {
                        FavRow(
                            managing = managing,
                            onClick = { nav.navigate(Routes.tool("path", from = p.from, to = p.to)) },
                            onDelete = { store.removePath(p) }
                        ) {
                            Text("${p.from} → ${p.to}", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "途经：" + (p.stops.drop(1).dropLast(1).take(8).joinToString("、")
                                    .ifEmpty { "（直达）" }),
                                color = InkSoft, fontSize = 13.sp, lineHeight = 19.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(fmtTime(p.time), color = InkSoft, fontSize = 11.sp)
                        }
                    }
                }

                if (managing && hasFav) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text(
                                "清空全部收藏（$favCount）",
                                color = Cinnabar,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, Cinnabar, RoundedCornerShape(4.dp))
                                    .clickable { confirmClear = true }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        } else {
            val hist by store.history.collectAsState()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "清空足迹",
                            color = Cinnabar, fontSize = 13.sp,
                            modifier = Modifier.clickable { store.clearHistory() }
                        )
                    }
                }
                if (hist.isEmpty()) item { EmptyHint("走过的路，会留在这里") }
                hist.forEach { h ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    h.type,
                                    color = Cinnabar, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(44.dp)
                                )
                                Text(h.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(fmtTime(h.time), color = InkSoft, fontSize = 11.sp)
                            }
                            if (h.detail.isNotEmpty()) {
                                Text(h.detail, color = InkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(4.dp))
                            InkDivider()
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部收藏？") },
            text = { Text("收藏的 $favCount 条内容（词、运算与走过的路）将一并清空，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    store.clearFavorites()
                    managing = false
                    confirmClear = false
                }) { Text("清空", color = Cinnabar, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

/** 收藏条目：正常态点击跳转；管理模式下改为删除态（右侧朱砂「删除」，禁用跳转） */
@Composable
private fun FavRow(
    managing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !managing, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            content()
            Spacer(Modifier.height(4.dp))
            InkDivider()
        }
        if (managing) {
            Text(
                "删除",
                color = Cinnabar, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

/** 管理模式下的词片：带朱砂 ×，点击 × 即移除 */
@Composable
private fun RemovableChip(word: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Cinnabar, RoundedCornerShape(4.dp))
            .padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(word, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Outlined.Close, contentDescription = "移除 $word",
            tint = Cinnabar,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onRemove)
                .padding(3.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, color = InkSoft, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

private fun fmtTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts))
