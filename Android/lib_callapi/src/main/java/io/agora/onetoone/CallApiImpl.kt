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
 * Timing for callee to join RTC during call
 * 被叫呼叫中加入RTC的时机
 */
enum class CalleeJoinRTCTiming(val value: Int) {
    // Join channel and push video stream when receiving call, higher cost for callee but faster video display
    // 在收到呼叫时即加入频道并推送视频流，被叫时费用较高但出图更快
    Calling(0),

    // Join channel and push video stream only after actively accepting call, lower cost for callee but slower video display
    // 在收到呼叫后，主动发起接受后才加入频道并推送视频流，被叫时费用较低但出图较慢
    Accepted(1)
}

class CallApiImpl constructor(
    val context: Context
): ICallApi, ISignalClientListener, IRtcEngineEventHandler() {

    companion object {
        const val kReportCategory = "2.1.2"
        const val kPublisher = "publisher"
        // Call timing information that will be output step by step when connected
        // 呼叫时的耗时信息，会在connected时抛出分步耗时
        const val kCostTimeMap = "costTimeMap"
        const val kRemoteUserId = "remoteUserId"
        const val kFromUserId = "fromUserId"
        const val kFromRoomId = "fromRoomId"
        const val kFromUserExtension = "fromUserExtension"

        // ⚠️ Do not modify the following two values, clients may make business decisions based on this rejectReason/call busy (e.g. user busy)
        // ⚠️不允许修改下列两项值，客户可能会根据该rejectReason/call busy 来做业务判断(例如用户忙)
        const val kRejectReason = "rejectReason"
        const val kRejectReasonCallBusy = "The user is currently busy"

        // Whether internally rejected, currently marked as peer call busy when receiving internal rejection
        // 是否内部拒绝，收到内部拒绝目前标记为对端call busy
        const val kRejectByInternal = "rejectByInternal"

        // Whether internally cancelled call, currently marked as remote calling timeout when receiving internal call cancellation
        // 是否内部取消呼叫，收到内部取消呼叫目前标记为对端 remote calling timeout
        const val kCancelCallByInternal = "cancelCallByInternal"

        const val kHangupReason = "hangupReason"

        // Message ID being sent
        // 发送的消息id
        private const val kMessageId = "messageId"
    }

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
    private var isChannelJoined = false
    // Message ID
    // 消息id
    private var messageId: Int = 0

    private var tempRemoteCanvasView = TextureView(context)
    private var tempLocalCanvasView = TextureView(context)
    // Default timing for joining RTC
    // 默认的加入rtc时机
    private var defaultCalleeJoinRTCTiming = CalleeJoinRTCTiming.Calling

    private var reporter: APIReporter? = null

    // Current state
    // 当前状态
    private var state: CallStateType = CallStateType.Idle
        set(value) {
            val prevState = field
            field = value
            if (prevState == value) { return }
            when(value) {
                CallStateType.Calling -> {
                    tempRemoteCanvasView.alpha = 0f
                    // If prepareConfig?.callTimeoutSeconds == 0, no timeout will be set internally
                    // 如果prepareConfig?.callTimeoutSeconds == 0，内部不做超时
                    val timeout = prepareConfig?.callTimeoutMillisecond ?: 0L
                    if (timeout <= 0L) {
                        return
                    }
                    // Start timer, if no response after timeout, call no response
                    // 开启定时器，如果超时无响应，调用no response
                    connectInfo.scheduledTimer({
                        _cancelCall(cancelCallByInternal = true) { }
                        updateAndNotifyState(CallStateType.Prepared, CallStateReason.CallingTimeout)
                        notifyEvent(CallEvent.CallingTimeout)
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
                    muteRemoteAudio(false)
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
                    leaveRTC()
                    connectInfo.clean()
                    isPreparing = false
                }
            }
        }
    /// RTC connection for join channel ex, used for leaving channel ex and checking if already joined ex channel
    /// join channel ex的connection，用来leave channel ex和判断是否已经加入ex channel
    private var rtcConnection: RtcConnection? = null

    // Callback when joining RTC is completed
    // 加入RTC完成回调
    private var joinRtcCompletion: ((AGError?) -> Unit)? = null

    // Callback when first frame of video/audio is rendered
    // 首帧 出图/出声 回调
    private var firstFrameCompletion: (() -> Unit)? = null

    private var isPreparing = false

    init {
        callPrint("init-- CallApiImpl")
    }

    // Get NTP time
    // 获取ntp时间
    private fun getTimeInMs(): Long {
        return System.currentTimeMillis()
    }

    private fun getCost(ts: Int? = null): Long {
        val cts = connectInfo.callTs ?: return 0
        return if (ts != null) {
            ts - cts
        } else {
            getTimeInMs() - cts
        }
    }

    private fun messageDic(action: CallAction): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            kMessageAction to action.value,
            kMessageVersion to kCurrentMessageVersion,
            kMessageTs to getTimeInMs(),
            kFromUserId to (config?.userId ?: 0),
            kCallId to connectInfo.callId
        )
        prepareConfig?.userExtension?.let {
            map[kFromUserExtension] = it
        }
        return map
    }

    private fun callMessageDic(remoteUserId: Int, callType: CallType, fromRoomId: String, callExtension: Map<String, Any>): Map<String, Any> {
        val message = messageDic(action = if(callType == CallType.Video) CallAction.Call else CallAction.AudioCall).toMutableMap()
        message[kRemoteUserId] = remoteUserId
        message[kFromRoomId] = fromRoomId
        var userExtension = message[kFromUserExtension] as? Map<String, Any> ?: emptyMap()
        userExtension = userExtension + callExtension
        message[kFromUserExtension] = userExtension
        return message
    }

    private fun cancelCallMessageDic(cancelByInternal: Boolean): Map<String, Any> {
        val message = messageDic(CallAction.CancelCall).toMutableMap()
        message[kCancelCallByInternal] = if (cancelByInternal) 1 else 0
        return message
    }

    private fun rejectMessageDic(reason: String?, rejectByInternal: Boolean): Map<String, Any> {
        val message = messageDic(CallAction.Reject).toMutableMap()
        message[kRejectReason] = reason ?: ""
        message[kRejectByInternal] = if (rejectByInternal) 1 else 0
        return message
    }

    private fun hangupMessageDic(reason: String?): Map<String, Any> {
        val message = messageDic(CallAction.Hangup).toMutableMap()
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

    private fun canJoinRtcOnCalling(eventInfo: Map<String, Any>): Boolean {
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

        // If no protocol is implemented, use default value
        // 如果一个协议都没有实现，使用默认值
        if (emptyCount == delegates.size) {
            callPrint("join rtc strategy callback not found, use default")
            return true
        }

        return false
    }

    private fun notifyCallConnected() {
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

    private fun notifyCallDisconnected(hangupUserId: Int) {
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

    private fun notifyTokenPrivilegeWillExpire() {
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
        if (firstFrameWaittingDisabled) {
            if (state != CallStateType.Connecting) { return }
        } else {
            if (!connectInfo.isRetrieveFirstFrame || state != CallStateType.Connecting) {return}
        }
        /*
         * 1. Due to callee joining channel and subscribing/publishing stream early, both sides may receive first video frame before callee accepts (becomes connecting)
         * 2. In 1v1 matching, both sides receive onCall, when A initiates accept, B receives onAccept+A's first frame, causing B to enter connected state before accepting
         * Therefore:
         * Becoming connecting: Need to check both "remote accepted" + "local accepted (or initiated call)"
         * Becoming connected: Need to check both "connecting state" + "received first frame"
         *
         * 1.因为被叫提前加频道并订阅流和推流，导致双端收到视频首帧可能会比被叫点accept(变成connecting)比更早
         * 2.由于匹配1v1时双端都会收到onCall，此时A发起accept，B收到了onAccept+A首帧，会导致B未接受即进入了connected状态
         * 因此:
         * 变成connecting: 需要同时检查是否变成了"远端已接受" + "本地已接受(或已发起呼叫)"
         * 变成connected: 需要同时检查是否是"connecting状态" + "收到首帧"
         */
        changeToConnectedState(reason)
    }

    private fun changeToConnectedState(reason: CallStateReason) {
        val eventInfo = mapOf(
            kFromRoomId to (connectInfo.callingRoomId ?: ""),
            kFromRoomId to (connectInfo.callingRoomId ?: ""),
            kFromUserId to (connectInfo.callingUserId ?: 0),
            kRemoteUserId to (config?.userId ?: 0),
            kCostTimeMap to connectInfo.callCostMap
        )
        updateAndNotifyState(CallStateType.Connected, reason, eventInfo = eventInfo)
//        notifyEvent(event: CallReason.RecvRemoteFirstFrame, elapsed: elapsed)
    }
    // External state notification
    // 外部状态通知
    private fun updateAndNotifyState(state: CallStateType,
                                      stateReason: CallStateReason = CallStateReason.None,
                                      eventReason: String = "",
                                      eventInfo: Map<String, Any> = emptyMap()) {
        callPrint("call change[${connectInfo.callId}] state: $state, stateReason: '$stateReason', eventReason: $eventReason")

        val oldState = this.state
        // Check connected/disconnected
        // 检查连接/断开连接状态
        if (state == CallStateType.Connected && oldState == CallStateType.Connecting) {
            notifyCallConnected()
        } else if (state == CallStateType.Prepared && oldState == CallStateType.Connected) {
            when (stateReason) {
                // Normally only .remoteCancel, .remoteHangup will be triggered, others are fallback
                // 正常只会触发.remoteCancel, .remoteHangup，剩余的做兜底
                CallStateReason.RemoteCancelled, CallStateReason.RemoteHangup, CallStateReason.RemoteRejected, CallStateReason.RemoteCallBusy -> {
                    notifyCallDisconnected(connectInfo.callingUserId ?: 0)
                }
                else -> {
                    // .localHangup or bad case
                    // .localHangup 或 bad case
                    notifyCallDisconnected(config?.userId ?: 0)
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
        reportCustomEvent(CallCustomEvent.stateChange, ext)

        this.state = state
        delegates.forEach {
            it.onCallStateChanged(state, stateReason, eventReason, eventInfo)
        }
    }

    private fun notifySendMessageErrorEvent(error: AGError, reason: String?) {
        notifyErrorEvent(
            CallErrorEvent.SendMessageFail,
            errorType = CallErrorCodeType.Message,
            errorCode = error.code,
            message = "${reason ?: ""}${error.msg}"
        )
    }

    private fun notifyRtcOccurErrorEvent(errorCode: Int, message: String? = null) {
        notifyErrorEvent(
            CallErrorEvent.RtcOccurError,
            errorType =  CallErrorCodeType.Rtc,
            errorCode =  errorCode,
            message =  message
        )
    }

    private fun notifyErrorEvent(
        errorEvent: CallErrorEvent,
        errorType: CallErrorCodeType,
        errorCode: Int,
        message: String?) {
        callPrint("call change[${connectInfo.callId} errorEvent: ${errorEvent.value}, errorType: ${errorType.value}, errorCode: ${errorCode}, message: ${message ?: ""}")
        delegates.forEach { listener ->
            listener.onCallError(errorEvent, errorType, errorCode, message)
        }
    }

    private fun notifyEvent(event: CallEvent, reasonCode: String? = null, reasonString: String? = null) {
        callPrint("call change[${connectInfo.callId}] event: ${event.value} reasonCode: '$reasonCode' reasonString: '$reasonString'")
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
            reportCustomEvent(CallCustomEvent.eventChange, ext)

        } ?: callWarningPrint("notifyEvent config == null")
        delegates.forEach { listener ->
            listener.onCallEventChanged(event, reasonCode)
        }
        when (event) {
            CallEvent.RemoteUserRecvCall -> reportCostEvent(CallConnectCostType.RemoteUserRecvCall)
            CallEvent.RemoteJoined -> reportCostEvent(CallConnectCostType.RemoteUserJoinChannel)
            CallEvent.LocalJoined -> reportCostEvent(CallConnectCostType.LocalUserJoinChannel)
            CallEvent.CaptureFirstLocalVideoFrame -> reportCostEvent(CallConnectCostType.LocalFirstFrameDidCapture)
            CallEvent.PublishFirstLocalAudioFrame -> reportCostEvent(CallConnectCostType.LocalFirstFrameDidPublish)
            CallEvent.PublishFirstLocalVideoFrame -> reportCostEvent(CallConnectCostType.LocalFirstFrameDidPublish)
            CallEvent.RemoteAccepted -> {
                reportCostEvent(CallConnectCostType.AcceptCall)
                checkConnectedSuccess(CallStateReason.RemoteAccepted)
            }
            CallEvent.LocalAccepted -> {
                reportCostEvent(CallConnectCostType.AcceptCall)
                checkConnectedSuccess(CallStateReason.LocalAccepted)
            }
            CallEvent.RecvRemoteFirstFrame -> {
                reportCostEvent(CallConnectCostType.RecvFirstFrame)
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

        leaveRTC()
        connectInfo.clean()

        completion?.invoke(null)

        // Different from iOS, Android adds TextureView rendering view to passed container first
        // 和iOS不同，Android先将渲染视图TextureView添加进传进来的容器
        setupTextureView()
    }
    private fun setupTextureView() {
        val prepareConfig = prepareConfig ?: return
        runOnUiThread {
            // Add remote rendering view
            // 添加远端渲染视图
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
                    callPrint("remote canvas already added")
                }
            }
            // Add local rendering view
            // 添加本地渲染视图
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
                    callPrint("local canvas already added")
                }
            }
        }
    }
    private fun deinitialize() {
        updateAndNotifyState(CallStateType.Idle)
        notifyEvent(CallEvent.Deinitialize)
        reporter = null
    }

    // Set remote view
    // 设置远端画面
    private fun setupRemoteVideo(uid: Int) {
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

    private fun removeRemoteVideo(uid: Int) {
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
        // 添加远端渲染视图
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

    private fun setupLocalVideo() {
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
            notifyErrorEvent(CallErrorEvent.StartCaptureFail, CallErrorCodeType.Rtc, ret, null)
        }
    }

    private fun removeLocalVideo() {
        if (connectInfo.callType == CallType.Audio) return
        val engine = config?.rtcEngine ?: run {
            callWarningPrint("_setupLocalVideo fail: engine is empty")
            return
        }
        val canvas = VideoCanvas(tempLocalCanvasView)
        canvas.setupMode = VideoCanvas.VIEW_SETUP_MODE_REMOVE
        engine.setupLocalVideo(canvas)
    }

    /// 判断当前加入的RTC频道和传入的房间id是否一致
    /// - Parameter roomId: <#roomId description#>
    /// - Returns: <#description#>
    private fun isCurrentRTCChannel(roomId: String): Boolean {
        return rtcConnection?.channelId == roomId
    }

    /// 当前RTC频道是否加入成功或者正在加入中
    /// - Returns: <#description#>
    private fun isChannelJoinedOrJoining(): Boolean {
        return rtcConnection != null
    }

    /// 是否初始化完成
    /// - Returns: <#description#>
    private fun isInitialized(): Boolean {
        return when (state) {
            CallStateType.Idle, CallStateType.Failed -> false
            else -> true
        }
    }

    private fun isCallingUser(message: Map<String, Any>) : Boolean {
        val fromUserId = message[kFromUserId] as? Int ?: return false
        if (connectInfo.callingUserId != fromUserId) return false
        return true
    }

    private fun joinRTCWithMediaOptions(roomId: String, completion: ((AGError?) -> Unit)) {
        if (!isCurrentRTCChannel(roomId)) {
            leaveRTC()
        }
        val isChannelJoinedOrJoining = isChannelJoinedOrJoining()
        if (isChannelJoinedOrJoining) {
            completion.invoke(null)
        } else {
            joinRTC(roomId){ error ->
                completion.invoke(error)
            }
        }
        val publishVideo = connectInfo.callType != CallType.Audio
        val subscribeVideo = connectInfo.callType != CallType.Audio

        updatePublishStatus(audioStatus = true, videoStatus = publishVideo)
        updateSubscribeStatus(audioStatus = true, videoStatus = subscribeVideo)

        // Mute audio after joining channel, unmute after connecting
        // 加入频道后先静音，等connecting后才解除静音
        muteRemoteAudio(true)
    }

    private fun joinRTCAsBroadcaster(roomId: String) {
        joinRTCWithMediaOptions(roomId) { error ->
            if (error != null) {
                notifyRtcOccurErrorEvent(error.code, error.msg)
            } else {
                notifyEvent(CallEvent.JoinRTCSuccessed)
            }
        }
        setupCanvas()
    }

    private fun joinRTC(roomId: String, completion:((AGError?) -> Unit)?) {
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
        val ret: Int = config.rtcEngine.joinChannelEx(rtcToken, connection, mediaOptions, this)
        callPrint("joinRTC channel roomId: $roomId uid: ${config.userId} ret = $ret")
        rtcConnection = connection
        joinRtcCompletion = {
            completion?.invoke(null)
        }
        firstFrameCompletion = {
            connectInfo.isRetrieveFirstFrame = true
            notifyEvent(CallEvent.RecvRemoteFirstFrame)
        }
        if (ret != Constants.ERR_OK) {
            notifyRtcOccurErrorEvent(ret)
        }
        notifyEvent(CallEvent.JoinRTCStart)

        reporter?.startDurationEvent(ApiCostEvent.FIRST_FRAME_ACTUAL)
    }

    /**
     * Update audio/video stream publishing status
     * 更新推送音视频流状态
     * @param audioStatus Whether to publish audio stream
     *                   是否推送音频流
     * @param videoStatus Whether to publish video stream
     *                   是否推送视频流
     */
    private fun updatePublishStatus(audioStatus: Boolean, videoStatus: Boolean) {
        val config = config
        val connection = rtcConnection
        if (config == null || connection == null) { return}
        callPrint("updatePublishStatus, audioStatus$audioStatus videoStatus:$videoStatus")

        config.rtcEngine.enableLocalAudio(audioStatus)
        config.rtcEngine.enableLocalVideo(videoStatus)

        val mediaOptions = ChannelMediaOptions()
        mediaOptions.publishCameraTrack = videoStatus
        mediaOptions.publishMicrophoneTrack = audioStatus
        config.rtcEngine.updateChannelMediaOptionsEx(mediaOptions, connection)
    }

    /**
     * Update audio/video stream subscription status
     * 更新音视频流订阅状态
     * @param audioStatus Audio stream subscription status
     *                   音频流订阅状态
     * @param videoStatus Video stream subscription status
     *                   视频流订阅状态
     */
    private fun updateSubscribeStatus(audioStatus: Boolean, videoStatus: Boolean) {
        val config = config ?: run { return }
        val connection = rtcConnection ?: run { return }
        callPrint("updateSubscribeStatus, audioStatus$audioStatus, videoStatus:$videoStatus")
        val mediaOptions = ChannelMediaOptions()
        mediaOptions.autoSubscribeAudio = audioStatus
        mediaOptions.autoSubscribeVideo = videoStatus
        config.rtcEngine.updateChannelMediaOptionsEx(mediaOptions, connection)
    }

    private fun muteRemoteAudio(isMute: Boolean) {
        val rtcEngine = config?.rtcEngine ?: return
        val connection = rtcConnection ?: return

        val uid = connectInfo.callingUserId
        uid?.let { it ->
            callPrint("muteRemoteAudio: $isMute uid: $it channelId: ${connection.channelId}")
            rtcEngine.adjustUserPlaybackSignalVolumeEx(it, if (isMute) 0 else 100, connection)
        }
    }

    private fun leaveRTC() {
        joinRtcCompletion = null
        val connection = rtcConnection ?: run {
            //callWarningPrint("leave RTC channel failed, not joined the channel")
            return
        }
        cleanCanvas()
        updatePublishStatus(audioStatus = false, videoStatus = false)
        config?.rtcEngine?.stopPreview()
        val ret = config?.rtcEngine?.leaveChannelEx(connection)
        callPrint("leave RTC channel[${ret ?: -1}]")
        rtcConnection = null
    }

    private fun setupCanvas() {
        setupLocalVideo()
        val callingUserId = connectInfo.callingUserId ?: run {
            callWarningPrint("setupCanvas fail: callingUserId == null")
            return
        }
        setupRemoteVideo(callingUserId)
    }

    private fun cleanCanvas() {
        removeLocalVideo()
        val callingUserId = connectInfo.callingUserId ?: run {
            callWarningPrint("cleanCanvas fail: callingUserId == null")
            return
        }
        removeRemoteVideo(callingUserId)
    }

    private fun reportCostEvent(type: CallConnectCostType) {
        val cost = getCost()
        connectInfo.callCostMap[type.value] = cost
        val ext = mapOf(
            "channelName" to (connectInfo.callingRoomId ?: ""),
            "callId" to connectInfo.callId,
            "userId" to (config?.userId ?: 0)
        )
        reporter?.reportCostEvent(type.value, cost.toInt(), ext)
    }

    private fun reportMethod(event: String, ext: Map<String, Any>? = null) {
        val value = ext ?: mapOf()
        callPrint("reportMethod event: $event value: $value")
        var subEvent = event
        val range = event.indexOf("(")
        if (range != -1) {
            subEvent = event.substring(0, range)
        }

        val extension = mapOf<String, Any>(
            "callId" to connectInfo.callId,
            "userId" to (config?.userId ?: 0)
        )
        reporter?.reportFuncEvent(
            name = subEvent,
            value = value,
            ext = extension
        )
    }

    private fun reportCustomEvent(event: String, ext: Map<String, Any>) {
        callPrint("reportMethod event: $event value: $ext")
        reporter?.reportCustomEvent(
            name = event,
            ext = ext
        )
    }

    private fun sendMessage(
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
    private fun processRespEvent(reason: CallAction, message: Map<String, Any>) {
        when (reason) {
            CallAction.Call ->          onCall(message, CallType.Video)
            CallAction.AudioCall ->     onCall(message, CallType.Audio)
            CallAction.CancelCall ->    onCancel(message)
            CallAction.Reject ->        onReject(message)
            CallAction.Accept ->        onAccept(message)
            CallAction.Hangup ->        onHangup(message)
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
        //Send call message
        connectInfo.set(
            callType = callType,
            userId = remoteUserId,
            roomId = fromRoomId,
            callId = UUID.randomUUID().toString(),
            isLocalAccepted = true
        )

        val message = callMessageDic(
            remoteUserId = remoteUserId,
            callType = callType,
            fromRoomId = fromRoomId,
            callExtension = callExtension
        )
        sendMessage(remoteUserId.toString(), message) { err ->
            completion?.invoke(err)
            if (err != null) {
                //updateAndNotifyState(CallStateType.Prepared, CallReason.MessageFailed, err.msg)
                notifySendMessageErrorEvent(err, "call fail: ")
                //return@sendMessage
            } else {
                notifyEvent(CallEvent.RemoteUserRecvCall)
            }
        }

        val reason = if (callType == CallType.Video) CallStateReason.LocalVideoCall else CallStateReason.LocalAudioCall
        val event = if (callType == CallType.Video) CallEvent.LocalVideoCall else CallEvent.LocalAudioCall
        updateAndNotifyState(CallStateType.Calling, reason, eventInfo = message)
        notifyEvent(event)
        joinRTCAsBroadcaster(fromRoomId)
    }

    private fun _cancelCall(message: Map<String, Any>? = null, cancelCallByInternal: Boolean = false, completion: ((AGError?) -> Unit)? = null) {
        val userId = connectInfo.callingUserId
        if (userId == null) {
            completion?.invoke(AGError("cancelCall fail! callingRoomId is empty", -1))
            callWarningPrint("cancelCall fail! callingRoomId is empty")
            return
        }
        val msg = message ?: cancelCallMessageDic(cancelCallByInternal)
        sendMessage(userId.toString(), msg) { err ->
            completion?.invoke(err)
            if (err != null) {
                notifySendMessageErrorEvent(err, "cancel call fail: ")
            }
        }
    }

    private fun _reject(remoteUserId: Int, message: Map<String, Any>, completion: ((AGError?) -> Unit)? = null) {
        sendMessage(remoteUserId.toString(), message, completion)
    }

    private fun _hangup(remoteUserId: Int, message: Map<String, Any>? = null, completion: ((AGError?) -> Unit)? = null) {
        sendMessage(remoteUserId.toString(), message ?: messageDic(CallAction.Hangup), completion)
    }

    // Received call message
    // 收到呼叫消息
    private fun onCall(message: Map<String, Any>, callType: CallType) {
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
                    val reason = rejectMessageDic(kRejectReasonCallBusy, rejectByInternal = true)
                    _reject(fromUserId, reason)
                    return
                }
                if (state == CallStateType.Calling) {
                    enableNotify = false
                }
            }
            else -> {}
        }

        connectInfo.set(callType, fromUserId, fromRoomId, callId)
        defaultCalleeJoinRTCTiming = if (canJoinRtcOnCalling(eventInfo = message)) CalleeJoinRTCTiming.Calling else CalleeJoinRTCTiming.Accepted
        if (enableNotify) {
            val reason = if (callType == CallType.Video) CallStateReason.RemoteVideoCall else CallStateReason.RemoteAudioCall
            val event = if (callType == CallType.Video) CallEvent.RemoteVideoCall else CallEvent.RemoteAudioCall
            updateAndNotifyState(CallStateType.Calling, reason, eventInfo = message)
            notifyEvent(event)
        }
        callPrint("[calling]defaultCalleeJoinRTCTiming: ${defaultCalleeJoinRTCTiming.value}")
        if(defaultCalleeJoinRTCTiming == CalleeJoinRTCTiming.Calling) {
            joinRTCAsBroadcaster(fromRoomId)
        }

        if (connectInfo.isLocalAccepted && prepareConfig?.firstFrameWaittingDisabled == true) {
            // If first frame is not associated, in show-to-1v1 scenario, auto-accept may occur, causing connected state before joining channel and unmute audio becomes invalid
            // 如果首帧不关联，在秀场转1v1场景下，可能会自动接受，会导致么有加频道前变成connected，unmute声音无效
            checkConnectedSuccess(CallStateReason.LocalAccepted)
        }
    }

    private fun onCancel(message: Map<String, Any>) {
        // If the operation is not from the user who is currently calling, ignore it
        // 如果不是来自的正在呼叫的用户的操作，不处理
        if (!isCallingUser(message)) return

        var stateReason: CallStateReason = CallStateReason.RemoteCancelled
        var callEvent: CallEvent = CallEvent.RemoteCancelled
        val cancelCallByInternal = message[kCancelCallByInternal] as? Int
        if (cancelCallByInternal == 1) {
            stateReason = CallStateReason.RemoteCallingTimeout
            callEvent = CallEvent.RemoteCallingTimeout
        }
        updateAndNotifyState(state = CallStateType.Prepared, stateReason = stateReason, eventInfo = message)
        notifyEvent(event = callEvent)
    }

    private fun onReject(message: Map<String, Any>) {
        if (!isCallingUser(message)) return
        var stateReason: CallStateReason =  CallStateReason.RemoteRejected
        var callEvent: CallEvent = CallEvent.RemoteRejected
        val rejectByInternal = message[kRejectByInternal]
        if (rejectByInternal == 1) {
            stateReason = CallStateReason.RemoteCallBusy
            callEvent = CallEvent.RemoteCallBusy
        }

        updateAndNotifyState(CallStateType.Prepared, stateReason, eventInfo = message)
        notifyEvent(callEvent)
    }

    private fun onAccept(message: Map<String, Any>) {
        // Must be in calling state and request must come from the calling user
        // 需要是calling状态，并且来自呼叫的用户的请求
        if (!isCallingUser(message) || state != CallStateType.Calling) return
        // Must be isLocalAccepted (initiated call or already accepted), otherwise considered not locally agreed
        // 并且是isLocalAccepted（发起呼叫或者已经accept过了），否则认为本地没有同意
        if (connectInfo.isLocalAccepted) {
            updateAndNotifyState(CallStateType.Connecting, CallStateReason.RemoteAccepted, eventInfo = message)
        }
        notifyEvent(CallEvent.RemoteAccepted)
    }

    private fun onHangup(message: Map<String, Any>) {
        if (!isCallingUser(message)) return

        updateAndNotifyState(CallStateType.Prepared, CallStateReason.RemoteHangup, eventInfo = message)
        notifyEvent(CallEvent.RemoteHangup)
    }

    //MARK: CallApiProtocol
    override fun getCallId(): String {
        reportMethod("getCallId")
        return connectInfo.callId
    }

    override fun initialize(config: CallConfig) {
        if (state != CallStateType.Idle) {
            callWarningPrint("must invoke 'deinitialize' to clean state")
            return
        }

        reporter = APIReporter(APIType.CALL, kReportCategory, config.rtcEngine)
        reportMethod("initialize", mapOf("appId" to config.appId, "userId" to config.userId))
        this.config = config.cloneConfig()

        // Video best practices
        // 视频最佳实践

        // 3. API enable first frame acceleration rendering for audio and video
        // API 开启音视频首帧加速渲染
//        config.rtcEngine.enableInstantMediaRendering()

        // 4. Enable first frame FEC through private parameters or configuration delivery
        // 私有参数或配置下发开启首帧 FEC
        config.rtcEngine.setParameters("{\"rtc.video.quickIntraHighFec\": true}")

        // 5. Set AUT CC mode through private parameters or configuration delivery
        // 私有参数或配置下发设置 AUT CC mode
        config.rtcEngine.setParameters("{\"rtc.network.e2e_cc_mode\": 3}")  //(Not needed for version 4.3.0 and later, default value changed to 3)
        //(4.3.0及以后版本不需要设置此项，默认值已改为3)

        // 6. Set VQC resolution adjustment sensitivity through private parameters or configuration delivery
        // 私有参数或配置下发设置VQC分辨率调节的灵敏度
        config.rtcEngine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomin\": 1000}")
        config.rtcEngine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomout\": 1000}")
    }

    override fun deinitialize(completion: (() -> Unit)) {
        reportMethod("deinitialize")
        when (state) {
            CallStateType.Calling -> {
                cancelCall { err ->
                    completion.invoke()
                }
                deinitialize()
            }
            CallStateType.Connecting, CallStateType.Connected -> {
                val callingUserId = connectInfo.callingUserId ?: 0
                _hangup(callingUserId) { err ->
                    completion.invoke()
                }
                deinitialize()
            }
            else -> {
                deinitialize()
                completion.invoke()
            }
        }
    }

    override fun renewToken(rtcToken: String) {
        reportMethod("renewToken")
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
        notifyEvent(event = CallEvent.PublishFirstLocalVideoFrame, reasonString = "elapsed: ${elapsed}ms")
    }

    override fun onFirstLocalVideoFrame(
        source: Constants.VideoSourceType?,
        width: Int,
        height: Int,
        elapsed: Int
    ) {
        super.onFirstLocalVideoFrame(source, width, height, elapsed)
        notifyEvent(event = CallEvent.CaptureFirstLocalVideoFrame, reasonString = "elapsed: ${elapsed}ms")
        config?.rtcEngine?.removeHandler(localFrameProxy)
    }

    override fun onFirstLocalAudioFramePublished(elapsed: Int) {
        super.onFirstLocalAudioFramePublished(elapsed)
        notifyEvent(CallEvent.PublishFirstLocalAudioFrame, reasonString = "elapsed: ${elapsed}ms")
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
        reportMethod("prepareForCall", mapOf("roomId" to prepareConfig.roomId))
        _prepareForCall(prepareConfig) { err ->
            if (err != null) {
                updateAndNotifyState(CallStateType.Failed, CallStateReason.RtmSetupFailed, err.msg)
                completion?.invoke(err)
                return@_prepareForCall
            }
            updateAndNotifyState(CallStateType.Prepared)
            completion?.invoke(null)
        }
    }

    override fun addListener(listener: ICallApiListener) {
        reportMethod("addListener")
        if (delegates.contains(listener)) { return }
        delegates.add(listener)
    }

    override fun removeListener(listener: ICallApiListener) {
        reportMethod("removeListener")
        delegates.remove(listener)
    }

    override fun call(remoteUserId: Int, completion: ((AGError?) -> Unit)?) {
        _call(
            remoteUserId = remoteUserId,
            callType = CallType.Video,
            callExtension = emptyMap(),
            completion = completion
        )
        reportMethod("call", mapOf("remoteUserId" to remoteUserId))
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
        reportMethod("call", mapOf("remoteUserId" to remoteUserId, "callType" to callType.value, "callExtension" to callExtension))
    }

    override fun cancelCall(completion: ((AGError?) -> Unit)?) {
        reportMethod("cancelCall")
        val message = messageDic(CallAction.CancelCall)
        _cancelCall(message, false, completion)
        updateAndNotifyState(CallStateType.Prepared, CallStateReason.LocalCancelled, eventInfo = message)
        notifyEvent(CallEvent.LocalCancelled)
    }

    // Accept
    // 接受
    override fun accept(remoteUserId: Int, completion: ((AGError?) -> Unit)?) {
        reportMethod("accept", mapOf("remoteUserId" to remoteUserId))
        val fromUserId = config?.userId
        val roomId = connectInfo.callingRoomId
        if (fromUserId == null || roomId == null) {
            val errReason = "accept fail! current userId or roomId is empty"
            completion?.invoke(AGError(errReason, -1))
            return
        }
        // Check if state is calling, if it's prepared, it means the caller may have cancelled
        // 查询是否是calling状态，如果是prapared，表示可能被主叫取消了
        if (state != CallStateType.Calling) {
            val errReason = "accept fail! current state is $state not calling"
            completion?.invoke(AGError(errReason, -1))
            notifyEvent(CallEvent.StateMismatch, reasonString = errReason)
            return
        }

        // By default, start capture and streaming within accept
        // accept内默认启动一次采集+推流
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

        // First check if the callee in presence is self, if so, don't send message again
        // 先查询presence里是不是正在呼叫的被叫是自己，如果是则不再发送消息
        val message = messageDic(CallAction.Accept)
        sendMessage(remoteUserId.toString(), message) { err ->
            completion?.invoke(err)
            if (err != null) {
                notifySendMessageErrorEvent(err, "accept fail: ")
            }
        }

        callPrint("[accepted]defaultCalleeJoinRTCTiming: ${defaultCalleeJoinRTCTiming.value}")
        if (defaultCalleeJoinRTCTiming == CalleeJoinRTCTiming.Accepted) {
            joinRTCAsBroadcaster(roomId)
        }
        updateAndNotifyState(CallStateType.Connecting, CallStateReason.LocalAccepted, eventInfo = message)
        notifyEvent(CallEvent.LocalAccepted)
    }

    // Reject call
    // 拒绝
    override fun reject(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?) {
        reportMethod("reject", mapOf("remoteUserId" to remoteUserId, "reason" to (reason ?: "")))
        val message = rejectMessageDic(reason, rejectByInternal = false)
        _reject(remoteUserId, message) { error ->
            completion?.invoke(error)
            if (error != null) {
                notifySendMessageErrorEvent(error, "reject fail: ")
            }
        }
        updateAndNotifyState(CallStateType.Prepared, CallStateReason.LocalRejected, eventInfo = message)
        notifyEvent(CallEvent.LocalRejected)
    }

    // Hang up call
    // 挂断
    override fun hangup(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?) {
        reportMethod("hangup", mapOf("remoteUserId" to remoteUserId))
        val message = hangupMessageDic(reason)
        _hangup(remoteUserId, message = message) { error ->
            completion?.invoke(error)
            if (error != null) {
                notifySendMessageErrorEvent(error, "hangup fail: ")
            }
        }
        updateAndNotifyState(CallStateType.Prepared, CallStateReason.LocalHangup, eventInfo = message)
        notifyEvent(CallEvent.LocalHangup)
    }

    //MARK: AgoraRtmClientDelegate
    override fun onTokenPrivilegeWillExpire(channelName: String?) {
        notifyTokenPrivilegeWillExpire()
    }
//    override fun onConnectionFail() {
//        updateAndNotifyState(CallStateType.Failed, CallStateReason.RtmLost)
//        notifyEvent(CallEvent.RtmLost)
//    }

    override fun onMessageReceive(message: String) {
        callPrint("on event message: $message")
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
        CallAction.fromValue(messageAction)?.let {
            processRespEvent(it, messageDic)
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
        if (connectInfo.callingUserId != uid) return
        runOnUiThread {
            notifyEvent(CallEvent.RemoteJoined)
        }
    }
    override fun onUserOffline(uid: Int, reason: Int) {
        callPrint("didOfflineOfUid: $uid， reason: $reason")
        if (connectInfo.callingUserId != uid) { return }
        runOnUiThread {
            notifyEvent(CallEvent.RemoteLeft, reasonCode = "$reason")
        }
    }

    override fun onLeaveChannel(stats: RtcStats?) {
        callPrint("didLeaveChannel: $stats")
        isChannelJoined = false
        /*
         * Since leave RTC to didLeaveChannelWith is asynchronous
         * Setting rtcConnection = nil here will cause didLeaveChannelWith to incorrectly clear the rtc connection after join if joining immediately after leaving
         *
         * 由于leave rtc到didLeaveChannelWith是异步的
         * 这里rtcConnection = nil会导致leave之后马上join，didLeaveChannelWith会在join之后错误的置空了rtc connection
         */
        //rtcConnection = null
        runOnUiThread {
            notifyEvent(CallEvent.LocalLeft)
        }
    }

    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
        callPrint("join RTC channel, didJoinChannel: $uid, channel: $channel elapsed: $elapsed")
        if (uid != config?.userId) { return }
        isChannelJoined = true
        runOnUiThread {
            joinRtcCompletion?.invoke(null)
            joinRtcCompletion = null
            notifyEvent(CallEvent.LocalJoined)
        }
    }

    override fun onError(err: Int) {
        runOnUiThread {
            notifyRtcOccurErrorEvent(err)
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

