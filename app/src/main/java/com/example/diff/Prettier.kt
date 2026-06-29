package com.example.diff

import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

object Prettier {
    
    fun formatJson(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            if (trimmed.startsWith("{")) {
                val jsonObject = JSONObject(trimmed)
                jsonObject.toString(4)
            } else if (trimmed.startsWith("[")) {
                val jsonArray = JSONArray(trimmed)
                jsonArray.toString(4)
            } else {
                trimmed
            }
        } catch (e: Exception) {
            // Return raw trimmed if failed
            trimmed
        }
    }

    fun formatXml(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val dbf = TransformerFactory.newInstance()
            val transformer = dbf.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            
            val result = StreamResult(StringWriter())
            val source = StreamSource(StringReader(trimmed))
            transformer.transform(source, result)
            result.writer.toString().trim()
        } catch (e: Exception) {
            trimmed
        }
    }

    fun formatAuto(filename: String, content: String): String {
        val ext = filename.lowercase().substringAfterLast('.', "")
        return when (ext) {
            "json" -> formatJson(content)
            "xml", "html", "xhtml", "svg" -> formatXml(content)
            else -> content
        }
    }
}
