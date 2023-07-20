package io.agora.onetoone.utils

import android.text.TextUtils
import android.util.Log
import io.agora.media.RtcTokenBuilder
import io.agora.onetoone.BuildConfig
import kotlin.random.Random

/**
 * @author create by zhangwei03
 */
object KeyCenter {

    private const val TAG = "KeyCenter"

    var rtcUid: Int = Random(System.nanoTime()).nextInt(10000) + 1000000;

    fun getRtcToken(channelId: String, uid: Int): String {
        var rtcToken: String = ""
        if (TextUtils.isEmpty(BuildConfig.AG_APP_CERTIFICATE)) {
            return rtcToken
        }
        try {
            rtcToken = RtcTokenBuilder().buildTokenWithUid(
                BuildConfig.AG_APP_ID, BuildConfig.AG_APP_CERTIFICATE, channelId, uid,
                RtcTokenBuilder.Role.Role_Publisher, 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "rtc token build error:${e.message}")
        }
        return rtcToken
    }
}