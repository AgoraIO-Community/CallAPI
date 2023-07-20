package io.agora.onetoone.model

import io.agora.rtc2.video.VideoEncoderConfiguration
import java.io.Serializable

/**  加入房间时携带的信息
 */
data class EnterRoomInfoModel (
    var isBrodCaster: Boolean = true,
    var currentUid: String = "",
    var showRoomId: String = "",
    var showUserId: String = "",

    var tokenRoomId: String = "",
    var rtcToken: String = "",
    var rtmToken: String = "",
    var showRoomToken: String = "",

    var dimensionsWidth: String = "",
    var dimensionsHeight: String = "",
    var frameRate: String = "",
    var bitrate: String = "0",
) : Serializable