package com.cijian.app.core

/**
 * 中文分词：双向最大匹配（FMM/BMM 择优），词典直接复用词向量词表（143,613 词）。
 * 不引第三方依赖；对"粘贴一句话 → 挑词玩向量"的场景足够好用。
 */
class Segmenter(private val vocab: Set<String>) {

    enum class Kind { WORD, LATIN, PUNCT }
    data class Token(val text: String, val inVocab: Boolean, val kind: Kind)

    private val maxLen = 10

    fun segment(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                isAsciiWord(c) -> {
                    var j = i
                    while (j < n && isAsciiWord(text[j])) j++
                    out.add(Token(text.substring(i, j), true, Kind.LATIN))
                    i = j
                }
                isPunct(c) -> {
                    out.add(Token(c.toString(), false, Kind.PUNCT))
                    i++
                }
                else -> {
                    // 找连续 CJK 片段
                    var j = i
                    while (j < n && !isAsciiWord(text[j]) && !isPunct(text[j]) && !text[j].isWhitespace()) j++
                    val run = text.substring(i, j)
                    val f = fmm(run)
                    val b = bmm(run)
                    val chosen = if (b.size < f.size) b else f
                    for (w in chosen) {
                        out.add(Token(w, vocab.contains(w), Kind.WORD))
                    }
                    i = j
                }
            }
        }
        return out
    }

    private fun isAsciiWord(c: Char): Boolean =
        c.code < 0x2E80 && (c.isLetterOrDigit() || c == '_' || c == '-')

    // 引号类标点用 Unicode 转义，避免在字面量中出现裸引号
    private val punctChars =
        "，。！？；：、（）《》【】…—·｡\u201C\u201D\u2018\u2019,.;:!?()[]{}<>\"'/\\|@#\$%^&*+=~`".toSet()

    private fun isPunct(c: Char): Boolean {
        if (c.isWhitespace()) return false
        if (c.code in 0x3000..0x303F) return true   // CJK 符号区
        if (c.code in 0xFF00..0xFFEF && !c.isLetterOrDigit()) return true  // 全角符号区
        return c in punctChars
    }

    /** 前向最大匹配 */
    private fun fmm(run: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < run.length) {
            var len = minOf(maxLen, run.length - i)
            while (len > 1 && !vocab.contains(run.substring(i, i + len))) len--
            out.add(run.substring(i, i + len))
            i += len
        }
        return out
    }

    /** 后向最大匹配 */
    private fun bmm(run: String): List<String> {
        val out = mutableListOf<String>()
        var j = run.length
        while (j > 0) {
            var len = minOf(maxLen, j)
            while (len > 1 && !vocab.contains(run.substring(j - len, j))) len--
            out.add(run.substring(j - len, j))
            j -= len
        }
        out.reverse()
        return out
    }
}
