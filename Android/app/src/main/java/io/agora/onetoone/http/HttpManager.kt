package io.agora.onetoone.http

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.agora.onetoone.BuildConfig
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HttpManager {
    private val TAG = "HttpManager_LOG"
    private const val mBaseUrl = "https://toolbox.bj2.agoralab.co/v1"
    private val mClient = OkHttpClient()

    fun sendPostRequest() {

        // Create a JSON formatted request body
        val requestBody = RequestBody.create(MediaType.parse("application/json"), "{\"name\":\"John\", \"age\":30}")

        val request = Request.Builder()
            .url(mBaseUrl)
            .post(requestBody)
            .build()

        mClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body()?.string()
                println(responseBody)
            }
        })
    }

    // 1: RTC Token; 2: RTM Token
    fun token007(channelName: String, uid: String, onCompletion: ((String?) -> Unit)?) {
        val postBody = JSONObject()
        val types = arrayOf(1, 2)
        val jsonArray = JSONArray(types)
        try {
            postBody.put("appId", BuildConfig.AG_APP_ID)
            postBody.put("appCertificate", BuildConfig.AG_APP_CERTIFICATE)
            postBody.put("channelName", channelName)
            postBody.put("expire", 1500) // expire seconds
            postBody.put("src", "Android")
            postBody.put("ts", System.currentTimeMillis())
            postBody.put("types", jsonArray)
            postBody.put("uid", uid)
        } catch (e: JSONException) {
            onCompletion?.invoke(null)
        }
        val requestBody = RequestBody.create(null, postBody.toString())
        val request = Request.Builder()
            .url("$mBaseUrl/token/generate")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        mClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json = response.body()?.string() ?: ""
                val map = parseJson(json)
                val data = map["data"] as? Map<String, Any>
                if (data != null) {
                    val token = data["token"] as? String
                    runOnUiThread {
                        onCompletion?.invoke(token)
                    }
                } else {
                    runOnUiThread {
                        onCompletion?.invoke(null)
                    }
                }
                Log.d(TAG, "HTTP response: $json")
            }
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "HTTP error")
                runOnUiThread {
                    onCompletion?.invoke(null)
                }
            }
        })
    }

    private fun runOnUiThread(runnable: Runnable) {
        if (Thread.currentThread() === Looper.getMainLooper().thread) {
            runnable.run()
        } else {
            Handler(Looper.getMainLooper()).post(runnable)
        }
    }

    fun parseJson(json: String): Map<String, Any> {
        val gson = Gson()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(json, type)
    }

}