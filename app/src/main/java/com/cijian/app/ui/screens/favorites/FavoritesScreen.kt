package com.cijian.app.ui.screens.favorites

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.ui.components.EmptyHint
import com.cijian.app.ui.components.InkDivider
import com.cijian.app.ui.components.WordChip
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
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

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("收藏夹", fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("收藏", fontSize = 15.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("历史足迹", fontSize = 15.sp) })
            }
        }

        if (tab == 0) {
            val fav = store.favorites.value
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
                item { SectionTitle("收藏的词（${fav.words.size}）") }
                if (fav.words.isEmpty()) item { EmptyHint("在词详情页点右上角的星，把它收进来") }
                item {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fav.words.forEach { w ->
                            WordChip(w) { nav.navigate(Routes.word(w)) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                item { SectionTitle("收藏的运算（${fav.calcs.size}）") }
                if (fav.calcs.isEmpty()) item { EmptyHint("词语计算器的结果可以在这里留存") }
                fav.calcs.forEach { item0 ->
                    item {
                        Column(Modifier.fillMaxWidth().clickable {
                            nav.navigate(Routes.tools("calc"))
                        }.padding(vertical = 10.dp)) {
                            Text(item0.expr, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "≈ ${item0.result} · %.3f".format(item0.score),
                                color = Cinnabar, fontSize = 13.sp
                            )
                            Text(fmtTime(item0.time), color = InkSoft, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            InkDivider()
                        }
                    }
                }

                item { SectionTitle("收藏的路（${fav.paths.size}）") }
                if (fav.paths.isEmpty()) item { EmptyHint("词义之路的行程可以在这里重温") }
                fav.paths.forEach { p ->
                    item {
                        Column(Modifier.fillMaxWidth().clickable {
                            nav.navigate(Routes.tools("path", from = p.from, to = p.to))
                        }.padding(vertical = 10.dp)) {
                            Text("${p.from} → ${p.to}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "途经：" + (p.stops.drop(1).dropLast(1).take(8).joinToString("、")
                                    .ifEmpty { "（直达）" }),
                                color = InkSoft, fontSize = 13.sp, lineHeight = 19.sp
                            )
                            Text(fmtTime(p.time), color = InkSoft, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            InkDivider()
                        }
                    }
                }
            }
        } else {
            val hist = store.history.value
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
                                Text(h.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(fmtTime(h.time), color = InkSoft, fontSize = 11.sp)
                            }
                            if (h.detail.isNotEmpty()) {
                                Text(h.detail, color = InkSoft, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            InkDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, color = InkSoft, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

private fun fmtTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts))
