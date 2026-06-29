package com.example.diff

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
    private val TAG_COLOR = Color(0xFF00bcd4)     // Teal
    private val ATTR_COLOR = Color(0xFF3F51B5)    // Indigo

    fun highlight(text: String, filename: String): AnnotatedString {
        val ext = filename.lowercase().substringAfterLast('.', "")
        return when (ext) {
            "json" -> highlightJson(text)
            "xml", "html", "htm" -> highlightXmlHtml(text)
            "js", "ts", "kt", "java", "css" -> highlightCode(text)
            else -> AnnotatedString(text)
        }
    }

    private fun highlightJson(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            // String literals (both keys and string values)
            val stringRegex = "\"[^\"]*\"".toRegex()
            stringRegex.findAll(text).forEach { match ->
                val isKey = match.range.last + 1 < text.length && text[match.range.last + 1] == ':'
                val color = if (isKey) ATTR_COLOR else STRING_COLOR
                addStyle(SpanStyle(color = color, fontWeight = if (isKey) FontWeight.Bold else FontWeight.Normal), match.range.first, match.range.last + 1)
            }
            // Numbers
            "-?\\d+(\\.\\d+)?".toRegex().findAll(text).forEach { match ->
                // Check if inside string
                if (!insideString(match.range.first, stringRegex, text)) {
                    addStyle(SpanStyle(color = NUMBER_COLOR), match.range.first, match.range.last + 1)
                }
            }
            // Keywords (true, false, null)
            "\\b(true|false|null)\\b".toRegex().findAll(text).forEach { match ->
                if (!insideString(match.range.first, stringRegex, text)) {
                    addStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
            }
        }
    }

    private fun highlightXmlHtml(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            // Tags: <tag ... > or </tag>
            "<[^>]+>".toRegex().findAll(text).forEach { match ->
                val tagText = match.value
                val start = match.range.first
                val end = match.range.last + 1

                // Style tag brackets and name
                addStyle(SpanStyle(color = TAG_COLOR, fontWeight = FontWeight.Bold), start, end)

                // Highlighting strings inside tag
                "\"[^\"]*\"".toRegex().findAll(tagText).forEach { strMatch ->
                    addStyle(SpanStyle(color = STRING_COLOR), start + strMatch.range.first, start + strMatch.range.last + 1)
                }
            }
        }
    }

    private fun highlightCode(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            val stringRegex = "\"[^\"]*\"|'[^']*'".toRegex()
            // String literals
            stringRegex.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = STRING_COLOR), match.range.first, match.range.last + 1)
            }
            // Keywords
            "\\b(class|fun|function|var|val|let|const|import|package|return|if|else|for|while|new|this|public|private|protected)\\b"
                .toRegex().findAll(text).forEach { match ->
                    if (!insideString(match.range.first, stringRegex, text)) {
                        addStyle(SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                    }
                }
            // Comments
            "//.*|/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/".toRegex().findAll(text).forEach { match ->
                addStyle(SpanStyle(color = COMMENT_COLOR, fontFamily = FontFamily.Monospace), match.range.first, match.range.last + 1)
            }
        }
    }

    private fun insideString(index: Int, stringRegex: Regex, text: String): Boolean {
        return stringRegex.findAll(text).any { index >= it.range.first && index <= it.range.last }
    }
}
