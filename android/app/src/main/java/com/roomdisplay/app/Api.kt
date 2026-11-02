package com.roomdisplay.app
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    fun post(server: String, path: String, body: JSONObject, deviceId: String="", token: String=""): JSONObject {
        val conn=URL(server.trimEnd('/')+path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod="POST";conn.connectTimeout=5000;conn.readTimeout=5000;conn.doOutput=true
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
            if(deviceId.isNotEmpty()) { conn.setRequestProperty("X-Device-Id",deviceId);conn.setRequestProperty("X-Device-Token",token) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode
            val text=(if(status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json=try { JSONObject(text) } catch(_: Exception) { JSONObject() }
            if(status !in 200..299) throw IllegalStateException(json.optString("message","服务器返回 HTTP $status"))
            return json
        } finally { conn.disconnect() }
    }
}
