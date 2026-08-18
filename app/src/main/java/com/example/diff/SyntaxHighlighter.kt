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
    private val cache = LruCache<String, AnnotatedString>(4096)

    // Precompiled static Regex instances
    private val JSON_STRING_REGEX = "\"[^\"]*\"".toRegex()
    private val JSON_NUMBER_REGEX = "-?\\d+(\\.\\d+)?".toRegex()
    private val JSON_KEYWORD_REGEX = "\\b(true|false|null)\\b".toRegex()

    private val XML_TAG_REGEX = "<[^>]+>".toRegex()
    private val XML_ATTR_STRING_REGEX = "\"[^\"]*\"".toRegex()

    private val CODE_STRING_REGEX = "\"[^\"]*\"|'[^']*'".toRegex()
    private val CODE_KEYWORD_REGEX = "\\b(class|fun|function|var|val|let|const|import|package|return|if|else|for|while|new|this|public|private|protected)\\b".toRegex()
    private val CODE_COMMENT_REGEX = "//.*|/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/".toRegex()

    fun highlight(text: String, filename: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")
        val ext = filename.lowercase().substringAfterLast('.', "")
        if (ext !in setOf("json", "xml", "html", "htm", "js", "ts", "kt", "java", "css", "smali")) {
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
            "js", "ts", "kt", "java", "css", "smali" -> highlightCode(text)
            else -> AnnotatedString(text)
        }

        cache.put(cacheKey, result)
        return result
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
