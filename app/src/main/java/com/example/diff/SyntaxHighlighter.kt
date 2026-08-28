package com.example.diff

import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object SyntaxHighlighter {

    private val KEYWORD_COLOR = Color(0xFF9C27B0) // Purple
    private val STRING_COLOR = Color(0xFF4CAF50)  // Green
    private val NUMBER_COLOR = Color(0xFFFF9800)  // Orange
    private val COMMENT_COLOR = Color(0xFF9E9E9E) // Gray
    private val TAG_COLOR = Color(0xFF00BCD4)     // Teal
    private val ATTR_COLOR = Color(0xFF3F51B5)    // Indigo

    // High-performance LRU Cache for rendered syntax lines
    private val cache = LruCache<String, AnnotatedString>(8192)

    // Precompiled static Regex instances
    private val JSON_STRING_REGEX = "\"[^\"]*\"".toRegex()
    private val JSON_NUMBER_REGEX = "-?\\d+(\\.\\d+)?".toRegex()
    private val JSON_KEYWORD_REGEX = "\\b(true|false|null)\\b".toRegex()

    private val XML_TAG_REGEX = "<[^>]+>".toRegex()
    private val XML_ATTR_STRING_REGEX = "\"[^\"]*\"".toRegex()

    private val CODE_STRING_REGEX = "\"[^\"]*\"|'[^']*'".toRegex()
    private val CODE_KEYWORD_REGEX = "\\b(class|fun|function|var|val|let|const|import|package|return|if|else|for|while|new|this|public|private|protected)\\b".toRegex()
    private val CODE_COMMENT_REGEX = "//.*|/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/".toRegex()

    // Smali / Dex bytecode highlighters
    private val SMALI_DIRECTIVE_REGEX = "\\.(method|end method|registers|locals|field|super|class|implements|source|annotation|end annotation|line|parameter|prologue|local|catch|catchall|array-data|end array-data|enum|restart local)\\b".toRegex()
    private val SMALI_INSTRUCTION_REGEX = "\\b(invoke-virtual|invoke-direct|invoke-static|invoke-super|invoke-interface|invoke-custom|invoke-polymorphic|invoke-virtual/range|invoke-direct/range|invoke-static/range|invoke-super/range|invoke-interface/range|const-string|const-class|const-wide|const/4|const/16|const|const-wide/16|const-wide/32|const-wide/high16|const/high16|move-result|move-result-object|move-result-wide|move-exception|move|move/from16|move/16|move-wide|move-object|return|return-void|return-object|return-wide|if-eq|if-ne|if-lt|if-ge|if-gt|if-le|if-eqz|if-nez|if-ltz|if-gez|if-gtz|if-lez|goto|goto/16|goto/32|new-instance|new-array|check-cast|instance-of|array-length|fill-array-data|aget|aget-wide|aget-object|aget-boolean|aget-byte|aget-char|aget-short|aput|aput-wide|aput-object|aput-boolean|aput-byte|aput-char|aput-short|iget|iget-wide|iget-object|iget-boolean|iget-byte|iget-char|iget-short|iput|iput-wide|iput-object|iput-boolean|iput-byte|iput-char|iput-short|sget|sget-wide|sget-object|sget-boolean|sget-byte|sget-char|sget-short|sput|sput-wide|sput-object|sput-boolean|sput-byte|sput-char|sput-short|throw|monitor-enter|monitor-exit|packed-switch|sparse-switch|nop|public|private|protected|static|final|abstract|synthetic|native|synchronized|bridge|transient|volatile)\\b".toRegex()
    private val SMALI_LABEL_REGEX = ":[a-zA-Z0-9_]+".toRegex()
    private val SMALI_REGISTER_REGEX = "\\b[vp][0-9]+\\b".toRegex()
    private val SMALI_COMMENT_REGEX = "#.*".toRegex()
    private val SMALI_NUMBER_REGEX = "-?0x[0-9a-fA-F]+L?|-?\\d+L?".toRegex()

    fun highlight(text: String, filename: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")
        val ext = filename.lowercase().substringAfterLast('.', "")
        if (ext !in setOf("json", "xml", "html", "htm", "js", "ts", "kt", "java", "css", "smali", "dex")) {
            return AnnotatedString(text)
        }

        val cacheKey = "$ext:$text"
        val cached = cache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val result = when (ext) {
            "json" -> highlightJson(text)
            "xml", "html", "htm" -> highlightXmlHtml(text)
            "smali", "dex" -> highlightSmali(text)
            "js", "ts", "kt", "java", "css" -> highlightCode(text)
            else -> AnnotatedString(text)
        }

        cache.put(cacheKey, result)
        return result
    }

    private fun highlightSmali(text: String): AnnotatedString {
        val stringMatches = CODE_STRING_REGEX.findAll(text).toList()
        val commentMatches = SMALI_COMMENT_REGEX.findAll(text).toList()
        val ignoreRanges = stringMatches + commentMatches

        return buildAnnotatedString {
            append(text)

            // Directives (.method, .registers, etc.)
            SMALI_DIRECTIVE_REGEX.findAll(text).forEach { match ->
                if (!isInsideRanges(match.range.first, ignoreRanges)) {
                    addStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
            }

            // Instructions (invoke-virtual, const-string, if-nez, etc.)
            SMALI_INSTRUCTION_REGEX.findAll(text).forEach { match ->
                if (!isInsideRanges(match.range.first, ignoreRanges)) {
                    addStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
            }

            // Labels (:label_1, :goto_0, etc.)
            SMALI_LABEL_REGEX.findAll(text).forEach { match ->
                if (!isInsideRanges(match.range.first, ignoreRanges)) {
                    addStyle(SpanStyle(color = TAG_COLOR, fontWeight = FontWeight.Medium), match.range.first, match.range.last + 1)
                }
            }

            // Registers (v0, v1, p0, p1, etc.)
            SMALI_REGISTER_REGEX.findAll(text).forEach { match ->
                if (!isInsideRanges(match.range.first, ignoreRanges)) {
                    addStyle(SpanStyle(color = ATTR_COLOR, fontWeight = FontWeight.Normal), match.range.first, match.range.last + 1)
                }
            }

            // Numbers & Hex
            SMALI_NUMBER_REGEX.findAll(text).forEach { match ->
                if (!isInsideRanges(match.range.first, ignoreRanges)) {
                    addStyle(SpanStyle(color = NUMBER_COLOR), match.range.first, match.range.last + 1)
                }
            }

            // String literals
            stringMatches.forEach { match ->
                addStyle(SpanStyle(color = STRING_COLOR), match.range.first, match.range.last + 1)
            }

            // Comments
            commentMatches.forEach { match ->
                addStyle(SpanStyle(color = COMMENT_COLOR, fontFamily = FontFamily.Monospace), match.range.first, match.range.last + 1)
            }
        }
    }

    private fun highlightJson(text: String): AnnotatedString {
        val stringMatches = JSON_STRING_REGEX.findAll(text).toList()
        return buildAnnotatedString {
            append(text)
            // String literals
            stringMatches.forEach { match ->
                val isKey = match.range.last + 1 < text.length && text[match.range.last + 1] == ':'
                val color = if (isKey) ATTR_COLOR else STRING_COLOR
                addStyle(SpanStyle(color = color, fontWeight = if (isKey) FontWeight.Bold else FontWeight.Normal), match.range.first, match.range.last + 1)
            }
            // Numbers
            JSON_NUMBER_REGEX.findAll(text).forEach { match ->
                if (!isInsideRanges(match.range.first, stringMatches)) {
                    addStyle(SpanStyle(color = NUMBER_COLOR), match.range.first, match.range.last + 1)
                }
            }
            // Keywords
            JSON_KEYWORD_REGEX.findAll(text).forEach { match ->
                if (!isInsideRanges(match.range.first, stringMatches)) {
                    addStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
            }
        }
    }

    private fun highlightXmlHtml(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            XML_TAG_REGEX.findAll(text).forEach { match ->
                val tagText = match.value
                val start = match.range.first
                val end = match.range.last + 1

                addStyle(SpanStyle(color = TAG_COLOR, fontWeight = FontWeight.Bold), start, end)

                XML_ATTR_STRING_REGEX.findAll(tagText).forEach { strMatch ->
                    addStyle(SpanStyle(color = STRING_COLOR), start + strMatch.range.first, start + strMatch.range.last + 1)
                }
            }
        }
    }

    private fun highlightCode(text: String): AnnotatedString {
        val stringMatches = CODE_STRING_REGEX.findAll(text).toList()
        return buildAnnotatedString {
            append(text)
            // String literals
            stringMatches.forEach { match ->
                addStyle(SpanStyle(color = STRING_COLOR), match.range.first, match.range.last + 1)
            }
            // Keywords
            CODE_KEYWORD_REGEX.findAll(text).forEach { match ->
                if (!isInsideRanges(match.range.first, stringMatches)) {
                    addStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
            }
            // Comments
            CODE_COMMENT_REGEX.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = COMMENT_COLOR, fontFamily = FontFamily.Monospace), match.range.first, match.range.last + 1)
            }
        }
    }

    private fun isInsideRanges(index: Int, matches: List<MatchResult>): Boolean {
        for (match in matches) {
            if (index in match.range) return true
        }
        return false
    }
}
