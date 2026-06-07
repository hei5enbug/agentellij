package com.agentellij.backend

object CustomArgsParser {
    fun parse(input: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var argStarted = false

        fun flush() {
            if (argStarted) {
                args += current.toString()
                current.clear()
                argStarted = false
            }
        }

        for (char in input) {
            when {
                escaping -> {
                    current.append(char)
                    argStarted = true
                    escaping = false
                }
                char == '\\' -> {
                    escaping = true
                    argStarted = true
                }
                quote != null -> {
                    if (char == quote) quote = null else current.append(char)
                    argStarted = true
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    argStarted = true
                }
                char.isWhitespace() -> flush()
                else -> {
                    current.append(char)
                    argStarted = true
                }
            }
        }

        if (escaping) current.append('\\')
        flush()
        return args
    }
}
