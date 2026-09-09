package com.cijian.app.core

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 端上 PCA 兜底：当 assets 中缺少 pca2d/pca3d 资产（例如用户更换了模型）时，
 * 用协方差矩阵 + 幂迭代求前 k 个主成分并投影。全量 143k×200 约 2~5 秒（多线程），结果缓存到内部存储。
 */
object Pca {

    /** 返回 n*k 的行优先坐标矩阵。onProgress 回调 0..1。 */
    suspend fun compute(
        vecs: FloatArray, n: Int, dim: Int, k: Int,
        threads: Int = (Runtime.getRuntime().availableProcessors()).coerceIn(2, 8),
        onProgress: (Float) -> Unit = {}
    ): FloatArray = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {

        // ── 均值 ──
        val mu = FloatArray(dim)
        for (i in 0 until n) {
            val base = i * dim
            for (j in 0 until dim) mu[j] += vecs[base + j]
        }
        for (j in 0 until dim) mu[j] /= n
        onProgress(0.1f)

        // ── 协方差 C = Σ (x-μ)(x-μ)ᵀ，按行分块并行 ──
        val C = FloatArray(dim * dim)
        val chunk = (n + threads - 1) / threads
        kotlinx.coroutines.coroutineScope {
            val jobs = (0 until threads).map { t ->
                kotlinx.coroutines.async {
                    val P = FloatArray(dim * dim)
                    val x = FloatArray(dim)
                    val from = t * chunk
                    val to = minOf(n, from + chunk)
                    for (i in from until to) {
                        val base = i * dim
                        for (j in 0 until dim) x[j] = vecs[base + j] - mu[j]
                        for (a in 0 until dim) {
                            val xa = x[a]
                            if (xa == 0f) continue
                            val row = a * dim
                            for (b in 0 until dim) P[row + b] += xa * x[b]
                        }
                    }
                    P
                }
            }
            for (P in jobs.map { it.await() }) {
                for (j in 0 until dim * dim) C[j] += P[j]
            }
        }
        for (j in 0 until dim * dim) C[j] /= (n - 1).toFloat()
        onProgress(0.7f)

        // ── 幂迭代 + Gram-Schmidt，求 top-k 特征向量 ──
        val rnd = Random(42)
        var V = Array(k) { FloatArray(dim) { rnd.nextFloat() * 2f - 1f } }
        normalizeOrthogonal(V)
        val W = Array(k) { FloatArray(dim) }
        val iters = 120
        for (it in 0 until iters) {
            for (c in 0 until k) {
                val v = V[c]; val w = W[c]
                for (a in 0 until dim) {
                    var s = 0f
                    val row = a * dim
                    for (b in 0 until dim) s += C[row + b] * v[b]
                    w[a] = s
                }
            }
            // 换入新基
            val tmp = V; V = W; for (c in 0 until k) tmp[c].fill(0f)
            normalizeOrthogonal(V)
            if (it % 20 == 0) onProgress(0.7f + 0.25f * it / iters)
        }
        onProgress(0.95f)

        // ── 投影 coords[i*k + c] = (x_i - μ)·v_c ──
        val coords = FloatArray(n * k)
        for (i in 0 until n) {
            val base = i * dim
            for (c in 0 until k) {
                val v = V[c]
                var s = 0f
                for (j in 0 until dim) s += (vecs[base + j] - mu[j]) * v[j]
                coords[i * k + c] = s
            }
        }
        onProgress(1f)
        coords
    }

    private fun normalizeOrthogonal(V: Array<FloatArray>) {
        for (c in V.indices) {
            val v = V[c]
            // 与已有基正交
            for (p in 0 until c) {
                val u = V[p]
                var dot = 0f
                for (j in u.indices) dot += v[j] * u[j]
                for (j in u.indices) v[j] -= dot * u[j]
            }
            var n2 = 0f
            for (x in v) n2 += x * x
            val inv = 1f / sqrt(n2.coerceAtLeast(1e-12f))
            for (j in v.indices) v[j] *= inv
        }
    }
}
