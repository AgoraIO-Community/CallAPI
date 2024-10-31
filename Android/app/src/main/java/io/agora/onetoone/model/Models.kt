package io.agora.onetoone.model

import java.io.Serializable

/** Information carried when joining a room */
data class EnterRoomInfoModel (
    var isRtm: Boolean = true,
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
    var autoJoinRTC: Boolean = false,

    var firstFrameWaittingDisabled: Boolean = false
) : Serializable