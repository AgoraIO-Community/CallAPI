package io.agora.onetoone.report

import android.util.Log
import io.agora.rtc2.Constants
import io.agora.rtc2.RtcEngine
import org.json.JSONObject
import java.util.HashMap

enum class APIType(val value: Int) {
    KTV(1),             // Karaoke
    CALL(2),            // Call connection
    BEAUTY(3),          // Beauty filter
    VIDEO_LOADER(4),    // Instant loading and switching
    PK(5),              // Team battle
    VIRTUAL_SPACE(6),   //
    SCREEN_SPACE(7),    // Screen sharing
    AUDIO_SCENARIO(8)   // Audio
}

enum class ApiEventType(val value: Int) {
    API(0),
    COST(1),
    CUSTOM(2)
}

object ApiEventKey {
    const val TYPE = "type"
    const val DESC = "desc"
    const val API_VALUE = "apiValue"
    const val TIMESTAMP = "ts"
    const val EXT = "ext"
}

object ApiCostEvent {
    const val CHANNEL_USAGE = "channelUsage"                 // Channel usage duration
    const val FIRST_FRAME_ACTUAL = "firstFrameActual"        // Actual duration of the first frame
    const val FIRST_FRAME_PERCEIVED = "firstFramePerceived"  // Perceived duration of the first frame
}

class APIReporter(
    private val type: APIType,
    private val version: String,
    private val rtcEngine: RtcEngine
) {
    private val tag = "APIReporter"
    private val messageId = "agora:scenarioAPI"
    private val durationEventStartMap = HashMap<String, Long>()
    private val category = "${type.value}_Android_$version"

    init {
        configParameters()
    }

    // Report normal scenario API
    fun reportFuncEvent(name: String, value: Map<String, Any>, ext: Map<String, Any>) {
        Log.d(tag, "reportFuncEvent: $name value: $value ext: $ext")
        val eventMap = mapOf(ApiEventKey.TYPE to ApiEventType.API.value, ApiEventKey.DESC to name)
        val labelMap = mapOf(ApiEventKey.API_VALUE to value, ApiEventKey.TIMESTAMP to getCurrentTs(), ApiEventKey.EXT to ext)
        val event = convertToJSONString(eventMap) ?: ""
        val label = convertToJSONString(labelMap) ?: ""
        rtcEngine.sendCustomReportMessage(messageId, category, event, label, 0)
    }

    fun startDurationEvent(name: String) {
        Log.d(tag, "startDurationEvent: $name")
        durationEventStartMap[name] = getCurrentTs()
    }

    fun endDurationEvent(name: String, ext: Map<String, Any>) {
        Log.d(tag, "endDurationEvent: $name")
        val beginTs = durationEventStartMap[name] ?: return
        durationEventStartMap.remove(name)
        val ts = getCurrentTs()
        val cost = (ts - beginTs).toInt()

        innerReportCostEvent(ts, name, cost, ext)
    }

    // Report duration timing information
    fun reportCostEvent(name: String, cost: Int, ext: Map<String, Any>) {
        durationEventStartMap.remove(name)
        innerReportCostEvent(
            ts = getCurrentTs(),
            name = name,
            cost = cost,
            ext = ext
        )
    }

    // Report custom information
    fun reportCustomEvent(name: String, ext: Map<String, Any>) {
        Log.d(tag, "reportCustomEvent: $name ext: $ext")
        val eventMap = mapOf(ApiEventKey.TYPE to ApiEventType.CUSTOM.value, ApiEventKey.DESC to name)
        val labelMap = mapOf(ApiEventKey.TIMESTAMP to getCurrentTs(), ApiEventKey.EXT to ext)
        val event = convertToJSONString(eventMap) ?: ""
        val label = convertToJSONString(labelMap) ?: ""
        rtcEngine.sendCustomReportMessage(messageId, category, event, label, 0)
    }

    fun writeLog(content: String, level: Int) {
        rtcEngine.writeLog(level, content)
    }

    fun cleanCache() {
        durationEventStartMap.clear()
    }

    // ---------------------- private ----------------------

    private fun configParameters() {
        //rtcEngine.setParameters("{\"rtc.qos_for_test_purpose\": true}") // For test environment
        // Data reporting
        rtcEngine.setParameters("{\"rtc.direct_send_custom_event\": true}")
        // Log writing
        rtcEngine.setParameters("{\"rtc.log_external_input\": true}")
    }

    private fun getCurrentTs(): Long {
        return System.currentTimeMillis()
    }

    private fun innerReportCostEvent(ts: Long, name: String, cost: Int, ext: Map<String, Any>) {
        Log.d(tag, "reportCostEvent: $name cost: $cost ms ext: $ext")
        writeLog("reportCostEvent: $name cost: $cost ms", Constants.LOG_LEVEL_INFO)
        val eventMap = mapOf(ApiEventKey.TYPE to ApiEventType.COST.value, ApiEventKey.DESC to name)
        val labelMap = mapOf(ApiEventKey.TIMESTAMP to ts, ApiEventKey.EXT to ext)
        val event = convertToJSONString(eventMap) ?: ""
        val label = convertToJSONString(labelMap) ?: ""
        rtcEngine.sendCustomReportMessage(messageId, category, event, label, cost)
    }

    private fun convertToJSONString(dictionary: Map<String, Any>): String? {
        return try {
            JSONObject(dictionary).toString()
        } catch (e: Exception) {
            writeLog("[$tag]convert to json fail: $e dictionary: $dictionary", Constants.LOG_LEVEL_WARNING)
            null
        }
    }
}