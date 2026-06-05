package com.lingce.aicleaner.core

/**
 * 轻量 glob 匹配，支持 `*`、`**`、`?`。大小写不敏感。
 * - 不含 `/` 的模式：只匹配文件名（basename）。
 * - 含 `/` 的模式：匹配相对路径。
 */
object GlobMatcher {

    fun parsePatterns(raw: String): List<String> =
        raw.split(',', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun matchesAny(patterns: List<String>, fileName: String, relativePath: String): Boolean =
        patterns.any { matches(it, fileName, relativePath) }

    fun matches(pattern: String, fileName: String, relativePath: String): Boolean {
        val p = pattern.trim().removePrefix("./")
        if (p.isEmpty()) return false
        val target = if (p.contains('/')) relativePath.replace('\\', '/') else fileName
        return regexFor(p).matches(target.lowercase())
    }

    private val cache = HashMap<String, Regex>()

    private fun regexFor(glob: String): Regex = cache.getOrPut(glob.lowercase()) {
        Regex(globToRegex(glob.lowercase()))
    }

    private fun globToRegex(glob: String): String {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> {
                    val doubleStar = i + 1 < glob.length && glob[i + 1] == '*'
                    if (doubleStar) {
                        // ** 匹配任意（含 /）。吃掉可能跟随的 /
                        sb.append(".*")
                        i++
                        if (i + 1 < glob.length && glob[i + 1] == '/') i++
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' ->
                    sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        sb.append('$')
        return sb.toString()
    }
}
