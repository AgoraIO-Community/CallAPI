package io.agora.onetoone

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.gson.Gson
import io.agora.callapi.BuildConfig
import io.agora.onetoone.extension.*
import io.agora.onetoone.report.APIReporter
import io.agora.onetoone.report.APIType
import io.agora.onetoone.report.ApiCostEvent
import io.agora.onetoone.signalClient.ISignalClientListener
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import org.json.JSONObject
import java.util.*

enum class CallAutoSubscribeType(val value: Int) {
    None(0),
    Video(1),
    AudioVideo(2),
}
enum class CallAction(val value: Int) {
    Call(0),
    CancelCall(1),
    Accept(2),
    Reject(3),
    Hangup(4),
    AudioCall(10);
    companion object {
        fun fromValue(value: Int): CallAction? {
            return CallAction.values().find { it.value == value }
        }
    }
}

object CallCustomEvent {
    const val stateChange = "stateChange"
    const val eventChange = "eventChange"
}

/*
 * Timing for joining RTC during an incoming call
 */
enum class CalleeJoinRTCTiming(val value: Int) {
    Calling(0),      // Join the channel and start pushing video stream immediately upon receiving the call; higher cost for the callee but faster video display
    Accepted(1)      // Join the channel and start pushing video stream only after actively accepting the call; lower cost for the callee but slower video display
}

class CallApiImpl constructor(
    val context: Context
): ICallApi, ISignalClientListener, IRtcEngineEventHandler() {

    companion object {
        const val kReportCategory = "2.1.2"
        const val kPublisher = "publisher"
        const val kCostTimeMap = "costTimeMap"    // Duration information during the call, which will throw step duration when connected
        const val kRemoteUserId = "remoteUserId"
        const val kFromUserId = "fromUserId"
        const val kFromRoomId = "fromRoomId"
        const val kFromUserExtension = "fromUserExtension"

        // ⚠️ Do not modify the following two values; the client may make business judgments based on this rejectReason/call busy (e.g., user busy)
        const val kRejectReason = "rejectReason"
        const val kRejectReasonCallBusy = "The user is currently busy"
        // Indicates whether it is an internal rejection; currently marked as the remote end call busy
        const val kRejectByInternal = "rejectByInternal"
        // Indicates whether the call was canceled internally; currently marked as remote calling timeout
        const val kCancelCallByInternal = "cancelCallByInternal"

        const val kHangupReason = "hangupReason"
        // The ID of the sent message
        private const val kMessageId = "messageId"
    }

    private val kCallTimeoutInterval: Long = 15000
    private val kCurrentMessageVersion = "1.0"
    private val kMessageAction = "message_action"
    private val kMessageVersion = "message_version"
    private val kMessageTs = "message_timestamp"
    private val kCallId = "callId"

    private val TAG = "CallApiImpl_LOG"
    private val delegates = mutableListOf<ICallApiListener>()
    private val localFrameProxy = CallLocalFirstFrameProxy(this)
    private var config: CallConfig? = null
        set(value) {
            field?.signalClient?.removeListener(this)
            field = value
            field?.signalClient?.addListener(this)
        }
    private var prepareConfig: PrepareConfig? = null
    private var connectInfo = CallConnectInfo()
    private var reportInfoList = listOf<CallReportInfo>()
    private var isChannelJoined = false
    // Message ID
    private var messageId: Int = 0

    private var tempRemoteCanvasView = TextureView(context)
    private var tempLocalCanvasView = TextureView(context)
    // Default timing for joining RTC
    private var defaultCalleeJoinRTCTiming = CalleeJoinRTCTiming.Calling

    private var reporter: APIReporter? = null

    /// Current State
    private var state: CallStateType = CallStateType.Idle
        set(value) {
            val prevState = field
            field = value
            if (prevState == value) { return }
            when(value) {
                CallStateType.Calling -> {
                    tempRemoteCanvasView.alpha = 0f
                    // If prepareConfig?.callTimeoutSeconds == 0, no timeout will be applied internally.
                    val timeout = prepareConfig?.callTimeoutMillisecond ?: 0L
                    if (timeout <= 0L) {
                        return
                    }
                    // Start the timer; if there is no response after the timeout, call "no response."
                    connectInfo.scheduledTimer({
                        _cancelCall(cancelCallByInternal = true) {  }
                        _updateAndNotifyState(CallStateType.Prepared, CallStateReason.CallingTimeout)
                        _notifyEvent(CallEvent.CallingTimeout)
                    }, timeout)
                }
                CallStateType.Prepared -> {
                    connectInfo.scheduledTimer(null)
                    if (prevState != CallStateType.Idle) {
                        _prepareForCall(prepareConfig!!) {
                        }
                    }
                }
                CallStateType.Connecting -> {
                    reporter?.startDurationEvent(ApiCostEvent.FIRST_FRAME_PERCEIVED)
                }
                CallStateType.Connected -> {
                    _muteRemoteAudio(false)
                    tempRemoteCanvasView.alpha = 1f
                    connectInfo.scheduledTimer(null)
                    val ext = mapOf<String, Any>(
                        "channelName" to (connectInfo.callingRoomId ?: ""),
                        "userId" to (config?.userId ?: 0)
                    )
                    reporter?.endDurationEvent(ApiCostEvent.FIRST_FRAME_PERCEIVED, ext)
                    reporter?.endDurationEvent(ApiCostEvent.FIRST_FRAME_ACTUAL, ext)
                }
                CallStateType.Idle, CallStateType.Failed -> {
                    _leaveRTC()
                    connectInfo.clean()
                    isPreparing = false
                }
                else -> {}
            }
        }
    // Connection for joining the channel ex, used to leave the channel ex and check if already joined the ex channel
    private var rtcConnection: RtcConnection? = null
    // Callback for completing RTC join
    private var joinRtcCompletion: ((AGError?) -> Unit)? = null
    // Callback for the first frame video/audio
    private var firstFrameCompletion: (() -> Unit)? = null
    // Indicates whether currently preparing; currently a straightforward return of an error, to see if we need to store each closure for dispatch after completion
    private var isPreparing = false

    init {
        callPrint("init-- CallApiImpl")
    }
    // Get NTP time
    private fun _getTimeInMs(): Long {
        return System.currentTimeMillis()
    }

    private fun _getCost(ts: Int? = null): Long {
        val cts = connectInfo.callTs ?: return 0
        return if (ts != null) {
            ts - cts
        } else {
            _getTimeInMs() - cts
        }
    }

    private fun _messageDic(action: CallAction): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            kMessageAction to action.value,
            kMessageVersion to kCurrentMessageVersion,
            kMessageTs to _getTimeInMs(),
            kFromUserId to (config?.userId ?: 0),
            kCallId to connectInfo.callId
        )
        prepareConfig?.userExtension?.let {
            map[kFromUserExtension] = it
        }
        return map
    }

    private fun _callMessageDic(remoteUserId: Int, callType: CallType, fromRoomId: String, callExtension: Map<String, Any>): Map<String, Any> {
        val message = _messageDic(action = if(callType == CallType.Video) CallAction.Call else CallAction.AudioCall).toMutableMap()
        message[kRemoteUserId] = remoteUserId
        message[kFromRoomId] = fromRoomId
        var userExtension = message[kFromUserExtension] as? Map<String, Any> ?: emptyMap()
        userExtension = userExtension + callExtension
        message[kFromUserExtension] = userExtension
        return message
    }

    private fun _cancelCallMessageDic(cancelByInternal: Boolean): Map<String, Any> {
        val message = _messageDic(CallAction.CancelCall).toMutableMap()
        message[kCancelCallByInternal] = if (cancelByInternal) 1 else 0
        return message
    }

    private fun _rejectMessageDic(reason: String?, rejectByInternal: Boolean): Map<String, Any> {
        val message = _messageDic(CallAction.Reject).toMutableMap()
        message[kRejectReason] = reason ?: ""
        message[kRejectByInternal] = if (rejectByInternal) 1 else 0
        return message
    }

    private fun _hangupMessageDic(reason: String?): Map<String, Any> {
        val message = _messageDic(CallAction.Hangup).toMutableMap()
        message[kHangupReason] = reason ?: ""
        return message
    }

    private fun getNtpTimeInMs(): Long {
        val currentNtpTime = config?.rtcEngine?.ntpWallTimeInMs ?: 0L
        return if (currentNtpTime != 0L) {
            currentNtpTime
        } else {
            Log.e(TAG, "getNtpTimeInMs ntpWallTimeInMs is zero!!!!!!!!!!")
            System.currentTimeMillis()
        }
    }

    private fun _canJoinRtcOnCalling(eventInfo: Map<String, Any>): Boolean {
        var emptyCount = 0
        delegates.forEach {
            val isEnable: Boolean? = it.canJoinRtcOnCalling(eventInfo)
            if (isEnable != null) {
                if (isEnable) {
                    return true
                }
            } else {
                emptyCount += 1
            }
        }

        // If no protocol is implemented, use the default value
        if (emptyCount == delegates.size) {
            callPrint("join rtc strategy callback not found, use default")
            return true
        }

        return false
    }

    private fun _notifyCallConnected() {
        val config = config ?: return
        val ntpTime = getNtpTimeInMs()
        connectInfo.callConnectedTs = ntpTime
        val callUserId = (if (connectInfo.callingRoomId == prepareConfig?.roomId) config.userId else connectInfo.callingUserId) ?: 0
        delegates.forEach { listener ->
            listener.onCallConnected(
                roomId = connectInfo.callingRoomId ?: "",
                callUserId = callUserId,
                currentUserId = config.userId,
                timestamp = ntpTime
            )
        }
    }

    private fun _notifyCallDisconnected(hangupUserId: Int) {
        val config = config ?: return
        val ntpTime = getNtpTimeInMs()
        delegates.forEach { listener ->
            listener.onCallDisconnected(
                roomId = connectInfo.callingRoomId ?: "",
                hangupUserId = hangupUserId,
                currentUserId = config.userId,
                timestamp = ntpTime,
                duration = ntpTime - connectInfo.callConnectedTs
            )
        }
    }

    private fun _notifyTokenPrivilegeWillExpire() {
        delegates.forEach { listener ->
            listener.tokenPrivilegeWillExpire()
        }
    }

    private fun checkConnectedSuccess(reason: CallStateReason) {
        if (rtcConnection == null) {
            callWarningPrint("checkConnectedSuccess fail, connection not found")
            return
        }
        val firstFrameWaittingDisabled = prepareConfig?.firstFrameWaittingDisabled ?: false
        callPrint("checkConnectedSuccess: firstFrameWaittingDisabled: ${firstFrameWaittingDisabled}, isRetrieveFirstFrame: ${connectInfo.isRetrieveFirstFrame} state: $state")
        if (firstFrameWaittingDisabled == true) {
            if (state == CallStateType.Connecting) else {return}
        } else {
            if (connectInfo.isRetrieveFirstFrame && state == CallStateType.Connecting) else {return}
        }
        /*
         1. Because the callee joins the channel and subscribes to streams and pushes streams early, the dual end may receive the first video frame earlier than the callee clicks accept (changing to connecting).
         2. Since both ends receive onCall when matching 1v1, if A initiates accept, B receives onAccept + A's first frame, it will cause B to enter the connected state without accepting.
         Therefore:
         Changing to connecting: need to check if it has changed to "remote accepted" + "local accepted (or called)".
         Changing to connected: need to check if it is in "connecting state" + "received first frame".
         */
        _changeToConnectedState(reason)
    }

    private fun _changeToConnectedState(reason: CallStateReason) {
        val eventInfo = mapOf(
            kFromRoomId to (connectInfo.callingRoomId ?: ""),
            kFromRoomId to (connectInfo.callingRoomId ?: ""),
            kFromUserId to (connectInfo.callingUserId ?: 0),
            kRemoteUserId to (config?.userId ?: 0),
            kCostTimeMap to connectInfo.callCostMap
        )
        _updateAndNotifyState(CallStateType.Connected, reason, eventInfo = eventInfo)
//        _notifyEvent(event: CallReason.RecvRemoteFirstFrame, elapsed: elapsed)
    }
    // External state notification
    private fun _updateAndNotifyState(state: CallStateType,
                                      stateReason: CallStateReason = CallStateReason.None,
                                      eventReason: String = "",
                                      eventInfo: Map<String, Any> = emptyMap()) {
        callPrint("call change[${connectInfo.callId}] state: $state, stateReason: '$stateReason', eventReason: $eventReason")

        val oldState = this.state
        //check connected/disconnected
        if (state == CallStateType.Connected && oldState == CallStateType.Connecting) {
            _notifyCallConnected()
        } else if (state == CallStateType.Prepared && oldState == CallStateType.Connected) {
            when (stateReason) {
                // Normally only RemoteCancelled, RemoteHangup will trigger, the rest are fallback
                CallStateReason.RemoteCancelled, CallStateReason.RemoteHangup, CallStateReason.RemoteRejected, CallStateReason.RemoteCallBusy -> {
                    _notifyCallDisconnected(connectInfo.callingUserId ?: 0)
                }
                else -> {
                    //.localHangup or bad case
                    _notifyCallDisconnected(config?.userId ?: 0)
                }
            }
        }

        val ext = mapOf(
            "state" to state.value,
            "stateReason" to stateReason.value,
            "eventReason" to eventReason,
            "userId" to (config?.userId ?: 0),
            "callId" to connectInfo.callId
        )
        _reportCustomEvent(CallCustomEvent.stateChange, ext)

        this.state = state
        delegates.forEach {
            it.onCallStateChanged(state, stateReason, eventReason, eventInfo)
        }
    }

    private fun _notifySendMessageErrorEvent(error: AGError, reason: String?) {
        _notifyErrorEvent(
            CallErrorEvent.SendMessageFail,
            errorType = CallErrorCodeType.Message,
            errorCode = error.code,
            message = "${reason ?: ""}${error.msg}"
        )
    }

    private fun _notifyRtcOccurErrorEvent(errorCode: Int, message: String? = null) {
        _notifyErrorEvent(
            CallErrorEvent.RtcOccurError,
            errorType =  CallErrorCodeType.Rtc,
            errorCode =  errorCode,
            message =  message
        )
    }

    private fun _notifyErrorEvent(
        errorEvent: CallErrorEvent,
        errorType: CallErrorCodeType,
        errorCode: Int,
        message: String?) {
        callPrint("call change[${connectInfo.callId} errorEvent: ${errorEvent.value}, errorType: ${errorType.value}, errorCode: ${errorCode}, message: ${message ?: ""}")
        delegates.forEach { listener ->
            listener.onCallError(errorEvent, errorType, errorCode, message)
        }
    }

    private fun _notifyEvent(event: CallEvent, reasonCode: String? = null, reasonString: String? = null) {
        callPrint("call change[${connectInfo.callId}] event: ${event.value} reason: '$reasonCode' reasonString: '$reasonString'")
        config?.let { config ->
            val ext = mutableMapOf(
                "event" to event.value,
                "userId" to config.userId,
                "state" to state.value,
                "callId" to connectInfo.callId
            )
            reasonCode?.let {
                ext["reasonCode"] = it
            }
            reasonString?.let {
                ext["reasonString"] = reasonString
            }
            _reportCustomEvent(CallCustomEvent.eventChange, ext)

        } ?: callWarningPrint("_notifyEvent config == null")
        delegates.forEach { listener ->
            listener.onCallEventChanged(event, reasonCode)
        }
        when (event) {
            CallEvent.RemoteUserRecvCall -> _reportCostEvent(CallConnectCostType.RemoteUserRecvCall)
            CallEvent.RemoteJoined -> _reportCostEvent(CallConnectCostType.RemoteUserJoinChannel)
            CallEvent.LocalJoined -> _reportCostEvent(CallConnectCostType.LocalUserJoinChannel)
            CallEvent.CaptureFirstLocalVideoFrame -> _reportCostEvent(CallConnectCostType.LocalFirstFrameDidCapture)
            CallEvent.PublishFirstLocalAudioFrame -> _reportCostEvent(CallConnectCostType.LocalFirstFrameDidPublish)
            CallEvent.PublishFirstLocalVideoFrame -> _reportCostEvent(CallConnectCostType.LocalFirstFrameDidPublish)
            CallEvent.RemoteAccepted -> {
                _reportCostEvent(CallConnectCostType.AcceptCall)
                checkConnectedSuccess(CallStateReason.RemoteAccepted)
            }
            CallEvent.LocalAccepted -> {
                _reportCostEvent(CallConnectCostType.AcceptCall)
                checkConnectedSuccess(CallStateReason.LocalAccepted)
            }
            CallEvent.RecvRemoteFirstFrame -> {
                _reportCostEvent(CallConnectCostType.RecvFirstFrame)
                checkConnectedSuccess(CallStateReason.RecvRemoteFirstFrame)
            }
            else -> {}
        }
    }

    private fun _prepareForCall(prepareConfig: PrepareConfig, completion: ((AGError?) -> Unit)?) {
        val cfg = config
        if (cfg == null) {
            val reason = "config is Empty"
            callWarningPrint(reason)
            completion?.invoke(AGError(reason, -1))
            return
        }
        if (isPreparing) {
            val reason = "is already in preparing"
            callWarningPrint(reason)
            completion?.invoke(AGError(reason, -1))
            return
        }
        when (state) {
            CallStateType.Calling, CallStateType.Connecting, CallStateType.Connected -> {
                val reason = "currently busy"
                callWarningPrint(reason)
                completion?.invoke(AGError(reason, -1))
                return
            }
            CallStateType.Prepared -> {
            }
            CallStateType.Failed, CallStateType.Idle -> {
            }
        }

        val tag = UUID.randomUUID().toString()
        callPrint("prepareForCall[$tag]")
        this.prepareConfig = prepareConfig.cloneConfig()

        _leaveRTC()
        connectInfo.clean()
        
        completion?.invoke(null)

        // Unlike iOS, Android first adds the rendered view TextureView to the provided container
        setupTextureView()
    }
    private fun setupTextureView() {
        val prepareConfig = prepareConfig ?: return
        runOnUiThread {
            // Add remote rendering view
            prepareConfig.remoteView?.let { remoteView ->
                (tempRemoteCanvasView.parent as? ViewGroup)?.let { parentView ->
                    if (parentView != remoteView) {
                        parentView.removeView(tempRemoteCanvasView)
                    }
                }
                if (remoteView.indexOfChild(tempRemoteCanvasView) == -1) {
                    tempRemoteCanvasView.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    remoteView.addView(tempRemoteCanvasView)
                } else {
                    callWarningPrint("remote view not found in connected state!")
                }
            }
            // add local rendering view
            prepareConfig.localView?.let { localView ->
                (tempLocalCanvasView.parent as? ViewGroup)?.let { parentView ->
                    if (parentView != localView) {
                        parentView.removeView(tempLocalCanvasView)
                    }
                }
                if (localView.indexOfChild(tempLocalCanvasView) == -1) {
                    tempLocalCanvasView.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    localView.addView(tempLocalCanvasView)
                } else {
                    callWarningPrint("remote view not found in connected state!")
                }
            }
        }
    }
    private fun _deinitialize() {
        _updateAndNotifyState(CallStateType.Idle)
        _notifyEvent(CallEvent.Deinitialize)
        reporter = null
    }

    // Set up remote video
    private fun _setupRemoteVideo(uid: Int) {
        if (connectInfo.callType == CallType.Audio) return

        val engine = config?.rtcEngine ?: return
        val connection = rtcConnection ?: run {
            callWarningPrint("_setupRemoteVideo fail: connection or engine is empty")
            return
        }
        val videoCanvas = VideoCanvas(tempRemoteCanvasView)
        videoCanvas.uid = uid
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN
        videoCanvas.mirrorMode = Constants.VIDEO_MIRROR_MODE_AUTO
        val ret = engine.setupRemoteVideoEx(videoCanvas, connection)
        callPrint("_setupRemoteVideo ret: $ret, channelId: ${connection.channelId}, uid: $uid")
    }

    private fun _removeRemoteVideo(uid: Int) {
        val engine = config?.rtcEngine ?: return
        val connection = rtcConnection ?: run {
            callWarningPrint("_setupRemoteVideo fail: connection or engine is empty")
            return
        }
        val videoCanvas = VideoCanvas(null)
        videoCanvas.uid = uid
        val ret = engine.setupRemoteVideoEx(videoCanvas, connection)
        callPrint("_setupRemoteVideo ret: $ret, channelId: ${connection.channelId}, uid: $uid")

        (tempRemoteCanvasView.parent as? ViewGroup)?.removeView(tempRemoteCanvasView)
        tempRemoteCanvasView = TextureView(context)

        // Add remote rendering view
        prepareConfig?.remoteView?.let { remoteView ->
            if (remoteView.indexOfChild(tempRemoteCanvasView) == -1) {
                tempRemoteCanvasView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                remoteView.addView(tempRemoteCanvasView)
            } else {
                callWarningPrint("remote view not found in connected state!")
            }
        }
    }

    private fun _setupLocalVideo() {
        if (connectInfo.callType == CallType.Audio) return
        val engine = config?.rtcEngine ?: run {
            callWarningPrint("_setupLocalVideo fail: engine is empty")
            return
        }
        config?.rtcEngine?.addHandler(localFrameProxy)

        val videoCanvas = VideoCanvas(tempLocalCanvasView)
        videoCanvas.setupMode = VideoCanvas.VIEW_SETUP_MODE_ADD
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN
        videoCanvas.mirrorMode = Constants.VIDEO_MIRROR_MODE_AUTO
        engine.setDefaultAudioRoutetoSpeakerphone(true)
        engine.setupLocalVideo(videoCanvas)
        val ret = engine.startPreview()
        if (ret != 0) {
            _notifyErrorEvent(CallErrorEvent.StartCaptureFail, CallErrorCodeType.Rtc, ret, null)
        }
    }

    private fun _removeLocalVideo() {
        if (connectInfo.callType == CallType.Audio) return
        val engine = config?.rtcEngine ?: run {
            callWarningPrint("_setupLocalVideo fail: engine is empty")
            return
        }
        val canvas = VideoCanvas(tempLocalCanvasView)
        canvas.setupMode = VideoCanvas.VIEW_SETUP_MODE_REMOVE
        engine.setupLocalVideo(canvas)
    }

    /// Check if the current RTC channel matches the provided room ID
    /// - Parameter roomId: <#roomId description#>
    /// - Returns: <#description#>
    private fun _isCurrentRTCChannel(roomId: String): Boolean {
        return rtcConnection?.channelId == roomId
    }

    /// Whether the current RTC channel has successfully joined or is in the process of joining
    /// - Returns: <#description#>
    private fun _isChannelJoinedOrJoining(): Boolean {
        return rtcConnection != null
    }

    /// Whether initialization is complete
    /// - Returns: <#description#>
    private fun _isInitialized(): Boolean {
        return when (state) {
            CallStateType.Idle, CallStateType.Failed -> false
            else -> true
        }
    }

    private fun _isCallingUser(message: Map<String, Any>) : Boolean {
        val fromUserId = message[kFromUserId] as? Int ?: return false
        if (connectInfo.callingUserId != fromUserId) return false
        return true
    }

    private fun _joinRTCWithMediaOptions(roomId: String, completion: ((AGError?) -> Unit)) {
        if (!_isCurrentRTCChannel(roomId)) {
            _leaveRTC()
        }
        val isChannelJoinedOrJoining = _isChannelJoinedOrJoining()
        if (isChannelJoinedOrJoining) {
            completion.invoke(null)
        } else {
            _joinRTC(roomId){ error ->
                completion.invoke(error)
            }
        }
        val publishVideo = connectInfo.callType != CallType.Audio
        val subscribeVideo = connectInfo.callType != CallType.Audio

        _updatePublishStatus(audioStatus = true, videoStatus = publishVideo)
        _updateSubscribeStatus(audioStatus = true, videoStatus = subscribeVideo)

        // After joining the channel, mute first, and unmute only after connecting
        _muteRemoteAudio(true)
    }

    private fun _joinRTCAsBroadcaster(roomId: String) {
        _joinRTCWithMediaOptions(roomId) { error ->
            if (error != null) {
                _notifyRtcOccurErrorEvent(error.code, error.msg)
            } else {
                _notifyEvent(CallEvent.JoinRTCSuccessed)
            }
        }
        setupCanvas()
    }

    private fun _joinRTC(roomId: String, completion:((AGError?) -> Unit)?) {
        val config = this.config
        val rtcToken = prepareConfig?.rtcToken
        if (config == null || rtcToken == null) {
            completion?.invoke(AGError("config is empty", -1))
            return
        }
        val connection = RtcConnection(roomId, config.userId)
        val mediaOptions = ChannelMediaOptions()
        mediaOptions.publishCameraTrack = false
        mediaOptions.publishMicrophoneTrack = false
        mediaOptions.autoSubscribeAudio = false
        mediaOptions.autoSubscribeVideo = false
        mediaOptions.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        val ret: Int = config.rtcEngine?.joinChannelEx(rtcToken, connection, mediaOptions, this) ?: 0
        callPrint("joinRTC channel roomId: $roomId uid: ${config.userId} ret = $ret")
        rtcConnection = connection
        joinRtcCompletion = {
            completion?.invoke(null)
        }
        firstFrameCompletion = {
            connectInfo.isRetrieveFirstFrame = true
            _notifyEvent(CallEvent.RecvRemoteFirstFrame)
        }
        if (ret != Constants.ERR_OK) {
            _notifyRtcOccurErrorEvent(ret)
        }
        _notifyEvent(CallEvent.JoinRTCStart)

        reporter?.startDurationEvent(ApiCostEvent.FIRST_FRAME_ACTUAL)
    }

    /**
     * Update the status of the audio and video stream being pushed
     * @param audioStatus Whether to push the audio stream
     * @param videoStatus Whether to push the video stream
     */
    private fun _updatePublishStatus(audioStatus: Boolean, videoStatus: Boolean) {
        val config = config
        val connection = rtcConnection
        if (config == null || connection == null) { return}
        callPrint("_updatePublishStatus, audioStatus$audioStatus videoStatus:$videoStatus")

        config.rtcEngine?.enableLocalAudio(audioStatus)
        config.rtcEngine?.enableLocalVideo(videoStatus)

        val mediaOptions = ChannelMediaOptions()
        mediaOptions.publishCameraTrack = videoStatus
        mediaOptions.publishMicrophoneTrack = audioStatus
        config.rtcEngine?.updateChannelMediaOptionsEx(mediaOptions, connection)
    }

    /**
     * Update the subscription status of the audio and video streams
     * @param audioStatus Audio stream subscription status
     * @param videoStatus Video stream subscription status
     */
    private fun _updateSubscribeStatus(audioStatus: Boolean, videoStatus: Boolean) {
        val config = config ?: run { return }
        val connection = rtcConnection ?: run { return }
        callPrint("_updateSubscribeStatus, audioStatus$audioStatus, videoStatus:$videoStatus")
        val mediaOptions = ChannelMediaOptions()
        mediaOptions.autoSubscribeAudio = audioStatus
        mediaOptions.autoSubscribeVideo = videoStatus
        config.rtcEngine?.updateChannelMediaOptionsEx(mediaOptions, connection)
    }

    private fun _muteRemoteAudio(isMute: Boolean) {
        val rtcEngine = config?.rtcEngine ?: return
        val connection = rtcConnection ?: return

        val uid = connectInfo.callingUserId
        uid?.let { it ->
            callPrint("_muteRemoteAudio: $isMute uid: $it channelId: ${connection.channelId}")
            rtcEngine.adjustUserPlaybackSignalVolumeEx(it, if (isMute) 0 else 100, connection)
        }
    }

    private fun _leaveRTC() {
        joinRtcCompletion = null
        val connection = rtcConnection ?: run {
            //callWarningPrint("leave RTC channel failed, not joined the channel")
            return
        }
        cleanCanvas()
        _updatePublishStatus(audioStatus = false, videoStatus = false)
        config?.rtcEngine?.stopPreview()
        val ret = config?.rtcEngine?.leaveChannelEx(connection)
        callPrint("leave RTC channel[${ret ?: -1}]")
        rtcConnection = null
    }

    private fun setupCanvas() {
        _setupLocalVideo()
        val callingUserId = connectInfo.callingUserId ?: run {
            callWarningPrint("setupCanvas fail: callingUserId == null")
            return
        }
        _setupRemoteVideo(callingUserId)
    }

    private fun cleanCanvas() {
        _removeLocalVideo()
        val callingUserId = connectInfo.callingUserId ?: run {
            callWarningPrint("cleanCanvas fail: callingUserId == null")
            return
        }
        _removeRemoteVideo(callingUserId)
    }

    private fun _reportCostEvent(type: CallConnectCostType) {
        val cost = _getCost()
        connectInfo.callCostMap[type.value] = cost
        val ext = mapOf(
            "channelName" to (connectInfo.callingRoomId ?: ""),
            "callId" to connectInfo.callId,
            "userId" to (config?.userId ?: 0)
        )
        reporter?.reportCostEvent(type.value, cost.toInt(), ext)
    }

    private fun _reportMethod(event: String, ext: Map<String, Any>? = null) {
        val value = ext ?: mapOf()
        callPrint("_reportMethod event: $event value: $value")
        var subEvent = event
        val range = event.indexOf("(")
        if (range != -1) {
            subEvent = event.substring(0, range)
        }

        val ext = mapOf<String, Any>(
            "callId" to connectInfo.callId,
            "userId" to (config?.userId ?: 0)
        )
        reporter?.reportFuncEvent(
            name = subEvent,
            value = value,
            ext = ext
        )
    }

    private fun _reportCustomEvent(event: String, ext: Map<String, Any>) {
        callPrint("_reportMethod event: $event value: $ext")
        reporter?.reportCustomEvent(
            name = event,
            ext = ext
        )
    }

    private fun _sendMessage(
        userId: String,
        message: Map<String, Any>,
        completion: ((AGError?) -> Unit)?
    ) {
        messageId += 1
        messageId %= Int.MAX_VALUE
        val map = message.toMutableMap()
        map[kMessageId] = messageId
        val jsonString = Gson().toJson(map).toString()
        config?.signalClient?.sendMessage(userId, jsonString, completion)
    }

    //MARK: on Message
    private fun _processRespEvent(reason: CallAction, message: Map<String, Any>) {
        when (reason) {
            CallAction.Call ->          _onCall(message, CallType.Video)
            CallAction.AudioCall ->     _onCall(message, CallType.Audio)
            CallAction.CancelCall ->    _onCancel(message)
            CallAction.Reject ->        _onReject(message)
            CallAction.Accept ->        _onAccept(message)
            CallAction.Hangup ->        _onHangup(message)
            else -> {}
        }
    }

    private fun _call(
        remoteUserId: Int,
        callType: CallType,
        callExtension: Map<String, Any>,
        completion: ((AGError?) -> Unit)?
    ) {
        val fromRoomId = prepareConfig?.roomId
        val fromUserId = config?.userId
        if (fromRoomId == null || fromUserId == null) {
            val reason = "call fail! config or roomId is empty"
            completion?.invoke(AGError(reason, -1))
            callWarningPrint(reason)
            return
        }
        if (state != CallStateType.Prepared) {
            val reason = "call fail! state busy or not initialized"
            completion?.invoke(AGError(reason, -1))
            callWarningPrint(reason)
            return
        }
        // Send call message
        connectInfo.set(
            callType = callType,
            userId = remoteUserId,
            roomId = fromRoomId,
            callId = UUID.randomUUID().toString(),
            isLocalAccepted = true
        )

        val message = _callMessageDic(
            remoteUserId = remoteUserId,
            callType = callType,
            fromRoomId = fromRoomId,
            callExtension = callExtension
        )
        _sendMessage(remoteUserId.toString(), message) { err ->
            completion?.invoke(err)
            if (err != null) {
                //_updateAndNotifyState(CallStateType.Prepared, CallReason.MessageFailed, err.msg)
                _notifySendMessageErrorEvent(err, "call fail: ")
                //return@sendMessage
            } else {
                _notifyEvent(CallEvent.RemoteUserRecvCall)
            }
        }

        val reason = if (callType == CallType.Video) CallStateReason.LocalVideoCall else CallStateReason.LocalAudioCall
        val event = if (callType == CallType.Video) CallEvent.LocalVideoCall else CallEvent.LocalAudioCall
        _updateAndNotifyState(CallStateType.Calling, reason, eventInfo = message)
        _notifyEvent(event)
        _joinRTCAsBroadcaster(fromRoomId)
    }

    private fun _cancelCall(message: Map<String, Any>? = null, cancelCallByInternal: Boolean = false, completion: ((AGError?) -> Unit)? = null) {
        val userId = connectInfo.callingUserId
        if (userId == null) {
            completion?.invoke(AGError("cancelCall fail! callingRoomId is empty", -1))
            callWarningPrint("cancelCall fail! callingRoomId is empty")
            return
        }
        val msg = message ?: _cancelCallMessageDic(cancelCallByInternal)
        _sendMessage(userId.toString(), msg) { err ->
            completion?.invoke(err)
            if (err != null) {
                _notifySendMessageErrorEvent(err, "cancel call fail: ")
            }
        }
    }

    private fun _reject(remoteUserId: Int, message: Map<String, Any>, completion: ((AGError?) -> Unit)? = null) {
        _sendMessage(remoteUserId.toString(), message, completion)
    }

    private fun _hangup(remoteUserId: Int, message: Map<String, Any>? = null, completion: ((AGError?) -> Unit)? = null) {
        _sendMessage(remoteUserId.toString(), message ?: _messageDic(CallAction.Hangup), completion)
    }

    // Received call message
    private fun _onCall(message: Map<String, Any>, callType: CallType) {
        val fromRoomId = message[kFromRoomId] as String
        val fromUserId = message[kFromUserId] as Int
        val callId = message[kCallId] as String

        var enableNotify = true
        when (state) {
            CallStateType.Idle, CallStateType.Failed -> {
                // not reachable
//            _reject(remoteUserId: fromUserId, reason: kRejectReasonCallBusy, true)
                return
            }
            CallStateType.Calling, CallStateType.Connecting, CallStateType.Connected -> {
                if ((connectInfo.callingUserId ?: 0) != fromUserId) {
                    val message = _rejectMessageDic(kRejectReasonCallBusy, rejectByInternal = true)
                    _reject(fromUserId, message)
                    return
                }
                if (state == CallStateType.Calling) {
                    enableNotify = false
                }
            }
            else -> {}
        }

        connectInfo.set(callType, fromUserId, fromRoomId, callId)
        defaultCalleeJoinRTCTiming = if (_canJoinRtcOnCalling(eventInfo = message)) CalleeJoinRTCTiming.Calling else CalleeJoinRTCTiming.Accepted
        if (enableNotify) {
            val reason = if (callType == CallType.Video) CallStateReason.RemoteVideoCall else CallStateReason.RemoteAudioCall
            val event = if (callType == CallType.Video) CallEvent.RemoteVideoCall else CallEvent.RemoteAudioCall
            _updateAndNotifyState(CallStateType.Calling, reason, eventInfo = message)
            _notifyEvent(event)
        }
        callPrint("[calling]defaultCalleeJoinRTCTiming: ${defaultCalleeJoinRTCTiming.value}")
        if(defaultCalleeJoinRTCTiming == CalleeJoinRTCTiming.Calling) {
            _joinRTCAsBroadcaster(fromRoomId)
        }

        if (connectInfo.isLocalAccepted && prepareConfig?.firstFrameWaittingDisabled == true) {
            // If the first frame is not associated, in the scenario of switching to a 1v1 show,
            // it might automatically accept, leading to a state of connected before joining the channel,
            // making unmuting ineffective.
            checkConnectedSuccess(CallStateReason.LocalAccepted)
        }
    }

    private fun _onCancel(message: Map<String, Any>) {
        // Do not process if the operation is not from the user who is currently calling
        if (!_isCallingUser(message)) return

        var stateReason: CallStateReason = CallStateReason.RemoteCancelled
        var callEvent: CallEvent = CallEvent.RemoteCancelled
        val cancelCallByInternal = message[kCancelCallByInternal] as? Int
        if (cancelCallByInternal == 1) {
            stateReason = CallStateReason.RemoteCallingTimeout
            callEvent = CallEvent.RemoteCallingTimeout
        }
        _updateAndNotifyState(state = CallStateType.Prepared, stateReason = stateReason, eventInfo = message)
        _notifyEvent(event = callEvent)
    }

    private fun _onReject(message: Map<String, Any>) {
        if (!_isCallingUser(message)) return
        var stateReason: CallStateReason =  CallStateReason.RemoteRejected
        var callEvent: CallEvent = CallEvent.RemoteRejected
        val rejectByInternal = message[kRejectByInternal]
        if (rejectByInternal == 1) {
            stateReason = CallStateReason.RemoteCallBusy
            callEvent = CallEvent.RemoteCallBusy
        }

        _updateAndNotifyState(CallStateType.Prepared, stateReason, eventInfo = message)
        _notifyEvent(callEvent)
    }

    private fun _onAccept(message: Map<String, Any>) {
        // Must be in calling state and from the user who initiated the call
        if (!_isCallingUser(message) || state != CallStateType.Calling) return
        // TODO: If already connected
        // and isLocalAccepted (initiated the call or has already accepted),
        // otherwise consider that local has not agreed
        if (connectInfo.isLocalAccepted) {
            _updateAndNotifyState(CallStateType.Connecting, CallStateReason.RemoteAccepted, eventInfo = message)
        }
        _notifyEvent(CallEvent.RemoteAccepted)
    }

    private fun _onHangup(message: Map<String, Any>) {
        if (!_isCallingUser(message)) return

        _updateAndNotifyState(CallStateType.Prepared, CallStateReason.RemoteHangup, eventInfo = message)
        _notifyEvent(CallEvent.RemoteHangup)
    }

    //MARK: CallApiProtocol
    override fun getCallId(): String {
        _reportMethod("getCallId")
        return connectInfo.callId
    }

    override fun initialize(config: CallConfig) {
        if (state != CallStateType.Idle) {
            callWarningPrint("must invoke 'deinitialize' to clean state")
            return
        }

        reporter = APIReporter(APIType.CALL, kReportCategory, config.rtcEngine)
        _reportMethod("initialize", mapOf("appId" to config.appId, "userId" to config.userId))
        this.config = config.cloneConfig()

        // Best Practices for Video
        // 3. API to enable accelerated rendering of the first frame of audio and video
//        config.rtcEngine.enableInstantMediaRendering()

        // 4. Private parameters or configuration to enable first frame FEC
        config.rtcEngine.setParameters("{\"rtc.video.quickIntraHighFec\": true}")

        // 5. Private parameters or configuration to set AUT CC mode
        config.rtcEngine.setParameters("{\"rtc.network.e2e_cc_mode\": 3}")  //(No need to set this in versions 4.3.0 and later; the default value has been changed to 3)

        // 6. Private parameters or configuration to set the sensitivity for VQC resolution adjustment
        config.rtcEngine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomin\": 1000}")
        config.rtcEngine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomout\": 1000}")
    }

    override fun deinitialize(completion: (() -> Unit)) {
        _reportMethod("deinitialize")
        when (state) {
            CallStateType.Calling -> {
                cancelCall { err ->
                    completion.invoke()
                }
                _deinitialize()
            }
            CallStateType.Connecting, CallStateType.Connected -> {
                val callingUserId = connectInfo.callingUserId ?: 0
                _hangup(callingUserId) { err ->
                    completion.invoke()
                }
                _deinitialize()
            }
            else -> {
                _deinitialize()
                completion.invoke()
            }
        }
    }

    override fun renewToken(rtcToken: String) {
        _reportMethod("renewToken")
        val roomId = prepareConfig?.roomId
        if (roomId == null) {
            callWarningPrint("renewToken failed, roomid missmatch")
            return
        }
        prepareConfig?.rtcToken = rtcToken
        callPrint("renewToken with roomId[$roomId]")
        val connection = rtcConnection ?: return
        val options = ChannelMediaOptions()
        options.token = rtcToken
        val ret = this.config?.rtcEngine?.updateChannelMediaOptionsEx(options, connection)
        callPrint("rtc[$roomId] renewToken ret = ${ret ?: -1}")
    }

    override fun onFirstLocalVideoFramePublished(source: Constants.VideoSourceType?, elapsed: Int) {
        super.onFirstLocalVideoFramePublished(source, elapsed)
        _notifyEvent(event = CallEvent.PublishFirstLocalVideoFrame, reasonString = "elapsed: ${elapsed}ms")
    }

    override fun onFirstLocalVideoFrame(
        source: Constants.VideoSourceType?,
        width: Int,
        height: Int,
        elapsed: Int
    ) {
        super.onFirstLocalVideoFrame(source, width, height, elapsed)
        _notifyEvent(event = CallEvent.CaptureFirstLocalVideoFrame, reasonString = "elapsed: ${elapsed}ms")
        config?.rtcEngine?.removeHandler(localFrameProxy)
    }

    override fun onFirstLocalAudioFramePublished(elapsed: Int) {
        super.onFirstLocalAudioFramePublished(elapsed)
        _notifyEvent(CallEvent.PublishFirstLocalAudioFrame, reasonString = "elapsed: ${elapsed}ms")
    }

    override fun onFirstRemoteAudioFrame(uid: Int, elapsed: Int) {
        super.onFirstRemoteAudioFrame(uid, elapsed)
        val channelId = prepareConfig?.roomId ?: return
        if (uid != connectInfo.callingUserId) return
        if (connectInfo.callType != CallType.Audio) return
        callPrint("firstRemoteAudioFrameOfUid, channelId: $channelId, uid: $uid")
        runOnUiThread {
            firstFrameCompletion?.invoke()
        }
    }

    override fun prepareForCall(prepareConfig: PrepareConfig, completion: ((AGError?) -> Unit)?) {
        _reportMethod("prepareForCall", mapOf("roomId" to prepareConfig.roomId))
        _prepareForCall(prepareConfig) { err ->
            if (err != null) {
                _updateAndNotifyState(CallStateType.Failed, CallStateReason.RtmSetupFailed, err.msg)
                completion?.invoke(err)
                return@_prepareForCall
            }
            _updateAndNotifyState(CallStateType.Prepared)
            completion?.invoke(null)
        }
    }

    override fun addListener(listener: ICallApiListener) {
        _reportMethod("addListener")
        if (delegates.contains(listener)) { return }
        delegates.add(listener)
    }

    override fun removeListener(listener: ICallApiListener) {
        _reportMethod("removeListener")
        delegates.remove(listener)
    }

    override fun call(remoteUserId: Int, completion: ((AGError?) -> Unit)?) {
        _call(
            remoteUserId = remoteUserId,
            callType = CallType.Video,
            callExtension = emptyMap(),
            completion = completion
        )
        _reportMethod("call", mapOf("remoteUserId" to remoteUserId))
    }

    override fun call(
        remoteUserId: Int,
        callType: CallType,
        callExtension: Map<String, Any>,
        completion: ((AGError?) -> Unit)?
    ) {
        _call(
            remoteUserId = remoteUserId,
            callType = callType,
            callExtension = callExtension,
            completion = completion
        )
        _reportMethod("call", mapOf("remoteUserId" to remoteUserId, "callType" to callType.value, "callExtension" to callExtension))
    }

    // Cancel call
    override fun cancelCall(completion: ((AGError?) -> Unit)?) {
        _reportMethod("cancelCall")
        val message = _messageDic(CallAction.CancelCall)
        _cancelCall(message, false, completion)
        _updateAndNotifyState(CallStateType.Prepared, CallStateReason.LocalCancelled, eventInfo = message)
        _notifyEvent(CallEvent.LocalCancelled)
    }

    // Accept
    override fun accept(remoteUserId: Int, completion: ((AGError?) -> Unit)?) {
        _reportMethod("accept", mapOf("remoteUserId" to remoteUserId))
        val fromUserId = config?.userId
        val roomId = connectInfo.callingRoomId
        if (fromUserId == null || roomId == null) {
            val errReason = "accept fail! current userId or roomId is empty"
            completion?.invoke(AGError(errReason, -1))
            return
        }
        // Check if in calling state; if in prepared, it indicates the caller may have canceled
        if (state == CallStateType.Calling) else {
            val errReason = "accept fail! current state is $state not calling"
            completion?.invoke(AGError(errReason, -1))
            _notifyEvent(CallEvent.StateMismatch, reasonString = errReason)
            return
        }

        // The accept method defaults to starting a capture and stream push once.
        rtcConnection?.let {
            if (connectInfo.callType != CallType.Audio) {
                config?.rtcEngine?.startPreview()
            }
            val mediaOptions = ChannelMediaOptions()
            mediaOptions.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            mediaOptions.publishCameraTrack = true
            mediaOptions.publishMicrophoneTrack = true
            config?.rtcEngine?.updateChannelMediaOptionsEx(mediaOptions, it)
        }

        connectInfo.set(userId = remoteUserId, roomId = roomId, isLocalAccepted = true)

        val message = _messageDic(CallAction.Accept)
        _sendMessage(remoteUserId.toString(), message) { err ->
            completion?.invoke(err)
            if (err != null) {
                _notifySendMessageErrorEvent(err, "accept fail: ")
            }
        }

        callPrint("[calling]defaultCalleeJoinRTCTiming: ${defaultCalleeJoinRTCTiming.value}")
        if (defaultCalleeJoinRTCTiming == CalleeJoinRTCTiming.Accepted) {
            _joinRTCAsBroadcaster(roomId)
        }
        _updateAndNotifyState(CallStateType.Connecting, CallStateReason.LocalAccepted, eventInfo = message)
        _notifyEvent(CallEvent.LocalAccepted)
    }

    // Reject
    override fun reject(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?) {
        _reportMethod("reject", mapOf("remoteUserId" to remoteUserId, "reason" to (reason ?: "")))
        val message = _rejectMessageDic(reason, rejectByInternal = false)
        _reject(remoteUserId, message) { error ->
            completion?.invoke(error)
            if (error != null) {
                _notifySendMessageErrorEvent(error, "reject fail: ")
            }
        }
        _updateAndNotifyState(CallStateType.Prepared, CallStateReason.LocalRejected, eventInfo = message)
        _notifyEvent(CallEvent.LocalRejected)
    }

    // Hang up
    override fun hangup(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?) {
        _reportMethod("hangup", mapOf("remoteUserId" to remoteUserId))
        val message = _hangupMessageDic(reason)
        _hangup(remoteUserId, message = message) { error ->
            completion?.invoke(error)
            if (error != null) {
                _notifySendMessageErrorEvent(error, "hangup fail: ")
            }
        }
        _updateAndNotifyState(CallStateType.Prepared, CallStateReason.LocalHangup, eventInfo = message)
        _notifyEvent(CallEvent.LocalHangup)
    }

    //MARK: AgoraRtmClientDelegate
    override fun onTokenPrivilegeWillExpire(channelName: String?) {
        _notifyTokenPrivilegeWillExpire()
    }
//    override fun onConnectionFail() {
//        _updateAndNotifyState(CallStateType.Failed, CallStateReason.RtmLost)
//        _notifyEvent(CallEvent.RtmLost)
//    }

    override fun onMessageReceive(message: String) {
        val messageDic = jsonStringToMap(message)
        val messageAction = messageDic[kMessageAction] as? Int ?: 0
        val msgTs = messageDic[kMessageTs] as? Long
        val userId = messageDic[kFromUserId] as? Int
        val messageVersion = messageDic[kMessageVersion] as? String
        if (messageVersion == null || msgTs == null || userId == null) {
            callWarningPrint("fail to parse message: $message")
            return
        }
        //TODO: compatible other message version
        if (kCurrentMessageVersion != messageVersion)  { return }
        callPrint("on event message: $message")
        CallAction.fromValue(messageAction)?.let {
            _processRespEvent(it, messageDic)
        }
    }

    override fun debugInfo(message: String, logLevel: Int) {
        callPrint(message)
    }

    // IRtcEngineEventHandler
    override fun onConnectionStateChanged(state: Int, reason: Int) {
        callPrint("connectionChangedTo state: $state reason: $reason")
    }

    override fun onUserJoined(uid: Int, elapsed: Int) {
        callPrint("didJoinedOfUid: $uid elapsed: $elapsed")
        if (connectInfo.callingUserId == uid) else return
        runOnUiThread {
            _notifyEvent(CallEvent.RemoteJoined)
        }
    }
    override fun onUserOffline(uid: Int, reason: Int) {
        callPrint("didOfflineOfUid: $uid， reason: $reason")
        if (connectInfo.callingUserId != uid) { return }
        runOnUiThread {
            _notifyEvent(CallEvent.RemoteLeft, reasonCode = "$reason")
        }
    }

    override fun onLeaveChannel(stats: RtcStats?) {
        callPrint("didLeaveChannel: $stats")
        isChannelJoined = false
        /*
         Since the transition from leave rtc to onLeaveChannel is asynchronous,
         setting rtcConnection = nil here will cause an immediate join after leave,
         which will incorrectly nullify the rtc connection after joining in onLeaveChannel
         */
        //rtcConnection = null
        runOnUiThread {
            _notifyEvent(CallEvent.LocalLeft)
        }
    }

    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
        callPrint("join RTC channel, didJoinChannel: $uid, channel: $channel elapsed: $elapsed")
        if (uid == config?.userId) else { return }
        isChannelJoined = true
        runOnUiThread {
            joinRtcCompletion?.invoke(null)
            joinRtcCompletion = null
            _notifyEvent(CallEvent.LocalJoined)
        }
    }

    override fun onError(err: Int) {
        runOnUiThread {
            _notifyRtcOccurErrorEvent(err)
        }
    }

    override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
        super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
        val channelId = prepareConfig?.roomId ?: ""
        if (uid != connectInfo.callingUserId) return
        callPrint("didLiveRtcRemoteVideoStateChanged channelId: $channelId/${connectInfo.callingRoomId ?: ""} uid: $uid/${connectInfo.callingUserId ?: 0} state: $state reason: $reason")
        if ((state == 2) && (reason == 6 || reason == 4 || reason == 3 )) {
            runOnUiThread {
                firstFrameCompletion?.invoke()
            }
        }
    }

    private fun jsonStringToMap(jsonString: String): Map<String, Any> {
        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.get(key)
        }
        return map
    }

    private fun callPrint(message: String, logLevel: CallLogLevel = CallLogLevel.Normal) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[CallApi]$message");
        } else {
            delegates.forEach { listener ->
                listener.callDebugInfo(message, logLevel)
            }
        }
        reporter?.writeLog("[CallApi]$message", Constants.LOG_LEVEL_INFO)
    }

    private fun callWarningPrint(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[CallApi]$message");
        } else {
            delegates.forEach { listener ->
                listener.callDebugInfo(message, CallLogLevel.Warning)
            }
        }
        reporter?.writeLog("[CallApi]$message", Constants.LOG_LEVEL_WARNING)
    }

    private val mHandler = Handler(Looper.getMainLooper())
    private fun runOnUiThread(runnable: Runnable) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            runnable.run()
        } else {
            mHandler.post(runnable)
        }
    }
}

