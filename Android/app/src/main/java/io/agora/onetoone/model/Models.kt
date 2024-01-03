package io.agora.onetoone.model

import java.io.Serializable

/**  加入房间时携带的信息
 */
data class EnterRoomInfoModel (
    var isBrodCaster: Boolean = true,
    var currentUid: String = "",
    var showRoomId: String = "",
    var showUserId: String = "",

    var rtcToken: String = "",
    var rtmToken: String = "",
    var showRoomToken: String = "",

    var dimensionsWidth: String = "",
    var dimensionsHeight: String = "",
    var frameRate: String = "",
    var bitrate: String = "0",

    var autoAccept: Boolean = true,
    var autoJoinRTC: Boolean = false
) : Serializable