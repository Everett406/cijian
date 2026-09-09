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
    var commonWords: List<String> = emptyList(); private set   // 人工精选常用词（assets/common_words.txt，已过词表校验）
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

        // 常用词表：猜词 / 词桥 / 星图随机定位共用，避免机器随机到偏门词
        commonWords = runCatching {
            context.assets.open("common_words.txt").bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() && index.containsKey(it) }
        }.getOrDefault(emptyList())

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

    /** 不存在时返回 null —— 调用方一律用 `?: ` 判空，避免拿到 -1 误用导致崩溃 */
    fun indexOf(w: String): Int? = if (::index.isInitialized) index[w] else null
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

    /** 从人工精选常用词里随机取一个（可选限定两个字），用于猜词/词桥/星图定位 */
    fun randomCommonWord(twoChar: Boolean = false): String {
        val pool = if (twoChar) commonWords.filter { it.length == 2 } else commonWords
        if (pool.isEmpty()) return randomWord()
        return pool[Random.Default.nextInt(pool.size)]
    }

    /** 余弦相似度映射为 0~100 的「相关度」：-0.35 及以下归零，1.0 归百 */
    fun relevancePct(cos: Float): Float = ((cos + 0.35f) / 1.35f).coerceIn(0f, 1f) * 100f

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

    // ────────────────────────── 一句话凝词 ──────────────────────────

    /**
     * 凝词：对一句话的分词结果做加权平均（两字词权重 1.0、单字 0.45 由调用方定），
     * 凝出这句心事指向的那个词，并给出三位邻居。weighted 由调用方分词后传入。
     */
    fun distill(weighted: List<Pair<String, Float>>): DistillResult? {
        if (weighted.isEmpty()) return null
        val acc = FloatArray(dim)
        var tw = 0f
        val used = mutableListOf<String>()
        for ((w, wt) in weighted) {
            val v = vector(w) ?: continue
            for (j in 0 until dim) acc[j] += v[j] * wt
            tw += wt; used.add(w)
        }
        if (tw <= 0f || used.isEmpty()) return null
        for (j in 0 until dim) acc[j] /= tw
        val nb = nearestTo(acc, 6, used.toSet())
        if (nb.isEmpty()) return null
        val core = nb.first()
        val iCore = indexOf(core.word) ?: return null
        var cos = 0f
        for (j in 0 until dim) cos += acc[j] * vecs[iCore * dim + j]   // acc 已被 nearestTo 归一化
        return DistillResult(core.word, cos, nb.drop(1).take(3), used)
    }

    // ────────────────────────── 词桥参考路线 ──────────────────────────

    /**
     * 引擎参考路线：beam search（宽度 4），每步只踩近邻，尽量少步数抵达终点。
     * 返回完整路线（含起终点）；maxSteps 内到不了则返回 null。
     */
    fun bridgeBest(from: String, to: String, maxSteps: Int = 10): List<String>? {
        if (indexOf(from) == null || indexOf(to) == null) return null
        if (from == to) return listOf(from)
        data class Node(val route: List<String>, val last: String, val visited: Set<String>)
        var beam = listOf(Node(listOf(from), from, setOf(from)))
        repeat(maxSteps) {
            val next = mutableListOf<Node>()
            for (node in beam) {
                if (node.last == to) return node.route
                for (cand in nearest(node.last, 10, node.visited)) {
                    next.add(Node(node.route + cand.word, cand.word, node.visited + cand.word))
                }
            }
            if (next.isEmpty()) break
            beam = next.distinctBy { it.last }
                .sortedByDescending { similarity(it.last, to) }
                .take(4)
        }
        return beam.firstOrNull { it.last == to }?.route
    }

    // ────────────────────────── 类比测验 ──────────────────────────

    /** 出题（题型与难度由调用方决定；「混合」模式在 UI 层随机挑一种传入）。 */
    fun generateQuiz(pairs: List<Pair<String, String>>, kind: QuizKind, level: QuizLevel = QuizLevel.NORMAL): QuizQuestion? = when (kind) {
        QuizKind.ANALOGY -> genAnalogy(pairs, level)
        QuizKind.CALC -> genCalc(pairs, level)
        QuizKind.ODD -> genOdd(level)
        QuizKind.CLOSEST -> genClosest(level)
        QuizKind.MIX -> null
    }

    private fun usablePairs(pairs: List<Pair<String, String>>) =
        pairs.filter { contains(it.first) && contains(it.second) }

    /** 干扰项不足时用常用词兜底补齐，保证每题必有 4 个选项 */
    private fun fillDistractors(ds: MutableList<String>, answer: String, exclude: Set<String>, need: Int) {
        var guard = 0
        while (ds.size < need && guard++ < 40) {
            val w = randomCommonWord()
            if (w !in ds && w !in exclude && w != answer) ds.add(w)
        }
    }

    /** 题型一：经典类比 A 之于 B，如同 C 之于 ？（把 B−A 的关系差平移到 C） */
    private fun genAnalogy(pairs: List<Pair<String, String>>, level: QuizLevel): QuizQuestion? {
        val usable = usablePairs(pairs)
        if (usable.isEmpty()) return null
        val cMax = when (level) { QuizLevel.EASY -> 0.72f; QuizLevel.HARD -> 0.85f; else -> 0.82f }
        val gap = when (level) { QuizLevel.EASY -> 0.15f; QuizLevel.HARD -> 0.03f; else -> 0.06f }
        val ansMin = when (level) { QuizLevel.EASY -> 0.42f; QuizLevel.HARD -> 0.30f; else -> 0.35f }
        repeat(4) {
            val (a, b) = usable[Random.Default.nextInt(usable.size)]
            val va = vector(a) ?: return null
            val c = nearestTo(va, 40, setOf(a, b))
                .filter { it.score < cMax }
                .randomOrNull()?.word ?: return@repeat
            val vb = vector(b) ?: return null
            val vc = vector(c) ?: return null
            val target = FloatArray(dim) { j -> vb[j] - va[j] + vc[j] }
            val ans = nearestTo(target, 1, setOf(a, b, c)).firstOrNull() ?: return@repeat
            if (ans.score < ansMin) return@repeat  // 类比关系太弱，换一组
            val ds = nearestTo(target, 40, setOf(a, b, c, ans.word))
                .filter { it.score < ans.score - gap }.shuffled().take(3).map { it.word }.toMutableList()
            fillDistractors(ds, ans.word, setOf(a, b, c), 3)
            return QuizQuestion(
                QuizKind.ANALOGY,
                listOf("$a 之于 $b", "如同 $c 之于 ？"),
                "思路：把「$b - $a」这段关系差，平移到 $c 上试试",
                (ds + ans.word).shuffled(), ans.word, ans.score,
                "推理式：$b - $a + $c ≈ ${ans.word}（余弦 %.3f）".format(ans.score)
            )
        }
        return null
    }

    /** 题型二：二词算式 A - B = ? 或 A + B = ?（入门档只出减法，更直观） */
    private fun genCalc(pairs: List<Pair<String, String>>, level: QuizLevel): QuizQuestion? {
        val usable = usablePairs(pairs)
        if (usable.isEmpty()) return null
        val gap = when (level) { QuizLevel.EASY -> 0.12f; QuizLevel.HARD -> 0.04f; else -> 0.07f }
        repeat(6) {
            val (a, b) = usable[Random.Default.nextInt(usable.size)]
            val va = vector(a) ?: return null
            val vb = vector(b) ?: return null
            val minus = if (level == QuizLevel.EASY) true else Random.Default.nextBoolean()
            val target = FloatArray(dim) { j -> if (minus) va[j] - vb[j] else va[j] + vb[j] }
            val ans = nearestTo(target, 1, setOf(a, b)).firstOrNull() ?: return@repeat
            val ok = when {
                minus && level == QuizLevel.EASY -> ans.score in 0.35f..0.80f
                minus -> ans.score in 0.25f..0.88f
                level == QuizLevel.NORMAL -> ans.score in 0.45f..0.92f
                else -> ans.score in 0.40f..0.94f
            }
            if (!ok) return@repeat
            val ds = nearestTo(target, 40, setOf(a, b, ans.word))
                .filter { it.score < ans.score - gap }.shuffled().take(3).map { it.word }.toMutableList()
            fillDistractors(ds, ans.word, setOf(a, b), 3)
            val expr = if (minus) "$a - $b = ?" else "$a + $b = ?"
            return QuizQuestion(
                QuizKind.CALC, listOf(expr),
                if (minus) "思路：减去一个词，等于朝它语义的反方向走一步"
                else "思路：两个词叠加，结果落在它们共同指向的方向上",
                (ds + ans.word).shuffled(), ans.word, ans.score,
                "${if (minus) "$a - $b" else "$a + $b"} ≈ ${ans.word}（余弦 %.3f）".format(ans.score)
            )
        }
        return null
    }

    /** 题型三：圈外人（三个同类词夹一个例外；烧脑档例外更难抓） */
    private fun genOdd(level: QuizLevel): QuizQuestion? {
        repeat(6) {
            val seed = randomCommonWord()
            val near = nearest(seed, 40, setOf(seed))
            val gMin: Float; val gMax: Float; val oMin: Float; val oMax: Float; val mutual: Float
            when (level) {
                QuizLevel.EASY -> { gMin = 0.62f; gMax = 0.85f; oMin = 0.20f; oMax = 0.40f; mutual = 0.50f }
                QuizLevel.HARD -> { gMin = 0.66f; gMax = 0.88f; oMin = 0.34f; oMax = 0.52f; mutual = 0.60f }
                else -> { gMin = 0.60f; gMax = 0.88f; oMin = 0.22f; oMax = 0.50f; mutual = 0.55f }
            }
            val group = near.filter { it.score in gMin..gMax }.take(3)
            if (group.size < 3) return@repeat
            val outCand = near.filter { it.score in oMin..oMax }
                .filter { o -> group.all { g -> similarity(o.word, g.word) < mutual } }
            val outsider = outCand.randomOrNull() ?: return@repeat
            val innerAvg = (similarity(group[0].word, group[1].word) +
                    similarity(group[0].word, group[2].word) +
                    similarity(group[1].word, group[2].word)) / 3f
            val outAvg = group.map { similarity(outsider.word, it.word) }.average().toFloat()
            return QuizQuestion(
                QuizKind.ODD,
                listOf("四个词里，谁最不合群？"),
                "思路：有三个词彼此的向量靠得很近，一个是例外",
                (group.map { it.word } + outsider.word).shuffled(),
                outsider.word, outAvg,
                "「${outsider.word}」与另三词的平均相似度仅 %.3f，而三词彼此 ≈ %.3f".format(outAvg, innerAvg)
            )
        }
        return null
    }

    /** 题型四：最近词热身（烧脑档干扰词更近） */
    private fun genClosest(level: QuizLevel): QuizQuestion? {
        repeat(6) {
            val seed = randomCommonWord()
            val near = nearest(seed, 12, setOf(seed))
            val ans = near.firstOrNull { it.score in 0.55f..0.95f } ?: return@repeat
            val dRange = when (level) {
                QuizLevel.EASY -> 0.05f..0.35f
                QuizLevel.HARD -> 0.28f..0.50f
                else -> 0.10f..0.42f
            }
            val ds = near.filter { it.score in dRange }.shuffled().take(3).map { it.word }.toMutableList()
            fillDistractors(ds, ans.word, setOf(seed), 3)
            return QuizQuestion(
                QuizKind.CLOSEST,
                listOf("哪个词与「$seed」最相近？"),
                "思路：谁的向量与它的夹角最小",
                (ds + ans.word).shuffled(), ans.word, ans.score,
                "「${ans.word}」与「$seed」的余弦相似度 %.3f".format(ans.score)
            )
        }
        return null
    }
}

/** 测验题型。MIX 仅用于 UI 层表示「混合」，传给引擎前会先随机成具体题型 */
enum class QuizKind(val label: String) {
    MIX("混合"), ANALOGY("类比"), CALC("算式"), ODD("圈外人"), CLOSEST("最近词")
}

/** 测验难度：档位控制答案与干扰项的语义距离 */
enum class QuizLevel(val label: String, val sub: String) {
    EASY("入门", "关系明显，一眼看穿"),
    NORMAL("进阶", "常规梯度"),
    HARD("烧脑", "干扰项贴得很近")
}

data class QuizQuestion(
    val kind: QuizKind,
    val promptLines: List<String>,   // 题目展示行（可多行，行距由 UI 控制）
    val hint: String,                // 运算思路提示（不剧透答案）
    val options: List<String>,
    val answer: String,
    val answerScore: Float,
    val explain: String              // 答题后的解析（含运算式与相似度）
)
