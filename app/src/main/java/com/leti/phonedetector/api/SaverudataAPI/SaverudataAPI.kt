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
    var urlPath: String
    private val url: String = "https://saverudata.info/db/dbpn/" // https://saverudata.info/db/dbpn/79/27/51/92.csv

    init {
        urlPath = convertPhoneToAPI(number)
    }

    private fun convertPhoneToAPI(number: String): String {
        val stringBuffer = StringBuffer(number)
        stringBuffer.insert(2, "/")
        stringBuffer.insert(5, "/")
        stringBuffer.insert(8, "/")
        return  stringBuffer.substring(0, 11)
    }

    fun getUser(): PhoneInfo {
        return NetworkTask().execute().get()
    }

    fun findInfo(): PhoneInfo {
        val (_, response, _) = Fuel.get("$url$urlPath.csv").timeout(timeout * 2000 + 1).response()
        Log.d(LOG_TAG_VERBOSE, "${response.statusCode}, ${response.data.toString(Charset.defaultCharset())}")
        return parseResponse(response)
    }

    private fun parseResponse(response: Response): PhoneInfo {
        val data = response.data.toString(Charset.defaultCharset())

        val indexStartDesc = data.indexOf(number)
        var indexEndDesc = data.lastIndexOf(number)
        indexEndDesc = data.indexOf("\n", indexEndDesc+1)
        if(indexStartDesc == indexEndDesc) // == -1
            return PhoneInfo()

        val description = data
            .substring(indexStartDesc, indexEndDesc)
            .replace(",,", "")
            .replace(number, "")
        return PhoneInfo(
            description,
            number)
    }

    private fun convertPhoneDefault(number: String): String {
        return if (number.startsWith("8")) {
            number.replace("^8".toRegex(), "+7")
        } else {
            number
        }
    }

    @SuppressLint("StaticFieldLeak")
    inner class NetworkTask : AsyncTask<String, Void, PhoneInfo>() {

        override fun doInBackground(vararg parts: String): PhoneInfo {
            return try {
                this@SaverudataAPI.findInfo()
            } catch (e: Exception) {
                Log.d(LOG_TAG_ERROR, "Error on API: $e")
                PhoneInfo(
                    number = convertPhoneDefault(urlPath).replace("+7", "7")
                )
            }
        }
    }
}
