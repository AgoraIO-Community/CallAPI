package io.agora.onetoone

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import io.agora.callapi.BuildConfig
import io.agora.onetoone.extension.*
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtm.*
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
    Hangup(4);
    companion object {
        fun fromValue(value: Int): CallAction {
            return CallAction.values().find { it.value == value } ?: Call
        }
    }
}

/// 被叫时加入RTC的策略
enum class CalleeJoinRTCType(val value: Int) {
    OnCall(0),      //在接到呼叫时即加入频道并推送音视频流，被叫时费用较高但出图更快
    Accept(1)       //在点击接受时才加入频道并推送音视频流，被叫时费用较低但出图较慢
}

class CallApiImpl constructor(
    private val context: Context
): ICallApi, RtmEventListener, CallMessageListener, IRtcEngineEventHandler() {

    companion object {
        val calleeJoinRTCType = CalleeJoinRTCType.OnCall
        val kReportCategory = "2_Android_0.4.0"
        val kPublisher = "publisher"
        val kCostTimeMap = "costTimeMap"    //呼叫时的耗时信息，会在connected时抛出分步耗时
        val kRemoteUserId = "remoteUserId"
        val kFromUserId = "fromUserId"
        val kFromRoomId = "fromRoomId"
        val kFromUserExtension = "fromUserExtension"
    }

    private val kCallTimeoutInterval: Long = 15000
    private val kCurrentMessageVersion = "1.0"
    private val kMessageAction = "message_action"
    private val kMessageVersion = "message_version"
    private val kMessageTs = "message_timestamp"
    private val kCallId = "callId"

    private val TAG = "CallApiImpl_LOG"
    private val delegates = mutableListOf<ICallApiListener>()
    private val rtcProxy = CallProxy()
    private var config: CallConfig? = null
    private var prepareConfig: PrepareConfig? = null
    private var messageManager: CallMessageManager? = null
        set(value) {
            val oldValue = field
            field = value
            oldValue?.deinitialize()
        }
    private var connectInfo = CallConnectInfo()
    private var reportInfoList = listOf<CallReportInfo>()
    private var isChannelJoined = false

    private var tempRemoteCanvasView = TextureView(context)
    private var tempLocalCanvasView = TextureView(context)

    /// 当前状态
    private var state: CallStateType = CallStateType.Idle
        set(value) {
            val prevState = field
            field = value
            if (prevState == value) { return }
            (tempRemoteCanvasView.parent as? ViewGroup)?.let { parentView ->
                parentView.removeView(tempRemoteCanvasView)
            }
            when(value) {
                CallStateType.Calling -> {
                    val localView = prepareConfig?.localView
                    if (localView != null && (localView.indexOfChild(tempLocalCanvasView) == -1)) {
                        tempLocalCanvasView.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        localView.addView(tempLocalCanvasView)
                    } else {
                        callWarningPrint("remote view not found in connected state!")
                    }
                    //开启定时器，如果超时无响应，调用no response
                    connectInfo.scheduledTimer({
                        cancelCall {  }
                        _updateAndNotifyState(CallStateType.Prepared, CallReason.CallingTimeout)
                        _notifyEvent(CallEvent.CallingTimeout)
                    }, kCallTimeoutInterval)
                }
                CallStateType.Prepared -> {
                    connectInfo.scheduledTimer(null)
                    if (prevState != CallStateType.Idle) {
                        _prepareForCall(prepareConfig!!) {
                        }
                    }
                }
                CallStateType.Connecting -> {
                    _updateAutoSubscribe(CallAutoSubscribeType.AudioVideo)
                }
                CallStateType.Connected -> {
                    connectInfo.scheduledTimer(null)
                    val remoteView = prepareConfig?.remoteView
                    if (remoteView != null && (remoteView.indexOfChild(tempLocalCanvasView) == -1)) {
                        tempRemoteCanvasView.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        remoteView.addView(tempRemoteCanvasView)
                    } else {
                        callWarningPrint("remote view not found in connected state!")
                    }
                }
                CallStateType.Idle, CallStateType.Failed -> {
                    _leaveRTC()
                    connectInfo.clean()
                    config = null
                    isPreparing = false
                    messageManager = null
                }
                else -> {}
            }
        }
    /// join channel ex的connection，用来leave channel ex和判断是否已经加入ex channel
    private var rtcConnection: RtcConnection? = null
    //加入RTC完成回调
    private var joinRtcCompletion: ((AGError?) -> Unit)? = null
    //首帧出图回调
    private var firstFrameCompletion: (() -> Unit)? = null

    private var isPreparing = false

    init {
        callPrint("init-- CallApiImpl")
        rtcProxy.addListener(this)
    }
    //获取ntp时间
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
        return mapOf<String, Any>(
            kMessageAction to action.value,
            kMessageVersion to kCurrentMessageVersion,
            kMessageTs to _getTimeInMs(),
            kFromUserId to (config?.userId ?: 0),
            kCallId to connectInfo.callId
        )
    }

    private fun _callMessageDic(remoteUserId: Int, fromRoomId: String): Map<String, Any> {
        val message = _messageDic(CallAction.Call).toMutableMap()
        message[kRemoteUserId] = remoteUserId
        message[kFromRoomId] = fromRoomId
        return message
    }

    private fun _notifyTokenPrivilegeWillExpire() {
        delegates.forEach { listener ->
            listener.tokenPrivilegeWillExpire()
        }
    }

    private fun checkConnectedSuccess(reason: CallReason) {
        if (connectInfo.isRetrieveFirstFrame && state == CallStateType.Connecting) else {return}
        //因为被叫提前加频道并订阅流和推流，导致双端收到视频首帧可能会比被叫点accept(变成connecting)比更早，所以需要检查是否变成了connecting，两者都满足才是conneced
        _changeToConnectedState(reason)
    }

    private fun _changeToConnectedState(reason: CallReason) {
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
    //外部状态通知
    private fun _updateAndNotifyState(state: CallStateType,
                                      stateReason: CallReason = CallReason.None,
                                      eventReason: String = "",
                                      eventInfo: Map<String, Any> = emptyMap()) {
        callPrint("call change[${connectInfo.callId}] state: $state, stateReason: '$stateReason', eventReason: $eventReason")
        this.state = state
        delegates.forEach {
            it.onCallStateChanged(state, stateReason, eventReason, eventInfo)
        }
    }

    private fun _notifyEvent(event: CallEvent, eventReason: String? = null) {
        callPrint("call change[${connectInfo.callId}] event: ${event.value} reason: '$eventReason'")
        config?.let { config ->
            var reason = ""
            if (eventReason != null) {
                reason = "&reason=$eventReason"
            }
            _reportEvent("event=${event.value}&userId=${config.userId}&state=${state.name}$reason", 0)
        } ?: callWarningPrint("_notifyEvent config == null")
        delegates.forEach { listener ->
            listener.onCallEventChanged(event)
        }
        when (event) {
            CallEvent.RemoteUserRecvCall -> _reportCostEvent(CallConnectCostType.RemoteUserRecvCall)
            CallEvent.RemoteJoin -> _reportCostEvent(CallConnectCostType.RemoteUserJoinChannel)
            CallEvent.LocalJoin -> _reportCostEvent(CallConnectCostType.LocalUserJoinChannel)
            CallEvent.RemoteAccepted -> {
                _reportCostEvent(CallConnectCostType.AcceptCall)
                checkConnectedSuccess(CallReason.RemoteAccepted)
            }
            CallEvent.LocalAccepted -> {
                _reportCostEvent(CallConnectCostType.AcceptCall)
                checkConnectedSuccess(CallReason.LocalAccepted)
            }
            CallEvent.RecvRemoteFirstFrame -> {
                _reportCostEvent(CallConnectCostType.RecvFirstFrame)
                checkConnectedSuccess(CallReason.RecvRemoteFirstFrame)
            }
            else -> {}
        }
    }

    private fun _prepareForCall(prepareConfig: PrepareConfig, completion: ((AGError?) -> Unit)?) {
        val cfg = config
        val rtmClient = config?.rtmClient
        if (cfg == null || rtmClient == null) {
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
        var enableLoginRTM = true
        when (state) {
            CallStateType.Calling, CallStateType.Connecting, CallStateType.Connected -> {
                val reason = "currently busy"
                callWarningPrint(reason)
                completion?.invoke(AGError(reason, -1))
                return
            }
            CallStateType.Prepared -> {
                enableLoginRTM = false
            }
            CallStateType.Failed, CallStateType.Idle -> {
            }
        }
        connectInfo.clean()

        val tag = UUID.randomUUID().toString()
        callPrint("prepareForCall[$tag]")
        this.prepareConfig = prepareConfig.cloneConfig()

        //join rtc if need
        if (prepareConfig.autoJoinRTC) {
            _joinRTCWithMediaOptions(prepareConfig.roomId, Constants.CLIENT_ROLE_AUDIENCE, CallAutoSubscribeType.Video) { err ->
                callWarningPrint("prepareForCall[$tag] joinRTC completion: ${err?.msg ?: "success"}")
                _notifyEvent(if (err == null) CallEvent.JoinRTCSuccessed else CallEvent.JoinRTCFailed)
            }
        } else {
            _leaveRTC()
        }
        //login rtm if need
        if (enableLoginRTM) {
            isPreparing = true
            val messageManager = CallMessageManager(cfg, this)
            this.messageManager = messageManager

            messageManager.initialize(prepareConfig) { err ->
                isPreparing = false
                callWarningPrint("prepareForCall[$tag] rtmInitialize completion: ${err?.msg ?: "success"})")
                _notifyEvent(if (err == null) CallEvent.RtmSetupSuccessed else CallEvent.RtmSetupFailed)
                completion?.invoke(err)
            }
        } else {
            completion?.invoke(null)
        }
    }
    private fun _deinitialize() {
        _updateAndNotifyState(CallStateType.Idle)
        _notifyEvent(CallEvent.Deinitialize)
    }
    private fun _setupRemoteVideo(uid: Int, view: TextureView) {
        val engine = config?.rtcEngine ?: return
        val connection = rtcConnection ?: run {
            callWarningPrint("_setupRemoteVideo fail: connection or engine is empty")
            return
        }
        val videoCanvas = VideoCanvas(view)
        videoCanvas.uid = uid
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN
        videoCanvas.mirrorMode = Constants.VIDEO_MIRROR_MODE_AUTO
        val ret = engine.setupRemoteVideoEx(videoCanvas, connection)
        callPrint("_setupRemoteVideo ret: $ret, channelId: ${connection.channelId}, uid: $uid")
    }

    private fun _setupLocalVideo(uid: Int, view: TextureView) {
        val engine = config?.rtcEngine ?: run {
            callWarningPrint("_setupLocalVideo fail: engine is empty")
            return
        }
        val videoCanvas = VideoCanvas(view)
        videoCanvas.uid = uid
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN
        videoCanvas.mirrorMode = Constants.VIDEO_MIRROR_MODE_AUTO

        engine.setDefaultAudioRoutetoSpeakerphone(true)
        engine.setupLocalVideo(videoCanvas)
        engine.startPreview()
    }

    private fun _notifyRTCState(err: AGError?) {
        if (err != null) {
            _updateAndNotifyState(CallStateType.Failed, CallReason.JoinRTCFailed, err.msg)
            _notifyEvent(CallEvent.JoinRTCFailed)
        } else {
            _notifyEvent(CallEvent.JoinRTCSuccessed)
        }
    }

    /// 判断当前加入的RTC频道和传入的房间id是否一致
    /// - Parameter roomId: <#roomId description#>
    /// - Returns: <#description#>
    private fun _isCurrentRTCChannel(roomId: String): Boolean {
        return rtcConnection?.channelId == roomId
    }

    /// 当前RTC频道是否加入成功或者正在加入中
    /// - Returns: <#description#>
    private fun _isChannelJoinedOrJoining(): Boolean {
        return rtcConnection != null
    }

    /// 是否初始化完成
    /// - Returns: <#description#>
    private fun _isInitialized(): Boolean {
        return when (state) {
            CallStateType.Idle, CallStateType.Failed -> false
            else -> true
        }
    }

    /// 是否可以继续呼叫
    /// - Parameter callerUserId: <#callerUserId description#>
    /// - Returns: <#description#>
    private fun _isCallActive(callerUserId: Int): Boolean {
        when (state) {
            CallStateType.Prepared -> return true
            CallStateType.Idle, CallStateType.Failed -> return false
            CallStateType.Calling, CallStateType.Connecting, CallStateType.Connected -> {
                if ((connectInfo.callingUserId ?: 0) == callerUserId) {
                    return true
                }
            }
        }
        return false
    }

    private fun _joinRTCWithMediaOptions(roomId: String, role: Int, subscribeType: CallAutoSubscribeType, completion: ((AGError?) -> Unit)) {
        if (!_isCurrentRTCChannel(roomId)) {
            _leaveRTC()
        }
        if (!_isChannelJoinedOrJoining()) {
            _joinRTC(roomId){ error ->
                completion.invoke(error)
            }
        } else {
            completion.invoke(null)
        }
        _updateRole(role)
        _updateAutoSubscribe(subscribeType)
    }

    private fun _joinRTCAsBroadcaster(roomId: String) {
        _joinRTCWithMediaOptions(roomId, Constants.CLIENT_ROLE_BROADCASTER, CallAutoSubscribeType.Video) { error ->
            _notifyRTCState(error)
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
        val ret: Int = config.rtcEngine?.joinChannelEx(rtcToken, connection, mediaOptions, rtcProxy) ?: 0
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
            callWarningPrint("join rtc fail: $ret!")
            _notifyEvent(CallEvent.RtcOccurError, "$ret")
        }
    }

    /// 切换主播和观众角色
    /// - Parameter role: <#role description#>
    private fun _updateRole(role: Int) {
        val config = config
        val connection = rtcConnection
        if (config == null || connection == null) { return}
        //需要先开启音视频，使用enableLocalAudio而不是enableAudio，否则会导致外部mute的频道变成unmute
        if (role == Constants.CLIENT_ROLE_BROADCASTER) {
            config.rtcEngine?.enableLocalAudio(true)
            config.rtcEngine?.enableLocalVideo(true)
        } else {
//            config.rtcEngine?.enableLocalAudio(false)
//            config.rtcEngine?.enableLocalVideo(false)
        }
        val mediaOptions = ChannelMediaOptions()
        mediaOptions.clientRoleType = role
        val isBroadcaster = (role == Constants.CLIENT_ROLE_BROADCASTER)
        mediaOptions.publishCameraTrack = isBroadcaster
        mediaOptions.publishMicrophoneTrack = isBroadcaster
        config.rtcEngine?.updateChannelMediaOptionsEx(mediaOptions, connection)
    }

    /// 更换订阅音视频流策略
    /// - Parameter type: <#type description#>
    private fun _updateAutoSubscribe(type: CallAutoSubscribeType) {
        val config = config ?: run { return }
        val connection = rtcConnection ?: run { return }
        val mediaOptions = ChannelMediaOptions()
        when (type) {
            CallAutoSubscribeType.None -> {
                mediaOptions.autoSubscribeAudio = false
                mediaOptions.autoSubscribeVideo = false
            }
            CallAutoSubscribeType.Video -> {
                mediaOptions.autoSubscribeAudio = false
                mediaOptions.autoSubscribeVideo = true
            }
            CallAutoSubscribeType.AudioVideo -> {
                mediaOptions.autoSubscribeAudio = true
                mediaOptions.autoSubscribeVideo = true
            }
        }
        config.rtcEngine?.updateChannelMediaOptionsEx(mediaOptions, connection)
    }

    private fun _leaveRTC() {
        joinRtcCompletion = null
        val connection = rtcConnection ?: run {
            callWarningPrint("leave RTC channel failed, not joined the channel")
            return
        }
        config?.rtcEngine?.stopPreview()
        val ret = config?.rtcEngine?.leaveChannelEx(connection)
        callPrint("leave RTC channel[${ret ?: -1}]")
        rtcConnection = null
    }

    private fun setupCanvas() {
        val config = config ?: return
        _setupLocalVideo(config.userId, tempLocalCanvasView)
        val callingUserId = connectInfo.callingUserId ?: run {
            callWarningPrint("join rtc fail: callingUserId == nil")
            return
        }
        _setupRemoteVideo(callingUserId, tempRemoteCanvasView)
    }

    private fun _flushReport() {
        reportInfoList.forEach { info ->
            _sendCustomReportMessage(info.msgId, info.category, info.event, info.label, info.value)
        }
        reportInfoList = emptyList()
    }
    private fun _reportCostEvent(type: CallConnectCostType) {
        val cost = _getCost()
        connectInfo.callCostMap[type.value] = cost
        _reportEvent(type.value, cost.toInt())
    }

    private fun _reportMethod(event: String, label: String = "") {
        val msgId = "scenarioAPI"
        callPrint("_reportMethod event: $event")
        var subEvent = event
        val range = event.indexOf("(")
        if (range != -1) {
            subEvent = event.substring(0, range)
        }
        var labelValue = "callId=${connectInfo.callId}&ts=${_getTimeInMs()}"
        if (label.isNotEmpty()) {
            labelValue = "$label&$labelValue"
        }
        if (isChannelJoined) {
            _sendCustomReportMessage(msgId, kReportCategory, subEvent, labelValue, 0)
            return
        }

        val info = CallReportInfo(msgId, kReportCategory, subEvent, labelValue, 0)
        val temp = reportInfoList.toMutableList()
        temp.add(info)
        reportInfoList = temp.takeLast(10)
        // callPrint("sendCustomReportMessage not join channel cache it! event: $subEvent label: $labelValue")
    }

    private fun _reportEvent(key: String, value: Int) {
        val config = config ?: return
        val msgId = "uid=${config.userId}&roomId=${connectInfo.callingRoomId ?: ""}"
        val label = "callId=${connectInfo.callId})&ts=${_getTimeInMs()}"
        if (isChannelJoined) {
            _sendCustomReportMessage(msgId, kReportCategory, key, label, value)
            return
        }
        val info = CallReportInfo(msgId, kReportCategory, key, label, value)
        val temp = reportInfoList.toMutableList()
        temp.add(info)
        reportInfoList = temp.takeLast(10)
//        callPrint("sendCustomReportMessage not join channel cache it! msgId: $msgId category: $category event: $key label: ${connectInfo.callId} value: $value")
    }

    private fun _sendCustomReportMessage(msgId: String,
                                         category: String,
                                         event: String,
                                         label: String,
                                         value: Int) {
        val c = config
        if (c != null && isChannelJoined && rtcConnection != null) else { return }
        c.rtcEngine?.sendCustomReportMessageEx(msgId, category, event, label, value, rtcConnection)
    }

    //MARK: on Message
    private fun _processRespEvent(reason: CallAction, message: Map<String, Any>) {
        when (reason) {
            CallAction.Call ->          _onCall(message)
            CallAction.CancelCall ->    _onCancel(message)
            CallAction.Reject ->        _onReject(message)
            CallAction.Accept ->        _onAccept(message)
            CallAction.Hangup ->        _onHangup(message)
            else -> {}
        }
    }
    private fun _reject(remoteUserId: Int, reason: String?, completion: ((AGError?, Map<String, Any>) -> Unit)? = null) {
        val fromUserId = config?.userId
        if (fromUserId == null) {
            completion?.invoke(AGError("reject fail! current userId or roomId is empty", -1), emptyMap())
            callWarningPrint("reject fail! current userId or roomId is empty")
            return
        }
        val message = _messageDic(CallAction.Reject)
        messageManager?.sendMessage(remoteUserId.toString(), fromUserId.toString(), message) { error ->
            completion?.invoke(error, message)
        }
    }

    private fun _hangup(remoteUserId: String, completion: ((AGError?, Map<String, Any>) -> Unit)? = null) {
        val fromUserId = config?.userId ?: run {
            completion?.invoke(AGError("reject fail! current roomId is empty", -1), emptyMap())
            callWarningPrint("reject fail! current roomId is empty")
            return
        }
        val message = _messageDic(CallAction.Hangup)
        messageManager?.sendMessage(remoteUserId, fromUserId.toString(), message) { err ->
            completion?.invoke(err, message)
        }
    }
    //收到呼叫消息
    private fun _onCall(message: Map<String, Any>) {
        val fromRoomId = message[kFromRoomId] as String
        val fromUserId = message[kFromUserId] as Int
        val callId = message[kCallId] as String

        var enableNotify = true
        var autoAccept = prepareConfig?.autoAccept ?: false
        when (state) {
            CallStateType.Idle, CallStateType.Failed -> {
                // not reachable
//            _reject(remoteUserId: fromUserId, reason: "callee is currently on call")
                return
            }
            CallStateType.Calling, CallStateType.Connecting, CallStateType.Connected -> {
                if ((connectInfo.callingUserId ?: 0) != fromUserId) {
                    _reject(fromUserId, "callee is currently on call")
                    return
                }
                if (state == CallStateType.Calling) {
                    enableNotify = false
                } else {
                    autoAccept = true
                }
            }
            else -> {}
        }

        connectInfo.set(fromUserId, fromRoomId, callId)
        if (enableNotify) {
            val eventInfo = mapOf<String, Any>(
                kFromRoomId to fromRoomId,
                kFromUserId to fromUserId,
                kRemoteUserId to (config?.userId ?: 0)
            )
            _updateAndNotifyState(CallStateType.Calling, CallReason.None, eventInfo = eventInfo)
            _notifyEvent(CallEvent.OnCalling)
        }
        if(calleeJoinRTCType == CalleeJoinRTCType.OnCall) {
            _joinRTCAsBroadcaster(fromRoomId)
        }

        if (!autoAccept) {
            return
        }
        accept(fromUserId) { err ->
        }
    }

    private fun _onCancel(message: Map<String, Any>) {
        //如果不是接收的正在接听的用户的呼叫
        val fromUserId = message[kFromUserId] as? Int ?: return
        if (connectInfo.callingUserId != fromUserId) { return }
        _updateAndNotifyState(CallStateType.Prepared, CallReason.RemoteCancel, eventInfo = message)
        _notifyEvent(CallEvent.RemoteCancel)
    }

    private fun _onReject(message: Map<String, Any>) {
        val fromUserId = message[kFromUserId] as? Int
        if (fromUserId == null || connectInfo.callingUserId != fromUserId) { return }
        _updateAndNotifyState(CallStateType.Prepared, CallReason.RemoteRejected, eventInfo = message)
        _notifyEvent(CallEvent.RemoteRejected)
    }

    private fun _onAccept(message: Map<String, Any>) {
        if (state != CallStateType.Calling) {
            return
        }
        if (state == CallStateType.Calling) {
            _updateAndNotifyState(CallStateType.Connecting, CallReason.RemoteAccepted)
        }
        _notifyEvent(CallEvent.RemoteAccepted)
    }

    private fun _onHangup(message: Map<String, Any>) {
        val fromUserId = message[kFromUserId] as? Int
        val callId = message[kCallId] as? String
        if (fromUserId == null || fromUserId != connectInfo.callingUserId || callId == null) { return }
        if (callId != connectInfo.callId) {
            callWarningPrint("onHangup fail: callId missmatch")
            return
        }
        _updateAndNotifyState(CallStateType.Prepared, CallReason.RemoteHangup)
        _notifyEvent(CallEvent.RemoteHangup)
    }

    //MARK: CallApiProtocol
    override fun getCallId(): String {
        _reportMethod("getCallId")
        return connectInfo.callId
    }

    override fun initialize(config: CallConfig) {
        _reportMethod("initialize", "appId=${config.appId}&userId=${config.userId}")
        if (state != CallStateType.Idle) {
            callWarningPrint("must invoke 'deinitialize' to clean state")
            return
        }
        this.config = config.cloneConfig()
    }

    override fun deinitialize(completion: (() -> Unit)) {
        _reportMethod("deinitialize")
        when (state) {
            CallStateType.Calling -> {
                cancelCall { err ->
                    _deinitialize()
                    completion.invoke()
                }
            }
            CallStateType.Connecting, CallStateType.Connected -> {
                val callingUserId = connectInfo.callingUserId ?: 0
                _hangup(callingUserId.toString()) { err, message ->
                    _deinitialize()
                    completion.invoke()
                }
            }
            else -> {
                _deinitialize()
                completion.invoke()
            }
        }
    }

    override fun renewToken(rtcToken: String, rtmToken: String) {
        _reportMethod("renewToken", "&rtcToken=${rtcToken}&rtmToken=${rtmToken}")
        val roomId = prepareConfig?.roomId
        if (roomId == null) {
            callWarningPrint("renewToken failed, roomid missmatch")
            return
        }
        prepareConfig?.rtcToken = rtcToken
        prepareConfig?.rtmToken = rtmToken
        callPrint("renewToken with roomId[$roomId]")
        messageManager?.renewToken(rtcToken, rtmToken)
        val connection = rtcConnection ?: return
        val options = ChannelMediaOptions()
        options.token = rtcToken
        val ret = this.config?.rtcEngine?.updateChannelMediaOptionsEx(options, connection)
        callPrint("rtc[$roomId] renewToken ret = ${ret ?: -1}")
    }

    override fun prepareForCall(prepareConfig: PrepareConfig, completion: ((AGError?) -> Unit)?) {
        _reportMethod("prepareForCall", "roomId=${prepareConfig.roomId}&autoJoinRTC=${prepareConfig.autoJoinRTC}")
        _prepareForCall(prepareConfig) { err ->
            if (err != null) {
                _updateAndNotifyState(CallStateType.Failed, CallReason.RtmSetupFailed, err.msg)
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
        //发送呼叫消息
        connectInfo.set(remoteUserId, fromRoomId, UUID.randomUUID().toString())
        //ensure that the report log contains a call
        _reportMethod("call", "remoteUserId=$remoteUserId")

        val message = _callMessageDic(remoteUserId, fromRoomId)
        messageManager?.sendMessage(remoteUserId.toString(), fromUserId.toString(), message) { err ->
            if (err != null) {
                _updateAndNotifyState(CallStateType.Prepared, CallReason.MessageFailed, err.msg)
                _notifyEvent(CallEvent.MessageFailed)
                return@sendMessage
            }
            _notifyEvent(CallEvent.RemoteUserRecvCall)
        }
        _updateAndNotifyState(CallStateType.Calling, eventInfo = message)
        _notifyEvent(CallEvent.OnCalling)
        _joinRTCAsBroadcaster(fromRoomId)
    }

    override fun cancelCall(completion: ((AGError?) -> Unit)?) {
        _reportMethod("cancelCall")
        val userId = connectInfo.callingUserId
        val fromUserId = config?.userId
        if (userId == null || fromUserId == null) {
            completion?.invoke(AGError("cancelCall fail! callingRoomId is empty", -1))
            callWarningPrint("cancelCall fail! callingRoomId is empty")
            return
        }
        val message = _messageDic(CallAction.CancelCall)
        messageManager?.sendMessage(userId.toString(), fromUserId.toString(), message) { err ->
        }
        _updateAndNotifyState(CallStateType.Prepared, CallReason.LocalCancel)
        _notifyEvent(CallEvent.LocalCancel)
    }
    //接受
    override fun accept(remoteUserId: Int, completion: ((AGError?) -> Unit)?) {
        _reportMethod("accept", "remoteUserId=$remoteUserId")
        val fromUserId = config?.userId
        val roomId = connectInfo.callingRoomId
        if (fromUserId == null || roomId == null) {
            val errReason = "accept fail! current userId or roomId is empty"
            completion?.invoke(AGError(errReason, -1))
            callWarningPrint(errReason)
            _updateAndNotifyState(CallStateType.Prepared, CallReason.MessageFailed, errReason)
            _notifyEvent(CallEvent.MessageFailed)
            return
        }
        //查询是否是calling状态，如果是prapared，表示可能被主叫取消了
        if (state == CallStateType.Calling) else {
            val errReason = "accept fail! current state is $state not calling"
            completion?.invoke(AGError(errReason, -1))
            callWarningPrint(errReason)
            _updateAndNotifyState(CallStateType.Prepared, CallReason.None, errReason)
            _notifyEvent(CallEvent.StateMismatch)
            return
        }
        //先查询presence里是不是正在呼叫的被叫是自己，如果是则不再发送消息
        val message = _messageDic(CallAction.Accept)
        messageManager?.sendMessage(remoteUserId.toString(), fromUserId.toString(), message) { err ->
        }
        _updateAndNotifyState(CallStateType.Connecting, CallReason.LocalAccepted, eventInfo = message)
        _notifyEvent(CallEvent.LocalAccepted)

        connectInfo.set(remoteUserId, roomId)

        if (calleeJoinRTCType == CalleeJoinRTCType.Accept) {
            _joinRTCAsBroadcaster(roomId)
        }
    }

    override fun reject(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?) {
        _reportMethod("reject", "remoteUserId=$remoteUserId&reason=$reason")
        _reject(remoteUserId, reason)
        _updateAndNotifyState(CallStateType.Prepared, CallReason.LocalRejected)
        _notifyEvent(CallEvent.LocalRejected)
    }

    override fun hangup(remoteUserId: Int, completion: ((AGError?) -> Unit)?) {
        _reportMethod("hangup", "remoteUserId=$remoteUserId")
        _hangup(remoteUserId.toString())
        _updateAndNotifyState(CallStateType.Prepared, CallReason.LocalHangup)
        _notifyEvent(CallEvent.LocalHangup)
    }
//    override fun addRTCListener(listener: IRtcEngineEventHandler) {
//        _reportMethod("addRTCListener")
//        rtcProxy.addListener(listener)
//    }
//    override fun removeRTCListener(listener: IRtcEngineEventHandler) {
//        _reportMethod( "removeRTCListener")
//        rtcProxy.removeListener(listener)
//    }

    //MARK: AgoraRtmClientDelegate
    override fun onTokenPrivilegeWillExpire(channelName: String?) {
        _notifyTokenPrivilegeWillExpire()
    }
    override fun onConnectionFail() {
        _updateAndNotifyState(CallStateType.Failed, CallReason.RtmLost)
        _notifyEvent(CallEvent.RtmLost)
    }
    override fun onMessageEvent(event: MessageEvent?) {
        val message = event?.message?.data as? ByteArray ?: return
        val jsonString = String(message, Charsets.UTF_8)
        val map = jsonStringToMap(jsonString)
        val messageAction = map[kMessageAction] as? Int ?: 0
        val msgTs = map[kMessageTs] as? Long
        val userId = map[kFromUserId] as? Int
        val messageVersion = map[kMessageVersion] as? String
        if (messageVersion == null || msgTs == null || userId == null) {
            callWarningPrint("fail to parse message: $jsonString")
            return
        }
        //TODO: compatible other message version
        if (kCurrentMessageVersion != messageVersion)  { return }
        _processRespEvent(CallAction.fromValue(messageAction), map)
    }
    override fun debugInfo(message: String, logLevel: Int) {
        callPrint(message)
    }
    override fun onMissReceipts(message: Map<String, Any>) {
        callWarningPrint("onMissReceipts: $message")
        _notifyEvent(CallEvent.MissingReceipts)
    }
    override fun onPresenceEvent(event: PresenceEvent?) {}
    override fun onTopicEvent(event: TopicEvent?) {}
    override fun onLockEvent(event: LockEvent?) {}
    override fun onStorageEvent(event: StorageEvent?) {}
    // IRtcEngineEventHandler
    override fun onConnectionStateChanged(state: Int, reason: Int) {
        callPrint("connectionChangedTo state: $state reason: $reason")
    }
    override fun onUserJoined(uid: Int, elapsed: Int) {
        callPrint("didJoinedOfUid: $uid elapsed: $elapsed")
        if (connectInfo.callingUserId == uid) else return
        _notifyEvent(CallEvent.RemoteJoin)
    }
    override fun onUserOffline(uid: Int, reason: Int) {
        callPrint("didOfflineOfUid: $uid")
        if (connectInfo.callingUserId != uid) { return }
        _notifyEvent(CallEvent.RemoteLeave)
    }
    override fun onLeaveChannel(stats: RtcStats?) {
        callPrint("didLeaveChannel: $stats")
        isChannelJoined = false
        _notifyEvent(CallEvent.LocalLeave)
    }

    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
        callPrint("join RTC channel, didJoinChannel: $uid, channel: $channel elapsed: $elapsed")
        if (uid == config?.userId) else { return }
        isChannelJoined = true
        _flushReport()
        runOnUiThread {
            joinRtcCompletion?.invoke(null)
            joinRtcCompletion = null
            _notifyEvent(CallEvent.LocalJoin)
        }
    }

    override fun onError(err: Int) {
        callWarningPrint("didOccurError: $err")
//        joinRtcCompletion?.invoke(AGError("join RTC fail", err))
//        joinRtcCompletion = null
        _notifyEvent(CallEvent.RtcOccurError, "$err")
    }

    override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
        super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
        val channelId = prepareConfig?.roomId ?: ""
        if (uid == connectInfo.callingUserId) else {return}
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
    }

    private fun callWarningPrint(message: String) {
        delegates.forEach { listener ->
            listener.callDebugInfo(message, CallLogLevel.Warning)
        }
        callPrint("[Warning]$message")
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

