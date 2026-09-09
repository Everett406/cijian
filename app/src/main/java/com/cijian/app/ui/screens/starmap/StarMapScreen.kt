package com.cijian.app.ui.screens.starmap

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.WordChip
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.Ink
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper

/** 星图视图状态（手势闭包持有此对象，避免捕获过期值） */
private class MapView {
    var scale by mutableStateOf(1f)
    var offX by mutableStateOf(0f)
    var offY by mutableStateOf(0f)
    var yaw by mutableStateOf(0.6f)
    var pitch by mutableStateOf(0.35f)
    var dist by mutableStateOf(3.2f)
}

@Composable
fun StarMapScreen(nav: NavHostController, focus: String) {
    val app = LocalContext.current.ciJian
    val engine = app.engine
    val view = remember { MapView() }

    var is3d by remember { mutableStateOf(false) }
    var smooth by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var hiIdx by remember { mutableStateOf(-1) }
    var hiNeighbors by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableStateOf(-1) }

    fun focusOn(word: String) {
        val i = engine.indexOf(word) ?: return
        hiIdx = i
        selected = -1
        val v = engine.vector(word)!!
        val nb = engine.nearestTo(v, 10, setOf(word))
        hiNeighbors = nb.mapNotNull { engine.indexOf(it.word) }.toSet()
        engine.pca2?.let { p ->
            view.offX = -p[i * 2] * view.scale
            view.offY = -p[i * 2 + 1] * view.scale
        }
    }

    LaunchedEffect(focus) { if (focus.isNotBlank()) focusOn(focus) }
    LaunchedEffect(is3d) {
        if (is3d && engine.pca3 == null) engine.ensurePcaAsync(app, app.appScope)
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        // ── 顶栏 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("语义星图", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${engine.size} 词 · PCA 投影 · 拖动漫游，点星查看",
                    color = InkSoft, fontSize = 12.sp
                )
            }
            FilterChip(
                selected = is3d,
                onClick = { is3d = !is3d },
                label = { Text(if (is3d) "3D" else "2D") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Cinnabar,
                    selectedLabelColor = Paper,
                )
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = smooth,
                onClick = { smooth = !smooth },
                label = { Text("流畅") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Indigo,
                    selectedLabelColor = Paper,
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("在星空中定位一个词…", color = InkFaint, fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Cinnabar,
                    unfocusedIndicatorColor = InkFaint,
                ),
            )
            Spacer(Modifier.width(10.dp))
            CinnabarButton("定位", onClick = {
                val w = query.trim()
                if (engine.contains(w)) focusOn(w)
            })
        }
        Spacer(Modifier.height(10.dp))

        // ── 星图 ──
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            val pca2 = engine.pca2
            val pca3 = engine.pca3
            if (pca2 == null || (is3d && pca3 == null)) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (engine.pcaComputing) {
                        CircularProgressIndicator(color = Cinnabar, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("正在为星辰排布坐标…", color = InkSoft, fontSize = 13.sp)
                    } else {
                        Text("暂无投影坐标", color = InkSoft, fontSize = 14.sp)
                    }
                }
            } else {
                StarCanvas(
                    pca = if (is3d) pca3!! else pca2,
                    dim = if (is3d) 3 else 2,
                    count = engine.size,
                    words = engine.words,
                    is3d = is3d,
                    smooth = smooth,
                    view = view,
                    hiIdx = hiIdx,
                    hiNeighbors = hiNeighbors,
                    selected = selected,
                    onTapWorld = { wx, wy ->
                        var best = -1; var bd = Float.MAX_VALUE
                        val th = 14f / view.scale
                        for (i in 0 until engine.size) {
                            val dx = pca2[i * 2] - wx; val dy = pca2[i * 2 + 1] - wy
                            val d = dx * dx + dy * dy
                            if (d < bd) { bd = d; best = i }
                        }
                        if (best >= 0 && bd < th * th) selected = best
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // ── 选中词信息卡 ──
                if (selected >= 0) {
                    val w = engine.words[selected]
                    val nb = remember(selected) { engine.nearest(w, 5) }
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Paper)
                            .border(1.dp, Ink, RoundedCornerShape(6.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("关闭 ✕", color = InkSoft, fontSize = 13.sp,
                                modifier = Modifier.clickable { selected = -1 })
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            nb.take(4).forEach { n ->
                                WordChip(n.word, score = n.score, onClick = {
                                    nav.navigate(Routes.word(n.word))
                                })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("看详情", color = Cinnabar, fontSize = 13.sp, modifier = Modifier.clickable {
                                nav.navigate(Routes.word(w))
                            })
                            Text("以此为中心", color = Indigo, fontSize = 13.sp, modifier = Modifier.clickable {
                                focusOn(w)
                            })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun StarCanvas(
    pca: FloatArray, dim: Int, count: Int, words: List<String>,
    is3d: Boolean, smooth: Boolean, view: MapView,
    hiIdx: Int, hiNeighbors: Set<Int>, selected: Int,
    onTapWorld: (Float, Float) -> Unit,
    modifier: Modifier,
) {
    val dotPaint = remember {
        Paint().apply { isAntiAlias = true; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
    }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2A2722")
            textSize = 26f; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.SERIF
        }
    }
    val hiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#B23A2E")
            textSize = 30f; textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        }
    }

    // 3D 投影缓冲（远/中/近三层）
    val projFar = remember(pca) { FloatArray(count * 2) }
    val projMid = remember(pca) { FloatArray(count * 2) }
    val projNear = remember(pca) { FloatArray(count * 2) }

    Canvas(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Paper)
            .border(1.dp, InkFaint, RoundedCornerShape(6.dp))
            .pointerInput(is3d) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (is3d) {
                        view.yaw += pan.x * 0.008f
                        view.pitch = (view.pitch + pan.y * 0.008f).coerceIn(-1.35f, 1.35f)
                        view.dist = (view.dist / zoom).coerceIn(1.6f, 8f)
                    } else {
                        view.scale = (view.scale * zoom).coerceIn(0.6f, 500f)
                        view.offX += pan.x
                        view.offY += pan.y
                    }
                }
            }
            .pointerInput(is3d) {
                detectTapGestures(onTap = { p ->
                    if (!is3d) {
                        val cx = this.size.width / 2f + view.offX
                        val cy = this.size.height / 2f + view.offY
                        onTapWorld((p.x - cx) / view.scale, (p.y - cy) / view.scale)
                    }
                })
            }
    ) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h / 2f
        val nc = drawContext.canvas.nativeCanvas

        if (!is3d) {
            // ── 2D 星图 ──
            nc.save()
            nc.translate(cx + view.offX, cy + view.offY)
            nc.scale(view.scale, view.scale)
            dotPaint.color = 0x662A2722
            dotPaint.strokeWidth = 2.4f / view.scale
            nc.drawPoints(pca, 0, count * 2, dotPaint)
            dotPaint.color = 0xCC35565A.toInt()
            dotPaint.strokeWidth = 5f / view.scale
            for (i in hiNeighbors) nc.drawPoint(pca[i * 2], pca[i * 2 + 1], dotPaint)
            if (hiIdx >= 0) {
                dotPaint.color = 0xFFB23A2E.toInt()
                dotPaint.strokeWidth = 9f / view.scale
                nc.drawPoint(pca[hiIdx * 2], pca[hiIdx * 2 + 1], dotPaint)
            }
            if (selected >= 0 && selected != hiIdx) {
                dotPaint.color = 0xFF9C7C46.toInt()
                dotPaint.strokeWidth = 7f / view.scale
                nc.drawPoint(pca[selected * 2], pca[selected * 2 + 1], dotPaint)
            }
            nc.restore()

            fun drawLabel(i: Int, paint: Paint, dy: Float) {
                val sx = pca[i * 2] * view.scale + cx + view.offX
                val sy = pca[i * 2 + 1] * view.scale + cy + view.offY
                nc.drawText(words[i], sx, sy - dy, paint)
            }
            if (hiIdx >= 0) drawLabel(hiIdx, hiPaint, 14f)
            for (i in hiNeighbors) drawLabel(i, labelPaint, 12f)
            if (selected >= 0 && selected != hiIdx && selected !in hiNeighbors) drawLabel(selected, labelPaint, 12f)

            if (hiIdx < 0 && selected < 0) {
                // 无高亮时标注离中心最近的 10 个词
                val top = FloatArray(10) { Float.MAX_VALUE }
                val topIdx = IntArray(10) { -1 }
                var i = 0
                while (i < count) {
                    val sx = pca[i * 2] * view.scale + cx + view.offX
                    val sy = pca[i * 2 + 1] * view.scale + cy + view.offY
                    if (sx > 40f && sx < w - 40f && sy > 40f && sy < h - 40f) {
                        val d = (sx - cx) * (sx - cx) + (sy - cy) * (sy - cy)
                        var j = 9
                        while (j >= 0 && top[j] > d) j--
                        if (j < 9) {
                            for (k in 9 downTo j + 2) { top[k] = top[k - 1]; topIdx[k] = topIdx[k - 1] }
                            top[j + 1] = d; topIdx[j + 1] = i
                        }
                    }
                    i += 7
                }
                for (j in 0 until 10) {
                    val i = topIdx[j]
                    if (i < 0) continue
                    val sx = pca[i * 2] * view.scale + cx + view.offX
                    val sy = pca[i * 2 + 1] * view.scale + cy + view.offY
                    nc.drawText(words[i], sx, sy - 10f, labelPaint)
                }
            }
        } else {
            // ── 3D 星图 ──
            val cyaw = cos(view.yaw); val syaw = sin(view.yaw)
            val cp = cos(view.pitch); val sp = sin(view.pitch)
            val f = kotlin.math.min(w, h) * 0.62f
            var cntFar = 0; var cntMid = 0; var cntNear = 0
            val stride = if (smooth) 1 else 2
            val dNear = view.dist * 0.75f
            val dMid = view.dist * 1.15f
            var i = 0
            while (i < count) {
                val x = pca[i * 3]; val y = pca[i * 3 + 1]; val z = pca[i * 3 + 2]
                val x1 = x * cyaw + z * syaw
                val z1 = -x * syaw + z * cyaw
                val y2 = y * cp - z1 * sp
                val z2 = y * sp + z1 * cp
                val depth = z2 + view.dist
                if (depth > 0.35f) {
                    val s = f / depth
                    val sx = x1 * s + cx
                    val syy = y2 * s + cy
                    if (sx > -60f && sx < w + 60f && syy > -60f && syy < h + 60f) {
                        if (depth > dMid) { projFar[cntFar * 2] = sx; projFar[cntFar * 2 + 1] = syy; cntFar++ }
                        else if (depth > dNear) { projMid[cntMid * 2] = sx; projMid[cntMid * 2 + 1] = syy; cntMid++ }
                        else { projNear[cntNear * 2] = sx; projNear[cntNear * 2 + 1] = syy; cntNear++ }
                    }
                }
                i += stride
            }
            dotPaint.color = 0x3D2A2722; dotPaint.strokeWidth = 2.6f
            nc.drawPoints(projFar, 0, cntFar * 2, dotPaint)
            dotPaint.color = 0x802A2722.toInt(); dotPaint.strokeWidth = 3.6f
            nc.drawPoints(projMid, 0, cntMid * 2, dotPaint)
            dotPaint.color = 0xB32A2722.toInt(); dotPaint.strokeWidth = 4.6f
            nc.drawPoints(projNear, 0, cntNear * 2, dotPaint)

            if (hiIdx >= 0) {
                fun proj3(i: Int): Offset? {
                    val x = pca[i * 3]; val y = pca[i * 3 + 1]; val z = pca[i * 3 + 2]
                    val x1 = x * cyaw + z * syaw
                    val z1 = -x * syaw + z * cyaw
                    val y2 = y * cp - z1 * sp
                    val z2 = y * sp + z1 * cp
                    val depth = z2 + view.dist
                    if (depth <= 0.35f) return null
                    val s = f / depth
                    return Offset(x1 * s + cx, y2 * s + cy)
                }
                for (i in hiNeighbors) {
                    val p = proj3(i)
                    if (p != null) {
                        dotPaint.color = 0xCC35565A.toInt(); dotPaint.strokeWidth = 7f
                        nc.drawPoint(p.x, p.y, dotPaint)
                        nc.drawText(words[i], p.x, p.y - 12f, labelPaint)
                    }
                }
                val hp = proj3(hiIdx)
                if (hp != null) {
                    dotPaint.color = 0xFFB23A2E.toInt(); dotPaint.strokeWidth = 11f
                    nc.drawPoint(hp.x, hp.y, dotPaint)
                    nc.drawText(words[hiIdx], hp.x, hp.y - 16f, hiPaint)
                }
            }
        }
    }
}

private fun cos(v: Float): Float = kotlin.math.cos(v)
private fun sin(v: Float): Float = kotlin.math.sin(v)
