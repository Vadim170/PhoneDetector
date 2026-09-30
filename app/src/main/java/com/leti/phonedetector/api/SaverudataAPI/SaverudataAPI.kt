package com.leti.phonedetector.api

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Response
import com.leti.phonedetector.LOG_TAG_ERROR
import com.leti.phonedetector.LOG_TAG_VERBOSE
import com.leti.phonedetector.model.PhoneInfo
import java.nio.charset.Charset

class SaverudataAPI(val number: String, private val timeout: Int) {
    private val digits: String = normalizeNumber(number)
    var urlPath: String
    private val url: String = "https://data.intelx.io/saverudata/db2/dbpn/" // https://data.intelx.io/saverudata/db2/dbpn/79/27/51/92.csv

    init {
        urlPath = convertPhoneToAPI(digits)
    }

    private fun normalizeNumber(number: String): String {
        val onlyDigits = number.filter { it.isDigit() }
        return if (onlyDigits.length == 11 && onlyDigits.startsWith("8")) "7" + onlyDigits.substring(1) else onlyDigits
    }

    // Files are grouped by the number without its last 3 digits: 79275192999 -> 79/27/51/92
    private fun convertPhoneToAPI(number: String): String {
        val prefix = number.dropLast(3)
        return prefix.chunked(2).take(4).joinToString("/")
    }

    fun getUser(): PhoneInfo {
        return NetworkTask().execute().get()
    }

    fun findInfo(): PhoneInfo {
        if (digits.length != 11) return PhoneInfo(number = number)
        val (_, response, _) = Fuel.get("$url$urlPath.csv").timeout(timeout * 2000 + 1).response()
        Log.d(LOG_TAG_VERBOSE, "${response.statusCode}, ${response.data.toString(Charset.defaultCharset())}")
        if (response.statusCode != 200) return PhoneInfo(number = number)
        return parseResponse(response)
    }

    // CSV has a header row: phone_number,type,<source fields...>; one number may have several rows
    private fun parseResponse(response: Response): PhoneInfo {
        val lines = response.data.toString(Charsets.UTF_8).lines()
        if (lines.isEmpty()) return PhoneInfo(number = number)

        val header = parseCsvLine(lines[0].trimStart('\uFEFF'))
        val skipColumns = setOf("phone_number", "type")
        val values = LinkedHashSet<String>()
        lines.drop(1)
            .filter { it.startsWith("$digits,") }
            .forEach { line ->
                parseCsvLine(line).forEachIndexed { i, value ->
                    val column = header.getOrNull(i) ?: ""
                    if (column !in skipColumns && !column.contains("password", ignoreCase = true) && value.any { it.isLetterOrDigit() }) values.add(value.trim())
                }
            }
        if (values.isEmpty())
            return PhoneInfo(number = number)

        return PhoneInfo(
            values.joinToString(", "),
            number)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(current.toString()); current.setLength(0) }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    @SuppressLint("StaticFieldLeak")
    inner class NetworkTask : AsyncTask<String, Void, PhoneInfo>() {

        override fun doInBackground(vararg parts: String): PhoneInfo {
            return try {
                this@SaverudataAPI.findInfo()
            } catch (e: Exception) {
                Log.d(LOG_TAG_ERROR, "Error on API: $e")
                PhoneInfo(number = number)
            }
        }
    }
}
