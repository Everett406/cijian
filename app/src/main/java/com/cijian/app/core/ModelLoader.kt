package com.cijian.app.core

import java.io.BufferedInputStream
import java.io.InputStream

/**
 * 腾讯 AI Lab 词向量 light 版解析器。
 *
 * 文件格式（经实际验证）：
 *  - 首行文本头："143613 200\n"
 *  - 之后为连续记录流：词(UTF-8 字节) + 0x20(空格) + 200×4 字节 float32(小端)，记录之间【无】分隔符。
 *    词向量二进制中可能含 0x20/0x0A，绝不能按"行"读取，必须按"词结束空格 + 定长向量"推进。
 */
object ModelLoader {
    const val MODEL_ASSET = "models/light_Tencent_AILab_ChineseEmbedding.bin"
    const val PCA2_ASSET = "models/pca2d.f32"
    const val PCA3_ASSET = "models/pca3d.f32"

    class Parsed(val words: Array<String>, val vectors: FloatArray, val dim: Int, val count: Int)

    fun parse(input: InputStream, onProgress: (Float) -> Unit = {}): Parsed {
        val ins = BufferedInputStream(input, 1 shl 16)

        // ── 文本头 ──
        val hb = StringBuilder()
        while (true) {
            val b = ins.read()
            if (b == 0x0A || b == -1) break
            hb.append(b.toChar())
        }
        val parts = hb.toString().trim().split(Regex("\\s+"))
        require(parts.size == 2) { "模型头异常: $hb" }
        val n = parts[0].toInt()
        val dim = parts[1].toInt()

        val words = Array(n) { "" }
        val vecs = FloatArray(n * dim)
        val rec = 4 * dim
        val vecBuf = ByteArray(rec)
        val wordBuf = ByteArray(128)
        val progressEvery = (n / 20).coerceAtLeast(1)
        var vi = 0

        for (i in 0 until n) {
            // ── 词：读字节直到空格 ──
            var wl = 0
            while (true) {
                val b = ins.read()
                if (b == 0x20 || b == -1) break
                if (wl < wordBuf.size) wordBuf[wl] = b.toByte()
                wl++
            }
            words[i] = String(wordBuf, 0, wl.coerceAtMost(wordBuf.size), Charsets.UTF_8)

            // ── 定长读 200×float32 ──
            var off = 0
            while (off < rec) {
                val r = ins.read(vecBuf, off, rec - off)
                if (r < 0) throw java.io.EOFException("模型提前结束 @ $i")
                off += r
            }
            for (j in 0 until dim) {
                val o = j shl 2
                val bits = (vecBuf[o].toInt() and 0xFF) or
                        ((vecBuf[o + 1].toInt() and 0xFF) shl 8) or
                        ((vecBuf[o + 2].toInt() and 0xFF) shl 16) or
                        ((vecBuf[o + 3].toInt() and 0xFF) shl 24)
                vecs[vi + j] = Float.fromBits(bits)
            }
            vi += dim
            if (i % progressEvery == 0) onProgress(i.toFloat() / n)
        }
        onProgress(1f)
        return Parsed(words, vecs, dim, n)
    }

    /** 读取 PCA 坐标资产: magic(4B) + n(4B) + k(4B) + n*k×float32(LE)。资产缺失返回 null。 */
    fun readPca(context: android.content.Context, asset: String, k: Int, magic: String): FloatArray? {
        return try {
            context.assets.open(asset).use { ins ->
                val head = ByteArray(12)
                var off = 0
                while (off < 12) off += ins.read(head, off, 12 - off)
                val mg = String(head, 0, 4, Charsets.US_ASCII)
                if (mg != magic) return null
                val n = leInt(head, 4)
                val kk = leInt(head, 8)
                if (kk != k) return null
                val data = ByteArray(n * k * 4)
                var o = 0
                while (o < data.size) {
                    val r = ins.read(data, o, data.size - o)
                    if (r < 0) break
                    o += r
                }
                val out = FloatArray(n * k)
                for (i in out.indices) out[i] = Float.fromBits(leInt(data, i * 4))
                out
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun leInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
                ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
