package com.rutina.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.ui.theme.Accent

/*
 * Formato sencillo para las notas (guardado como texto plano, legible en cualquier lado):
 *   **negrita**   _cursiva_   ~~tachado~~   ==resaltado==
 *   "- " lista    "[ ] " / "[x] " casilla    "# " título
 */

data class MdSpan(val start: Int, val end: Int, val len: Int, val style: SpanStyle)

private val HighlightColor = Color(0x66F6D98E)

private val inlineMarkers: List<Pair<String, SpanStyle>> = listOf(
    "**" to SpanStyle(fontWeight = FontWeight.Bold),
    "~~" to SpanStyle(textDecoration = TextDecoration.LineThrough),
    "==" to SpanStyle(background = HighlightColor),
    "_" to SpanStyle(fontStyle = FontStyle.Italic),
)

/** Prefijos de lista que continúan al presionar Enter. */
private val listPrefixes = listOf("[ ] ", "[x] ", "- ")

fun listPrefixOf(line: String): String? = listPrefixes.firstOrNull { line.startsWith(it) }

/** Encuentra pares de marcas de formato dentro de cada línea. */
fun findSpans(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    val closers = HashMap<Int, Int>()
    var i = 0
    while (i < text.length) {
        val cl = closers[i]
        if (cl != null) { i += cl; continue }
        var advanced = false
        for ((m, style) in inlineMarkers) {
            if (!text.startsWith(m, i)) continue
            // "_" solo cuenta al inicio de palabra (para no romper snake_case)
            if (m == "_" && i > 0 && text[i - 1].isLetterOrDigit()) break
            val lineEnd = text.indexOf('\n', i).let { if (it < 0) text.length else it }
            val close = findClose(text, m, i + m.length, lineEnd)
            if (close >= i + m.length + 1) {
                spans += MdSpan(i, close, m.length, style)
                closers[close] = m.length
                i += m.length
                advanced = true
            }
            break
        }
        if (!advanced) i++
    }
    return spans
}

private fun findClose(text: String, m: String, from: Int, lineEnd: Int): Int {
    var c = text.indexOf(m, from)
    while (c >= 0 && c + m.length <= lineEnd) {
        if (m != "_" || c + 1 >= text.length || !text[c + 1].isLetterOrDigit()) return c
        c = text.indexOf(m, c + 1)
    }
    return -1
}

/** Texto de una línea con el formato aplicado y las marcas ocultas. */
private val urlRegex = Regex("(https?://|www\\.)[^\\s]+")

/** Vuelve tocables los enlaces (se abren en tu navegador; Rutina no se conecta). */
private fun withLinks(a: AnnotatedString): AnnotatedString {
    val found = urlRegex.findAll(a.text).toList()
    if (found.isEmpty()) return a
    return buildAnnotatedString {
        append(a)
        found.forEach { m ->
            val url = m.value.trimEnd('.', ',', ')', ';')
            val target = if (url.startsWith("www.")) "https://$url" else url
            addLink(
                androidx.compose.ui.text.LinkAnnotation.Url(
                    target,
                    androidx.compose.ui.text.TextLinkStyles(SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)),
                ),
                m.range.first, m.range.first + url.length,
            )
        }
    }
}

fun renderInline(line: String): AnnotatedString = withLinks(renderInlinePlain(line))

private fun renderInlinePlain(line: String): AnnotatedString {
    val spans = findSpans(line)
    if (spans.isEmpty()) return AnnotatedString(line)
    val skip = BooleanArray(line.length)
    spans.forEach { s ->
        for (k in 0 until s.len) { skip[s.start + k] = true; skip[s.end + k] = true }
    }
    val map = IntArray(line.length + 1)
    val sb = StringBuilder()
    for (k in line.indices) {
        map[k] = sb.length
        if (!skip[k]) sb.append(line[k])
    }
    map[line.length] = sb.length
    return buildAnnotatedString {
        append(sb.toString())
        spans.forEach { s ->
            val a = map[s.start + s.len]
            val b = map[s.end]
            if (b > a) addStyle(s.style, a, b)
        }
    }
}

/** En el editor: aplica el formato mientras escribes y atenúa las marcas. */
class MarkdownTransformation(private val markerColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val marker = SpanStyle(color = markerColor)
        val out = buildAnnotatedString {
            append(raw)
            findSpans(raw).forEach { s ->
                addStyle(s.style, s.start, s.end + s.len)
                addStyle(marker, s.start, s.start + s.len)
                addStyle(marker, s.end, s.end + s.len)
            }
            urlRegex.findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = Accent, textDecoration = TextDecoration.Underline), m.range.first, m.range.last + 1)
            }
            var lineStart = 0
            raw.split('\n').forEach { line ->
                val p = listPrefixOf(line) ?: if (line.startsWith("# ")) "# " else null
                if (p != null) addStyle(marker, lineStart, lineStart + p.length)
                if (line.startsWith("# ")) {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp), lineStart + 2, lineStart + line.length)
                }
                if (line.startsWith("[x] ")) {
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = markerColor), lineStart + 4, lineStart + line.length)
                }
                lineStart += line.length + 1
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/**
 * Muestra una nota con su formato. Si [onToggleCheck] no es nulo,
 * las casillas se pueden marcar con un toque.
 */
@Composable
fun NoteBody(
    content: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onToggleCheck: ((Int) -> Unit)? = null,
) {
    val lines = content.trimEnd().split('\n')
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        lines.take(maxLines).forEachIndexed { idx, line ->
            when {
                line.startsWith("[ ] ") || line.startsWith("[x] ") -> {
                    val checked = line.startsWith("[x] ")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(
                            if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = if (checked) "Hecho" else "Pendiente",
                            tint = if (checked) Accent else dim,
                            modifier = Modifier.size(22.dp)
                                .clickable(enabled = onToggleCheck != null) { onToggleCheck?.invoke(idx) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            renderInline(line.substring(4)),
                            color = if (checked) dim else Color.Unspecified,
                            textDecoration = if (checked) TextDecoration.LineThrough else null,
                        )
                    }
                }
                line.startsWith("- ") -> Row {
                    Text("•", Modifier.width(18.dp).padding(start = 4.dp))
                    Text(renderInline(line.substring(2)))
                }
                line.startsWith("# ") -> Text(renderInline(line.substring(2)), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                line.isEmpty() -> Spacer(Modifier.height(8.dp))
                else -> Text(renderInline(line))
            }
        }
        if (lines.size > maxLines) Text("…", color = dim)
    }
}
