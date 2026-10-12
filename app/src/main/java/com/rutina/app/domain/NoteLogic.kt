package com.rutina.app.domain

/** Reglas puras para notas del diario. */
object NoteLogic {
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val checkPrefix = Regex("(?m)^\\[[ x]] ")

    /** Palabras escritas (ignora las marcas de formato y las casillas). */
    fun wordCount(content: String): Int = word.findAll(checkPrefix.replace(content, "")).count()

    /** Marca o desmarca la casilla de la línea [index]. */
    fun toggleCheck(content: String, index: Int): String {
        val lines = content.split('\n').toMutableList()
        val l = lines.getOrNull(index) ?: return content
        lines[index] = when {
            l.startsWith("[x] ") -> "[ ] " + l.substring(4)
            l.startsWith("[ ] ") -> "[x] " + l.substring(4)
            else -> l
        }
        return lines.joinToString("\n")
    }
}
