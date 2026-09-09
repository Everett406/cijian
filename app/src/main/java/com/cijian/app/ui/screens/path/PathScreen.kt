package com.cijian.app.ui.screens.path

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.core.PathStop
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.EmptyHint
import com.cijian.app.ui.components.InkButton
import com.cijian.app.ui.components.ScoreBar
import com.cijian.app.ui.components.SectionHeader
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.Ink
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@Composable
fun PathScreen(nav: NavHostController, initFrom: String, initTo: String) {
    val app = LocalContext.current.ciJian
    val engine = app.engine
    val store = app.store

    var from by remember { mutableStateOf(initFrom) }
    var to by remember { mutableStateOf(initTo) }
    var steps by remember { mutableFloatStateOf(8f) }
    var stops by remember { mutableStateOf(listOf<PathStop>()) }
    var searched by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    fun launchJourney() {
        val f = from.trim(); val t = to.trim()
        if (f.isEmpty() || t.isEmpty()) { hint = "先填好起点和终点"; return }
        if (!engine.contains(f)) { hint = "「$f」不在词表中"; return }
        if (!engine.contains(t)) { hint = "「$t」不在词表中"; return }
        if (f == t) { hint = "同一个词之间，无需行走"; return }
        hint = ""; busy = true; searched = true
        scope.launch(Dispatchers.Default) {
            val r = engine.path(f, t, steps.toInt())
            withContext(Dispatchers.Main) {
                stops = r
                busy = false
                store.addHistory("路径", "$f → $t", "${r.size} 个途经词")
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Text("词义之路", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("在语义空间里，从「爱情」走到「战争」会路过什么？", color = InkSoft, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = from, onValueChange = { from = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("起点，如 爱情", color = InkFaint, fontSize = 14.sp) },
                    singleLine = true, shape = RoundedCornerShape(4.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Cinnabar, unfocusedIndicatorColor = InkFaint,
                    ),
                )
                Text("→", color = InkSoft, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = to, onValueChange = { to = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("终点，如 战争", color = InkFaint, fontSize = 14.sp) },
                    singleLine = true, shape = RoundedCornerShape(4.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Indigo, unfocusedIndicatorColor = InkFaint,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("步数 ${steps.toInt()}", color = InkSoft, fontSize = 13.sp)
                Slider(
                    value = steps, onValueChange = { steps = it },
                    valueRange = 4f..14f, steps = 9,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = SliderDefaults.colors(thumbColor = Cinnabar, activeTrackColor = Cinnabar),
                )
            }
            if (hint.isNotEmpty()) {
                Text(hint, color = Cinnabar, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CinnabarButton("启程", enabled = !busy, onClick = { launchJourney() })
                InkButton("示例：爱情 → 战争", enabled = !busy, onClick = {
                    from = "爱情"; to = "战争"; searched = true
                    launchJourney()
                })
            }
            Spacer(Modifier.height(18.dp))
        }

        if (searched && stops.isEmpty() && !busy) {
            item { EmptyHint("这条路没有找到途经词。\n两个词离得太远时，试试多走几步。") }
        }

        if (stops.isNotEmpty()) {
            val words = listOf(from.trim()) + stops.map { it.word } + listOf(to.trim())
            item {
                SectionHeader("行程图谱", trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("收藏此路", color = Cinnabar, fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                store.addPath(from.trim(), to.trim(), words)
                            })
                        Text("生成分享卡", color = Indigo, fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                ShareCard.share(nav.context, from.trim(), to.trim(), words)
                            })
                    }
                })
                ForceGraph(words = words, onNodeTap = { w ->
                    store.addHistory("词", w, "途经词")
                    nav.navigate(Routes.word(w))
                })
                Spacer(Modifier.height(18.dp))
            }

            item { SectionHeader("逐站停留") }
            item {
                Column {
                    StopRow(0, from.trim(), 1f, "起点") {}
                    stops.forEachIndexed { i, s ->
                        StopRow(i + 1, s.word, (s.cosToTarget + 1f) / 2f, "%.3f".format(s.cosToTarget)) {
                            store.addHistory("词", s.word, "途经词")
                            nav.navigate(Routes.word(s.word))
                        }
                    }
                    StopRow(stops.size + 1, to.trim(), 1f, "终点") {}
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StopRow(index: Int, word: String, progress: Float, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(12.dp))
                .background(if (tag == "起点" || tag == "终点") Cinnabar else Paper)
                .border(1.dp, Cinnabar, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = if (tag == "起点" || tag == "终点") Paper else Cinnabar, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(word, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            ScoreBar(progress, color = if (tag == "终点") Cinnabar else Indigo)
            Spacer(Modifier.height(3.dp))
            Text(tag, color = InkSoft, fontSize = 11.sp)
        }
    }
}

// ─────────────────────────── 力导向图 ───────────────────────────

private class FNode(var x: Float, var y: Float) {
    var vx = 0f; var vy = 0f; var pinned = false
}

private class PathSim(n: Int, w: Float, h: Float) {
    val nodes = Array(n) {
        val ang = Math.PI * 2 * it / n
        FNode((w / 2 + 0.38 * w * kotlin.math.cos(ang)).toFloat(),
              (h / 2 + 0.38 * h * kotlin.math.sin(ang)).toFloat())
    }
    fun step(width: Float, height: Float) {
        val ns = nodes; val count = ns.size
        if (count < 2) return
        // 斥力
        for (i in 0 until count) for (j in i + 1 until count) {
            val a = ns[i]; val b = ns[j]
            var dx = a.x - b.x; var dy = a.y - b.y
            var d2 = dx * dx + dy * dy
            if (d2 < 1f) { d2 = 1f; dx = (i - j) * 7f; dy = (j - i) * 7f }
            val d = kotlin.math.sqrt(d2)
            var f = 260000f / d2
            if (f > 26f) f = 26f
            val fx = f * dx / d; val fy = f * dy / d
            a.vx += fx; a.vy += fy
            b.vx -= fx; b.vy -= fy
        }
        // 弹簧（链式）
        for (i in 0 until count - 1) {
            val a = ns[i]; val b = ns[i + 1]
            val dx = b.x - a.x; val dy = b.y - a.y
            val d = max(kotlin.math.sqrt(dx * dx + dy * dy), 1f)
            val f = 0.045f * (d - min(150f, width / (count + 1.6f)))
            val fx = f * dx / d; val fy = f * dy / d
            a.vx += fx; a.vy += fy
            b.vx -= fx; b.vy -= fy
        }
        // 中心引力 + 阻尼
        val cx = width / 2; val cy = height / 2
        for (nd in ns) {
            nd.vx += (cx - nd.x) * 0.012f
            nd.vy += (cy - nd.y) * 0.012f
            nd.vx *= 0.80f; nd.vy *= 0.80f
            val sp = kotlin.math.sqrt(nd.vx * nd.vx + nd.vy * nd.vy)
            if (sp > 18f) { nd.vx *= 18f / sp; nd.vy *= 18f / sp }
            if (!nd.pinned) { nd.x += nd.vx; nd.y += nd.vy }
            // 边界
            nd.x = nd.x.coerceIn(50f, width - 50f)
            nd.y = nd.y.coerceIn(56f, height - 66f)
        }
    }
}

@Composable
fun ForceGraph(words: List<String>, onNodeTap: (String) -> Unit) {
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }
    val sim = remember(words) { PathSim(words.size, 100f, 100f) }
    var frame by remember(words) { mutableIntStateOf(0) }
    var dragIdx by remember(words) { mutableIntStateOf(-1) }

    // 文字画笔
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2A2722")
            textSize = 34f; textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.SERIF
        }
    }
    val selPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#B23A2E")
            textSize = 34f; textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        }
    }

    LaunchedEffect(words) {
        while (true) {
            androidx.compose.runtime.withFrameNanos {
                if (width > 0f) sim.step(width, height)
                frame++
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .pointerInput(words) {
                detectTapGestures(onTap = { p ->
                    var best = -1; var bd = Float.MAX_VALUE
                    sim.nodes.forEachIndexed { i, n ->
                        val d = (n.x - p.x) * (n.x - p.x) + (n.y - p.y) * (n.y - p.y)
                        if (d < bd) { bd = d; best = i }
                    }
                    if (best >= 0 && bd < 60f * 60f) onNodeTap(words[best])
                })
            }
            .pointerInput(words) {
                detectDragGestures(
                    onDragStart = { p ->
                        var best = -1; var bd = Float.MAX_VALUE
                        sim.nodes.forEachIndexed { i, n ->
                            val d = (n.x - p.x) * (n.x - p.x) + (n.y - p.y) * (n.y - p.y)
                            if (d < bd) { bd = d; best = i }
                        }
                        dragIdx = if (bd < 64f * 64f) best else -1
                        if (dragIdx >= 0) sim.nodes[dragIdx].pinned = true
                    },
                    onDrag = { change, amt ->
                        change.consume()
                        if (dragIdx >= 0) {
                            sim.nodes[dragIdx].x += amt.x
                            sim.nodes[dragIdx].y += amt.y
                        }
                    },
                    onDragEnd = { if (dragIdx >= 0) sim.nodes[dragIdx].pinned = false },
                    onDragCancel = { if (dragIdx >= 0) sim.nodes[dragIdx].pinned = false },
                )
            }
    ) {
        width = size.width; height = size.height
        frame // 读取状态以触发重绘

        val n = words.size
        // 边：起点朱砂 → 终点黛青 渐变
        for (i in 0 until n - 1) {
            val a = sim.nodes[i]; val b = sim.nodes[i + 1]
            val t = i.toFloat() / (n - 1).coerceAtLeast(1)
            val c = lerpColor(Cinnabar, Indigo, t)
            drawLine(c, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 5f, cap = StrokeCap.Round)
        }
        // 节点
        for (i in 0 until n) {
            val nd = sim.nodes[i]
            val center = Offset(nd.x, nd.y)
            val isEnd = i == 0 || i == n - 1
            if (isEnd) {
                drawCircle(if (i == 0) Cinnabar else Ink, radius = 26f, center = center)
                drawCircle(Paper, radius = 26f, center = center, style = Stroke(width = 3f))
            } else {
                drawCircle(Paper, radius = 19f, center = center)
                drawCircle(lerpColor(Cinnabar, Indigo, i.toFloat() / (n - 1).coerceAtLeast(1)), radius = 19f, center = center, style = Stroke(width = 3.5f))
            }
            // 标签
            drawIntoCanvasText(words[i], nd.x, nd.y - (if (isEnd) 42f else 34f), if (isEnd) selPaint else labelPaint)
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoCanvasText(text: String, x: Float, y: Float, paint: Paint) {
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
