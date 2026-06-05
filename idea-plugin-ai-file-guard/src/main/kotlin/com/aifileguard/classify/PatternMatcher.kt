package com.aifileguard.classify

/**
 * Very small glob matcher supporting `*`, `?` and `**`.
 * Patterns are matched against either the file name or the project-relative path.
 */
object PatternMatcher {

    fun parsePatterns(raw: String): List<String> =
        raw.split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    /**
     * @param relativePath path relative to the project root, using '/' separators.
     * @param fileName the simple file name.
     */
    fun matchesAny(patterns: List<String>, relativePath: String, fileName: String): Boolean {
        val normalizedPath = relativePath.replace('\\', '/')
        return patterns.any { pattern ->
            val p = pattern.replace('\\', '/')
            if (p.contains('/')) {
                globToRegex(p).matches(normalizedPath) ||
                    globToRegex(p).matches(normalizedPath.substringAfterLast('/'))
            } else {
                globToRegex(p).matches(fileName)
            }
        }
    }

    private val cache = HashMap<String, Regex>()

    private fun globToRegex(glob: String): Regex = cache.getOrPut(glob) {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        // ** -> match across path separators
                        sb.append(".*")
                        i++
                        // swallow an optional trailing slash after **
                        if (i + 1 < glob.length && glob[i + 1] == '/') i++
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']' -> {
                    sb.append('\\').append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        sb.append('$')
        Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}
