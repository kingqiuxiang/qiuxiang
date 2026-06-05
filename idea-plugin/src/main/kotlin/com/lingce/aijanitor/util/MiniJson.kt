package com.lingce.aijanitor.util

/**
 * Tiny dependency-free JSON parser/encoder. Good enough for talking to an
 * OpenAI-compatible chat endpoint without pulling an external JSON library
 * (which would risk classloader conflicts with the platform's bundled one).
 */
object MiniJson {

    fun escape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    fun quote(s: String): String = "\"" + escape(s) + "\""

    fun parse(input: String): Any? = Parser(input).parseValue().also { /* trailing ignored */ }

    @Suppress("UNCHECKED_CAST")
    fun asObject(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun asArray(v: Any?): List<Any?>? = v as? List<Any?>

    /** Extract the first balanced JSON object found in [text] (LLMs love to wrap JSON in prose). */
    fun extractFirstObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var escaped = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (inStr) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inStr = false
                }
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    private class Parser(val s: String) {
        var pos = 0

        fun parseValue(): Any? {
            skipWs()
            if (pos >= s.length) return null
            return when (s[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBool()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            expect('{')
            skipWs()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWs()
                when (peek()) {
                    ',' -> { pos++; continue }
                    '}' -> { pos++; break }
                    else -> throw IllegalStateException("Expected , or } at $pos")
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            expect('[')
            skipWs()
            if (peek() == ']') { pos++; return list }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> { pos++; continue }
                    ']' -> { pos++; break }
                    else -> throw IllegalStateException("Expected , or ] at $pos")
                }
            }
            return list
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < s.length) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[pos++]
                        when (e) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = s.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalStateException("Unterminated string")
        }

        private fun parseBool(): Boolean {
            return if (s.startsWith("true", pos)) { pos += 4; true }
            else if (s.startsWith("false", pos)) { pos += 5; false }
            else throw IllegalStateException("Invalid literal at $pos")
        }

        private fun parseNull(): Any? {
            if (s.startsWith("null", pos)) { pos += 4; return null }
            throw IllegalStateException("Invalid literal at $pos")
        }

        private fun parseNumber(): Any {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            val num = s.substring(start, pos)
            return num.toDoubleOrNull() ?: throw IllegalStateException("Invalid number: $num")
        }

        private fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
        private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
        private fun expect(c: Char) {
            if (peek() != c) throw IllegalStateException("Expected '$c' at $pos")
            pos++
        }
    }
}
