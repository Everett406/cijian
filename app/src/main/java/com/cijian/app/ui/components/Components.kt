package com.cijian.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Ink
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper

/** 朱砂小印 */
@Composable
fun SealMark(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(Cinnabar),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "词",
            color = Paper,
            fontSize = (size.value * 0.52f).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (size.value * 0.55f).sp
        )
    }
}

/** 区块标题：朱砂小方 + 宋体标题 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Cinnabar))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        trailing?.invoke()
    }
}

/** 墨线词片 */
@Composable
fun WordChip(
    word: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    score: Float? = null,
    accent: Color = Ink,
    filled: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (filled) accent else Paper)
            .border(1.dp, if (filled) accent else InkFaint, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            word,
            color = if (filled) Paper else accent,
            fontSize = 16.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal
        )
        if (score != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                "%.2f".format(score),
                color = if (filled) Paper.copy(alpha = 0.8f) else InkSoft,
                fontSize = 11.sp
            )
        }
    }
}

/** 满幅墨色按钮 */
@Composable
fun CinnabarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) Cinnabar else InkFaint)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Paper, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** 描边墨按钮 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (enabled) Ink else InkFaint, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Ink else InkFaint, fontSize = 15.sp)
    }
}

/** 相似度细条 */
@Composable
fun ScoreBar(score: Float, modifier: Modifier = Modifier, color: Color = Cinnabar) {
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(InkFaint.copy(alpha = 0.35f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(score.coerceIn(0f, 1f))
                .height(3.dp)
                .background(color)
        )
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        color = InkFaint,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        lineHeight = 24.sp
    )
}

/** 墨色分割线（中段淡出感） */
@Composable
fun InkDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(InkFaint.copy(alpha = 0.4f)))
}
