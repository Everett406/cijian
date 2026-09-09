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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.cijian.app.ciJian
import com.cijian.app.core.Word2VecEngine
import com.cijian.app.ui.components.CinnabarButton
import com.cijian.app.ui.components.WordChip
import com.cijian.app.ui.nav.Routes
import com.cijian.app.ui.theme.Cinnabar
import com.cijian.app.ui.theme.Gold
import com.cijian.app.ui.theme.Indigo
import com.cijian.app.ui.theme.InkFaint
import com.cijian.app.ui.theme.InkSoft
import com.cijian.app.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 视图状态（手势闭包持有此对象，避免捕获过期值） */
private class MapView {
    var scale by mutableStateOf(1f)     // 墨涟缩放
    var offX by mutableStateOf(0f)
    var offY by mutableStateOf(0f)
    var yaw by mutableStateOf(0.6f)     // 星海
    var pitch by mutableStateOf(0.35f)
    var dist by mutableStateOf(3.2f)
}

/** 墨涟图节点：unit 坐标（约 [-1.5,1.5]），ring 0 最近 */
private class RippleNode(val idx: Int, val word: String, val score: Float, val x: Float, val y: Float, val ring: Int)
private class RippleLayout(val centerIdx: Int, val centerWord: String, val nodes: List<RippleNode>)

private const val RING_RADII = 0.34f   // 第 k 圈半径 = 0.34 + 0.27k

/** 由中心词构建墨涟布局（重活，须在后台线程调用） */
private fun buildRippleLayout(engine: Word2VecEngine, centerIdx: Int): RippleLayout? {
    val w = engine.words[centerIdx]
    val v = engine.vector(w) ?: return null
    val nb = engine.nearestTo(v, 46, setOf(w))
    val bands = floatArrayOf(0.70f, 0.55f, 0.40f, 0.20f)
    val golden = 2.399963f
    val seed = (centerIdx % 89) / 89f * 6.28318f
    val counters = IntArray(bands.size + 1)
    val nodes = ArrayList<RippleNode>(nb.size)
    for (n in nb) {
        val ring = when {
            n.score >= bands[0] -> 0
            n.score >= bands[1] -> 1
            n.score >= bands[2] -> 2
            n.score >= bands[3] -> 3
            else -> 4
        }
        val idx = engine.indexOf(n.word) ?: continue
        val r = RING_RADII + 0.27f * ring
        val k = counters[ring]++
        val ang = seed + k * golden + ring * 0.7f
        nodes.add(RippleNode(idx, n.word, n.score, r * cos(ang), r * sin(ang), ring))
    }
    return RippleLayout(centerIdx, w, nodes)
}

@Composable
fun StarMapScreen(nav: NavHostController, focus: String) {
    val app = LocalContext.current.ciJian
    val engine = app.engine
    val view = remember { MapView() }
    val scope = rememberCoroutineScope()

    var is3d by remember { mutableStateOf(false) }      // false = 墨涟(2D邻域) / true = 星海(3D)
    var dense by remember { mutableStateOf(true) }      // 星海点密度：密 / 疏
    var query by remember { mutableStateOf("") }
    var hiIdx by remember { mutableStateOf(-1) }
    var hiNeighbors by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableStateOf(-1) }
    var ripple by remember { mutableStateOf<RippleLayout?>(null) }

    fun focusOn(word: String) {
        val i = engine.indexOf(word) ?: return
        scope.launch(Dispatchers.Default) {
            val rp = buildRippleLayout(engine, i)
            val v = engine.vector(word)
            val nb = if (v != null) engine.nearestTo(v, 10, setOf(word)) else emptyList()
            val nbIdx = nb.mapNotNull { engine.indexOf(it.word) }.toSet()
            withContext(Dispatchers.Main) {
                hiIdx = i
                hiNeighbors = nbIdx
                selected = -1
                ripple = rp
                view.offX = 0f; view.offY = 0f; view.scale = 1f
            }
        }
    }

    LaunchedEffect(focus) {
        if (focus.isNotBlank() && engine.contains(focus)) focusOn(focus)
        else if (hiIdx < 0) focusOn(engine.randomCommonWord(twoChar = true))
    }
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
                    if (is3d) "14 万词的语义星空 · 拖动环视，双指缩放"
                    else "四圈涟漪由近及远 · 点词可探",
                    color = InkSoft, fontSize = 12.sp
                )
            }
            FilterChip(
                selected = is3d,
                onClick = { is3d = !is3d },
                label = { Text(if (is3d) "星海 3D" else "墨涟 2D") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Cinnabar,
                    selectedLabelColor = Paper,
                )
            )
            if (is3d) {
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = dense,
                    onClick = { dense = !dense },
                    label = { Text(if (dense) "密" else "疏") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Indigo,
                        selectedLabelColor = Paper,
                    )
                )
            }
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
            CinnabarButton(text = "定位", onClick = {
                val w = query.trim()
                if (engine.contains(w)) focusOn(w)
            })
        }
        Spacer(Modifier.height(10.dp))

        // ── 图区 ──
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            val pca3 = engine.pca3
            if (is3d && pca3 == null) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (engine.pcaComputing) {
                        CircularProgressIndicator(color = Cinnabar, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("正在为星辰排布坐标…", color = InkSoft, fontSize = 13.sp)
                    } else {
                        Text("暂无三维投影坐标", color = InkSoft, fontSize = 14.sp)
                    }
                }
            } else {
                StarCanvas(
                    pca3 = pca3,
                    count = engine.size,
                    words = engine.words,
                    is3d = is3d,
                    dense = dense,
                    view = view,
                    ripple = ripple,
                    hiIdx = hiIdx,
                    hiNeighbors = hiNeighbors,
                    selected = selected,
                    onSelect = { selected = it },
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
                            .border(1.dp, InkFaint, RoundedCornerShape(6.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
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
    pca3: FloatArray?,
    count: Int,
    words: List<String>,
    is3d: Boolean,
    dense: Boolean,
    view: MapView,
    ripple: RippleLayout?,
    hiIdx: Int,
    hiNeighbors: Set<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    val dotPaint = remember {
        Paint().apply { isAntiAlias = true; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
    }
    // 标签：纸色描边晕圈 + 墨色填充，保证在任何底色上可读
    val haloPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF6F2E7.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeJoin = Paint.Join.ROUND
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.SERIF
        }
    }
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2A2722")
            textSize = 26f; textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.SERIF
        }
    }
    val hiPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#B23A2E")
            textSize = 30f; textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        }
    }
    val ringPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#72695B")
            textSize = 21f; textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.SERIF
        }
    }

    Canvas(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Paper)
            .border(1.dp, InkFaint, RoundedCornerShape(6.dp))
            .pointerInput(is3d) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (is3d) {
                        view.yaw -= pan.x * 0.008f   // 抓住星空拖动：内容随手的方向走
                        view.pitch = (view.pitch + pan.y * 0.008f).coerceIn(-1.35f, 1.35f)
                        view.dist = (view.dist / zoom).coerceIn(0.55f, 12f)   // 大幅放宽缩放深度
                    } else {
                        view.scale = (view.scale * zoom).coerceIn(0.5f, 3f)
                        view.offX += pan.x
                        view.offY += pan.y
                    }
                }
            }
            .pointerInput(is3d) {
                detectTapGestures(onTap = { p ->
                    if (!is3d) {
                        val rp = ripple ?: return@detectTapGestures
                        val sizeC = this.size
                        val base = min(sizeC.width, sizeC.height) / 3.1f * view.scale
                        val ox = sizeC.width / 2f + view.offX
                        val oy = sizeC.height / 2f + view.offY
                        var best = -1; var bd = 40f * 40f
                        for (n in rp.nodes) {
                            val dx = ox + n.x * base - p.x
                            val dy = oy + n.y * base - p.y
                            val d = dx * dx + dy * dy
                            if (d < bd) { bd = d; best = n.idx }
                        }
                        val dcx = ox - p.x; val dcy = oy - p.y
                        if (dcx * dcx + dcy * dcy < 40f * 40f) best = rp.centerIdx
                        onSelect(best)   // 未命中时取消选中
                    }
                })
            }
    ) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h / 2f
        val nc = drawContext.canvas.nativeCanvas

        if (!is3d) {
            // ── 墨涟：2D 语义邻域图 ──
            val rp = ripple ?: return@Canvas
            val base = min(w, h) / 3.1f * view.scale
            val ox = cx + view.offX
            val oy = cy + view.offY

            // 五圈涟漪（相似度等高线）
            val ringLabels = listOf("≥0.70", "≥0.55", "≥0.40", "≥0.20", "更远")
            for (k in 0 until 5) {
                val rpx = (RING_RADII + 0.27f * k) * base
                drawCircle(
                    color = InkFaint.copy(alpha = 0.6f),
                    radius = rpx,
                    center = Offset(ox, oy),
                    style = Stroke(
                        width = 1.6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 14f))
                    )
                )
                nc.drawText(ringLabels[k], ox, oy - rpx - 7f, ringPaint)
            }
            // 涟漪辐线
            for (n in rp.nodes) {
                drawLine(
                    InkSoft.copy(alpha = 0.22f),
                    Offset(ox, oy),
                    Offset(ox + n.x * base, oy + n.y * base),
                    strokeWidth = 1.6f
                )
            }
            // 词点
            for (n in rp.nodes) {
                val p = Offset(ox + n.x * base, oy + n.y * base)
                val isSel = n.idx == selected
                val r = if (isSel) 12f else if (n.ring <= 1) 9f else 7.5f
                drawCircle(Paper, r, p)
                val col = when {
                    n.idx == hiIdx -> Cinnabar
                    isSel -> Gold
                    n.ring <= 1 -> Indigo
                    else -> InkSoft
                }
                drawCircle(col, r, p, style = Stroke(width = 3.6f))
            }
            // 中心
            drawCircle(Cinnabar, 13f, Offset(ox, oy))
            drawCircle(Paper, 13f, Offset(ox, oy), style = Stroke(width = 3f))
            // 标签（晕圈 + 填充）
            ncDrawHaloText(nc, haloPaint, rp.centerWord, ox, oy - 22f, hiPaint)
            for (n in rp.nodes) {
                val isSel = n.idx == selected
                val paint = if (n.idx == hiIdx) hiPaint else labelPaint
                labelPaint.textSize = if (n.ring <= 1) 27f else 24f
                val alpha = if (n.ring <= 2) 255 else 210
                labelPaint.alpha = alpha
                ncDrawHaloText(nc, haloPaint, n.word, ox + n.x * base, oy + n.y * base - (if (isSel) 18f else 15f), paint)
            }
        } else {
            // ── 星海：3D 全词漫游（带 LOD 剔除） ──
            if (pca3 == null) return@Canvas
            val cyaw = cos(view.yaw); val syaw = sin(view.yaw)
            val cp = cos(view.pitch); val sp = sin(view.pitch)
            val f = min(w, h) * 0.62f
            var cntFar = 0; var cntMid = 0; var cntNear = 0
            val stride = if (dense) 1 else 3
            val dNear = view.dist * 0.75f
            val dMid = view.dist * 1.15f
            val capFar = 12000; val capMid = 9000; val capNear = 7000
            val projFar = projFarBuf; val projMid = projMidBuf; val projNear = projNearBuf
            val nearIdx = nearIdxBuf
            val seen = seenBuf; seen.clear()
            val CELL = 9f
            var i = 0
            while (i < count) {
                val x = pca3[i * 3]; val y = pca3[i * 3 + 1]; val z = pca3[i * 3 + 2]
                val x1 = x * cyaw + z * syaw
                val z1 = -x * syaw + z * cyaw
                val y2 = y * cp - z1 * sp
                val z2 = y * sp + z1 * cp
                val depth = z2 + view.dist
                if (depth > 0.35f) {
                    val s = f / depth
                    val sx = x1 * s + cx
                    val syy = y2 * s + cy
                    if (sx > -50f && sx < w + 50f && syy > -50f && syy < h + 50f) {
                        val far = depth > dMid
                        val mid = !far && depth > dNear
                        val cap = if (far) capFar else if (mid) capMid else capNear
                        val cnt = if (far) cntFar else if (mid) cntMid else cntNear
                        // LOD：屏幕网格去重 + 分层容量上限，靠近了自然显现更多细节
                        if (cnt < cap) {
                            val key = (sx / CELL).toInt() * 100003 + (syy / CELL).toInt()
                            if (seen.add(key)) {
                                if (far) { projFar[cntFar * 2] = sx; projFar[cntFar * 2 + 1] = syy; cntFar++ }
                                else if (mid) { projMid[cntMid * 2] = sx; projMid[cntMid * 2 + 1] = syy; cntMid++ }
                                else {
                                    projNear[cntNear * 2] = sx; projNear[cntNear * 2 + 1] = syy
                                    if (cntNear < capNear) nearIdx[cntNear] = i
                                    cntNear++
                                }
                            }
                        }
                    }
                }
                i += stride
            }
            dotPaint.color = 0x592A2722.toInt(); dotPaint.strokeWidth = 2.6f
            nc.drawPoints(projFar, 0, cntFar * 2, dotPaint)
            dotPaint.color = 0x992A2722.toInt(); dotPaint.strokeWidth = 3.6f
            nc.drawPoints(projMid, 0, cntMid * 2, dotPaint)
            dotPaint.color = 0xCC2A2722.toInt(); dotPaint.strokeWidth = 4.6f
            nc.drawPoints(projNear, 0, cntNear * 2, dotPaint)

            // 靠近时：近层词浮现标签（游戏式 LOD 加载）
            if (view.dist < 2.4f) {
                val lseen = labelSeenBuf; lseen.clear()
                val LCELL = 120f
                var drawn = 0
                for (k in 0 until cntNear) {
                    if (drawn >= 12) break
                    val sx = projNear[k * 2]; val syy = projNear[k * 2 + 1]
                    val key = (sx / LCELL).toInt() * 100003 + (syy / LCELL).toInt()
                    if (lseen.add(key)) {
                        labelPaint.textSize = 24f; labelPaint.alpha = 235
                        ncDrawHaloText(nc, haloPaint, words[nearIdx[k]], sx, syy - 12f, labelPaint)
                        drawn++
                    }
                }
            }

            if (hiIdx >= 0) {
                fun proj3(idx: Int): Offset? {
                    val x = pca3[idx * 3]; val y = pca3[idx * 3 + 1]; val z = pca3[idx * 3 + 2]
                    val x1 = x * cyaw + z * syaw
                    val z1 = -x * syaw + z * cyaw
                    val y2 = y * cp - z1 * sp
                    val z2 = y * sp + z1 * cp
                    val depth = z2 + view.dist
                    if (depth <= 0.35f) return null
                    val s = f / depth
                    return Offset(x1 * s + cx, y2 * s + cy)
                }
                for (idx in hiNeighbors) {
                    val p = proj3(idx)
                    if (p != null) {
                        dotPaint.color = 0xCC35565A.toInt(); dotPaint.strokeWidth = 7f
                        nc.drawPoint(p.x, p.y, dotPaint)
                        ncDrawHaloText(nc, haloPaint, words[idx], p.x, p.y - 12f, labelPaint)
                    }
                }
                val hp = proj3(hiIdx)
                if (hp != null) {
                    dotPaint.color = 0xFFB23A2E.toInt(); dotPaint.strokeWidth = 11f
                    nc.drawPoint(hp.x, hp.y, dotPaint)
                    ncDrawHaloText(nc, haloPaint, words[hiIdx], hp.x, hp.y - 16f, hiPaint)
                }
            }
        }
    }
}

/** 晕圈文字：先描纸色边，再填充 */
private fun ncDrawHaloText(nc: android.graphics.Canvas, halo: Paint, text: String, x: Float, y: Float, fill: Paint) {
    halo.textSize = fill.textSize
    halo.typeface = fill.typeface
    nc.drawText(text, x, y, halo)
    nc.drawText(text, x, y, fill)
}

/** 3D 投影缓冲（复用，避免每帧分配） */
private val projFarBuf = FloatArray(12000 * 2)
private val projMidBuf = FloatArray(9000 * 2)
private val projNearBuf = FloatArray(7000 * 2)
private val nearIdxBuf = IntArray(7000)
private val seenBuf = java.util.HashSet<Int>(16384)
private val labelSeenBuf = java.util.HashSet<Int>(64)
