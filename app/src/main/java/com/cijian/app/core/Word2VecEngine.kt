package com.cijian.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

data class Scored(val word: String, val score: Float)
data class PathStop(val word: String, val t: Float, val cosToTarget: Float)

/**
 * 词间核心引擎：全量向量驻留内存（float32 已归一化），暴力点积检索。
 * 143,613 词 × 200 维 ≈ 115MB，单次近邻 ~30ms（现代手机），无需任何向量索引库。
 */
class Word2VecEngine {

    var dim: Int = 0; private set
    var size: Int = 0; private set
    var words: List<String> = emptyList(); private set
    private lateinit var vecs: FloatArray          // 已 L2 归一化，行优先
    private lateinit var index: HashMap<String, Int>

    var pca2: FloatArray? by mutableStateOf(null); private set   // size*2，已归一化到 [-1,1]
    var pca3: FloatArray? by mutableStateOf(null); private set   // size*3
    var pcaComputing: Boolean by mutableStateOf(false); private set

    /** 加载模型 + 归一化 + 载入/回补 PCA。耗时操作，须在 IO/Default 线程调用。 */
    fun load(context: Context, onProgress: (Float) -> Unit = {}) {
        val parsed = context.assets.open(ModelLoader.MODEL_ASSET).use {
            ModelLoader.parse(it, onProgress)
        }
        dim = parsed.dim
        size = parsed.count
        words = parsed.words.toList()
        vecs = parsed.vectors

        // L2 归一化（原始范数均值约 4.27）
        for (i in 0 until size) {
            val base = i * dim
            var n2 = 0f
            for (j in 0 until dim) { val x = vecs[base + j]; n2 += x * x }
            val inv = 1f / kotlin.math.sqrt(n2.coerceAtLeast(1e-12f))
            for (j in 0 until dim) vecs[base + j] *= inv
        }

        index = HashMap(size * 2)
        for (i in 0 until size) if (!index.containsKey(words[i])) index[words[i]] = i

        // PCA 资产：优先 assets，缺失则端上计算并缓存
        val cacheDir = context.getFileStreamPath("pca_cache")
        cacheDir.mkdirs()
        pca2 = readPcaCached(context, cacheDir.resolve("pca2d.f32")) { ModelLoader.readPca(context, ModelLoader.PCA2_ASSET, 2, "CJP2") }
            ?.also { normalizeCoords(it, 2) }
        pca3 = readPcaCached(context, cacheDir.resolve("pca3d.f32")) { ModelLoader.readPca(context, ModelLoader.PCA3_ASSET, 3, "CJP3") }
            ?.also { normalizeCoords(it, 3) }
    }

    /** 兜底：若缺 PCA（换了模型），在后台算并缓存。pcaComputing/pca* 为 Compose 状态，UI 自动感知。 */
    fun ensurePcaAsync(context: Context, scope: CoroutineScope): Boolean {
        if (pca2 != null && pca3 != null) return false
        if (pcaComputing) return true
        pcaComputing = true
        scope.launch(Dispatchers.Default) {
            val cacheDir = context.getFileStreamPath("pca_cache")
            cacheDir.mkdirs()
            if (pca2 == null) {
                val c = Pca.compute(vecs, size, dim, 2)
                normalizeCoords(c, 2)
                pca2 = c
                runCatching { cacheDir.resolve("pca2d.f32").writeBytes(toAssetBytes(c, 2, "CJP2")) }
            }
            if (pca3 == null) {
                val c = Pca.compute(vecs, size, dim, 3)
                normalizeCoords(c, 3)
                pca3 = c
                runCatching { cacheDir.resolve("pca3d.f32").writeBytes(toAssetBytes(c, 3, "CJP3")) }
            }
            pcaComputing = false
        }
        return true
    }

    private fun readPcaCached(context: Context, cacheFile: java.io.File, assetLoader: () -> FloatArray?): FloatArray? {
        // 1) 内存缓存已有则跳过；2) 本地缓存；3) assets
        if (cacheFile.exists()) {
            runCatching {
                val b = cacheFile.readBytes()
                return toFloatArray(b)
            }
        }
        val fromAsset = assetLoader() ?: return null
        runCatching { cacheFile.writeBytes(fromAsset.toBytes()) }
        return fromAsset
    }

    private fun FloatArray.toBytes(): ByteArray {
        val bb = java.nio.ByteBuffer.allocate(size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (f in this) bb.putFloat(f)
        return bb.array()
    }

    private fun toFloatArray(b: ByteArray): FloatArray {
        val bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(b.size / 4)
        for (i in out.indices) out[i] = bb.float
        return out
    }

    private fun toAssetBytes(coords: FloatArray, k: Int, magic: String): ByteArray {
        val head = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        head.put(magic.toByteArray(Charsets.US_ASCII), 0, 4)
        head.putInt(size); head.putInt(k)
        return head.array() + coords.toBytes()
    }

    /** PCA 坐标缩放到 [-1,1]，便于渲染。 */
    private fun normalizeCoords(coords: FloatArray, k: Int) {
        for (c in 0 until k) {
            var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
            for (i in 0 until size) {
                val v = coords[i * k + c]
                if (v < mn) mn = v
                if (v > mx) mx = v
            }
            val span = (mx - mn).coerceAtLeast(1e-6f)
            val scale = 2f / span
            val shift = -(mx + mn) / span
            for (i in 0 until size) coords[i * k + c] = coords[i * k + c] * scale + shift
        }
    }

    // ────────────────────────── 基础查询 ──────────────────────────

    fun contains(w: String): Boolean = ::index.isInitialized && index.containsKey(w)
    fun indexOf(w: String): Int = index[w] ?: -1
    fun vector(w: String): FloatArray? {
        val i = indexOf(w) ?: return null
        return vecs.copyOfRange(i * dim, i * dim + dim)
    }

    fun similarity(a: String, b: String): Float {
        val ia = indexOf(a) ?: return -1f
        val ib = indexOf(b) ?: return -1f
        var s = 0f
        val ba = ia * dim; val bb = ib * dim
        for (j in 0 until dim) s += vecs[ba + j] * vecs[bb + j]
        return s
    }

    fun randomWord(): String {
        val rnd = Random.Default
        repeat(60) {
            val w = words[rnd.nextInt(size)]
            if (w.length >= 2 && !w.any { it.isWhitespace() }) return w
        }
        return words[rnd.nextInt(size)]
    }

    /** 前缀搜索建议：精确匹配置顶。 */
    fun prefixSearch(q: String, limit: Int = 12): List<String> {
        if (q.isBlank()) return emptyList()
        val exact = if (contains(q)) listOf(q) else emptyList()
        val rest = words.asSequence()
            .filter { it != q && it.startsWith(q) }
            .take(limit - exact.size)
            .toList()
        return exact + rest
    }

    // ────────────────────────── 近邻 ──────────────────────────

    fun nearest(word: String, k: Int = 20, exclude: Set<String> = emptySet()): List<Scored> {
        val i = indexOf(word) ?: return emptyList()
        val v = vecs.copyOfRange(i * dim, i * dim + dim)
        return nearestTo(v, k, exclude + word)
    }

    /** 对任意向量（自动归一化）暴力检索 top-k。 */
    fun nearestTo(v: FloatArray, k: Int, exclude: Set<String> = emptySet()): List<Scored> {
        if (!::vecs.isInitialized) return emptyList()
        var n2 = 0f
        for (x in v) n2 += x * x
        val inv = 1f / kotlin.math.sqrt(n2.coerceAtLeast(1e-12f))
        for (j in v.indices) v[j] *= inv

        val heapScores = FloatArray(k) { Float.NEGATIVE_INFINITY }
        val heapIdx = IntArray(k) { -1 }
        val ex = exclude
        for (i in 0 until size) {
            val w = words[i]
            if (w in ex) continue
            val base = i * dim
            var s = 0f
            for (j in 0 until dim) s += vecs[base + j] * v[j]
            if (s > heapScores[0]) {
                // 替换当前最小者
                var worst = 0
                heapScores[0] = s; heapIdx[0] = i
                for (t in 1 until k) if (heapScores[t] < heapScores[worst]) worst = t
                // 把 worst 位置挪到 0（简易选择堆）
                if (worst != 0) {
                    val ts = heapScores[0]; val ti = heapIdx[0]
                    heapScores[0] = heapScores[worst]; heapIdx[0] = heapIdx[worst]
                    heapScores[worst] = ts; heapIdx[worst] = ti
                }
            }
        }
        return heapIdx.withIndex()
            .filter { it.value >= 0 }
            .map { Scored(words[it.value], heapScores[it.index]) }
            .sortedByDescending { it.score }
            .take(k)
    }

    // ────────────────────────── 词语算术 ──────────────────────────

    /** result = Σ(plus) − Σ(minus)，排除参与运算的词。 */
    fun calc(plus: List<String>, minus: List<String>, k: Int = 10): List<Scored> {
        val acc = FloatArray(dim)
        var used = 0
        val exclude = mutableSetOf<String>()
        for (w in plus) {
            val i = indexOf(w) ?: continue
            val base = i * dim
            for (j in 0 until dim) acc[j] += vecs[base + j]
            exclude.add(w); used++
        }
        for (w in minus) {
            val i = indexOf(w) ?: continue
            val base = i * dim
            for (j in 0 until dim) acc[j] -= vecs[base + j]
            exclude.add(w); used++
        }
        if (used == 0) return emptyList()
        return nearestTo(acc, k, exclude)
    }

    // ────────────────────────── 词义之路 ──────────────────────────

    /**
     * 从 from 走到 to：线性插值 p(t) = (1-t)·v_from + t·v_to，
     * 每一步取最近邻并排除已访问词，自然形成一条语义路径。
     */
    fun path(from: String, to: String, steps: Int = 8): List<PathStop> {
        val ia = indexOf(from) ?: return emptyList()
        val ib = indexOf(to) ?: return emptyList()
        if (ia == ib) return emptyList()
        val va = vecs.copyOfRange(ia * dim, ia * dim + dim)
        val vb = vecs.copyOfRange(ib * dim, ib * dim + dim)
        val visited = LinkedHashSet<String>()
        visited.add(from); visited.add(to)
        val stops = mutableListOf<PathStop>()
        for (s in 1 until steps) {
            val t = s.toFloat() / steps
            val p = FloatArray(dim) { j -> (1f - t) * va[j] + t * vb[j] }
            val pick = nearestTo(p, 1, visited).firstOrNull() ?: break
            visited.add(pick.word)
            // 与目标的余弦
            var cosB = 0f
            for (j in 0 until dim) cosB += p[j] * vb[j]
            stops.add(PathStop(pick.word, t, cosB))
        }
        return stops
    }

    // ────────────────────────── 类比测验 ──────────────────────────

    /** 出题：a:b :: c:? ，从词对库选 a→b，c 取 a 的近邻，答案按经典四词类比推理。 */
    fun generateQuiz(pairs: List<Pair<String, String>>): QuizQuestion? {
        val usable = pairs.filter { contains(it.first) && contains(it.second) }
        if (usable.isEmpty()) return null
        val (a, b) = usable[Random.Default.nextInt(usable.size)]
        val va = vector(a)!!
        val neighbors = nearestTo(va, 40, setOf(a, b)).filter { it.score < 0.82f }
        val c = neighbors.getOrNull(Random.Default.nextInt(neighbors.size.coerceAtLeast(1)))?.word
            ?: return null
        val vb = vector(b)!!; val vc = vector(c)!!
        val target = FloatArray(dim) { j -> vb[j] - va[j] + vc[j] }
        val answer = nearestTo(target, 1, setOf(a, b, c)).firstOrNull() ?: return null
        val distractors = nearestTo(vc, 12, setOf(a, b, c, answer.word))
            .shuffled().take(3).map { it.word }
        val options = (distractors + answer.word).shuffled()
        return QuizQuestion(a, b, c, answer.word, options, answer.score)
    }
}

data class QuizQuestion(
    val a: String, val b: String, val c: String, val answer: String,
    val options: List<String>, val answerScore: Float
)
