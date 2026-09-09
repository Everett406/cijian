package com.cijian.app.ui.screens.tools

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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper

private data class ToolEntry(val feature: String, val title: String, val sub: String, val icon: ImageVector)

@Composable
fun ToolsHubScreen(nav: NavHostController) {
    val tools = listOf(
        ToolEntry("path", "词义之路", "从一词走至另一词", Icons.Outlined.Route),
        ToolEntry("calc", "词语计算器", "国王 - 男人 + 女人 ≈ ?", Icons.Outlined.Calculate),
        ToolEntry("distill", "一句话凝词", "心事凝成一个词", Icons.Outlined.AutoAwesome),
        ToolEntry("quiz", "类比测验", "四种题型·三档难度", Icons.Outlined.Quiz),
        ToolEntry("guess", "语义猜词", "凭相关度猜隐藏词", Icons.Outlined.Psychology),
        ToolEntry("bridge", "词桥", "十步搭起一座桥", Icons.Outlined.SwapHoriz),
        ToolEntry("blitz", "词海消消", "限时连消相似词对", Icons.Outlined.GridOn),
        ToolEntry("segment", "分词工坊", "一句话拆成词", Icons.Outlined.TextFields),
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("词途", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Spacer(Modifier.height(4.dp))
            Text("八般玩法，皆在于此 · 每一种都是语义空间的一次漫游", color = InkSoft, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }
        items(tools.size) { i ->
            val t = tools[i]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, InkFaint, RoundedCornerShape(6.dp))
                    .clickable { nav.navigate(Routes.tool(t.feature)) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(5.dp)).background(Paper),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        t.icon, contentDescription = t.title,
                        tint = if (i % 2 == 0) Cinnabar else Indigo,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(t.sub, color = InkSoft, fontSize = 12.sp, maxLines = 1)
                }
                Text("›", color = InkFaint, fontSize = 24.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        item {
            Spacer(Modifier.height(6.dp))
            Text(
                "提示：在任意词的详情页，也能直接「星图定位」或「从这出发」。",
                color = InkFaint, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
}
