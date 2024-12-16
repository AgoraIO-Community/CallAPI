package io.agora.onetoone

import android.view.ViewGroup
import io.agora.onetoone.signalClient.ISignalClient
import io.agora.rtc2.RtcEngineEx

open class CallConfig(
    // Agora App Id
    var appId: String = "",
    // User ID used for sending signaling messages
    var userId: Int = 0,
    // RTC engine instance
    var rtcEngine: RtcEngineEx,
    // ISignalClient instance
    var signalClient: ISignalClient
){}
open class PrepareConfig(
    // Own RTC channel name, used when calling remote user to join this RTC channel
    var roomId: String = "",
    // RTC token, needs to use universal token, channel name should be empty string when creating token
    var rtcToken: String = "",
    // Canvas for displaying local stream
    var localView: ViewGroup? = null,
    // Canvas for displaying remote stream
    var remoteView: ViewGroup? = null,
    // Call timeout duration in milliseconds, if set to 0 no internal timeout logic will be applied
    var callTimeoutMillisecond: Long = 15000L,
    // [Optional] User extension field, can be retrieved through kFromUserExtension field when receiving remote messages that change state (e.g. calling/connecting)
    var userExtension: Map<String, Any>? = null,
    // Whether to disable waiting for first frame in connected state, true: yes, caller considers call successful upon receiving accept message, callee considers call successful upon clicking accept.
    // Note: using this method may result in connection without audio/video permissions and no video display due to poor network, false: no, will wait for audio first frame (audio call) or video first frame (video call)
    var firstFrameWaittingDisabled: Boolean = false
) {}

/**
 * Call type
 */
enum class CallType(val value: Int) {
    Video(0),
    Audio(1)
}

/**
 * Call state type
 */
enum class CallStateType(val value: Int) {
    Idle(0),            // Idle
    Prepared(1),        // 1v1 environment creation completed
    Calling(2),         // In calling state
    Connecting(3),      // In connecting state
    Connected(4),       // In call
    Failed(10);         // Error occurred

    companion object {
        fun fromValue(value: Int): CallStateType {
            return values().find { it.value == value } ?: Idle
        }
    }
}

/*
 * Call state transition reason
 */
enum class CallStateReason(val value: Int) {
    None(0),
    JoinRTCFailed(1),           // Failed to join RTC
    RtmSetupFailed(2),          // Failed to setup RTM
    RtmSetupSuccessed(3),       // Successfully setup RTM
    MessageFailed(4),           // Message sending failed
    LocalRejected(5),           // Local user rejected
    RemoteRejected(6),          // Remote user rejected
    RemoteAccepted(7),          // Remote user accepted
    LocalAccepted(8),           // Local user accepted
    LocalHangup(9),             // Local user hung up
    RemoteHangup(10),           // Remote user hung up
    LocalCancelled(11),         // Local user cancelled call
    RemoteCancelled(12),        // Remote user cancelled call
    RecvRemoteFirstFrame(13),   // Received remote first frame (video frame for video call, audio frame for audio call)
    CallingTimeout(14),         // Call timeout
    CancelByCallerRecall(15),   // Call cancelled due to same caller calling different channel
    RtmLost(16),                // RTM connection timeout
    RemoteCallBusy(17),         // Remote user busy
    RemoteCallingTimeout(18),   // Remote call timeout
    LocalVideoCall(30),         // Local initiated video call
    LocalAudioCall(31),         // Local initiated audio call
    RemoteVideoCall(32),        // Remote initiated video call
    RemoteAudioCall(33),        // Remote initiated audio call
}

/*
 * Call event
 */
enum class CallEvent(val value: Int) {
    None(0),
    Deinitialize(1),                        // Called deinitialize
    //MissingReceipts(2),                   // No message receipt received [Deprecated]
    CallingTimeout(3),                      // Call timeout
    RemoteCallingTimeout(4),                // Remote call timeout
    JoinRTCSuccessed(5),                    // RTC joined successfully
    //RtmSetupFailed(6),                    // RTM setup failed [Deprecated, please use onCallErrorOccur(state: rtmSetupFail)]
    RtmSetupSuccessed(7),                   // RTM setup successfully
    //MessageFailed(8),                     // Message sending failed [Deprecated, please use onCallErrorOccur(state: sendMessageFail)]
    StateMismatch(9),                       // State transition exception
    JoinRTCStart(10),                       // Local user has joined RTC channel but not yet successful (JoinChannelEx called)
    RemoteUserRecvCall(99),                 // Caller call successful
    LocalRejected(100),                     // Local user rejected
    RemoteRejected(101),                    // Remote user rejected
    OnCalling(102),                         // Changed to calling state [2.1.0 deprecated, please refer to localVideoCall/localAudioCall/remoteVideoCall/remoteAudioCall]
    RemoteAccepted(103),                    // Remote user accepted
    LocalAccepted(104),                     // Local user accepted
    LocalHangup(105),                       // Local user hung up
    RemoteHangup(106),                      // Remote user hung up
    RemoteJoined(107),                      // Remote user joined RTC channel
    RemoteLeft(108),                        // Remote user left RTC channel, RTC channel (eventReason please refer to AgoraUserOfflineReason)
    LocalCancelled(109),                    // Local user cancelled call
    RemoteCancelled(110),                   // Remote user cancelled call
    LocalJoined(111),                       // Local user joined RTC channel
    LocalLeft(112),                         // Local user left RTC channel
    RecvRemoteFirstFrame(113),              // Received remote first frame
    //CancelByCallerRecall(114),            // Call cancelled due to same caller calling different channel [Deprecated]
    RtmLost(115),                           // RTM connection timeout
    //RtcOccurError(116),                   // RTC error occurred [Deprecated, please use onCallErrorOccur(state: rtcOccurError)]
    RemoteCallBusy(117),                    // Remote user busy
    //StartCaptureFail(118),                // Start capture failed [Deprecated, please use onCallErrorOccur(state: startCaptureFail)]
    CaptureFirstLocalVideoFrame(119),       // Captured first video frame
    PublishFirstLocalVideoFrame(120),       // Published first video frame successfully
    PublishFirstLocalAudioFrame(130),       // Published first audio frame successfully [2.1.0 supported]
    LocalVideoCall(140),                    // Local initiated video call
    LocalAudioCall(141),                    // Local initiated audio call
    RemoteVideoCall(142),                   // Remote initiated video call
    RemoteAudioCall(142),                   // Remote initiated audio call
}

/*
 * Call error event
 */
enum class CallErrorEvent(val value: Int) {
    NormalError(0),             // General error
    RtcOccurError(100),         // RTC error occurred
    StartCaptureFail(110),      // RTC start capture failed
    // RtmSetupFail(200),       // RTM initialization failed [Deprecated, replaced by messageManager manually initializing]
    SendMessageFail(210)        // Message error, if using CallRtmMessageManager, it is AgoraRtmErrorCode, for custom channel, it is the corresponding error code of the channel
}

/*
 * Call error event error code type
 */
enum class CallErrorCodeType(val value: Int) {
    Normal(0),                  // Business type error, temporarily no
    Rtc(1),                     // RTC error, using AgoraErrorCode
    Message(2)                  // RTM error, using AgoraRtmErrorCode
}

/*
 * Log level
 */
enum class CallLogLevel(val value: Int) {
    Normal(0),
    Warning(1),
    Error(2),
}

interface ICallApiListener {
    /**
     * State response callback
     * @param state State type
     * @param stateReason State transition reason
     * @param eventReason Event type description
     * @param eventInfo Extended information, different parameters for different event types, where key is "publisher" for the state change initiator id, empty means it's your own state change
     */
    fun onCallStateChanged(state: CallStateType,
                           stateReason: CallStateReason,
                           eventReason: String,
                           eventInfo: Map<String, Any>)

    /**
     * Internal detailed event change callback
     * @param event Event
     * @param eventReason Event reason, default null, represents different meanings according to different events
     */
    fun onCallEventChanged(event: CallEvent, eventReason: String?) {}

    /**
     * Internal detailed event change callback
     * @param errorEvent Error event
     * @param errorType Error type
     * @param errorCode Error code
     * @param message Error message
     */
    fun onCallError(errorEvent: CallErrorEvent,
                    errorType: CallErrorCodeType,
                    errorCode: Int,
                    message: String?) {}

    /**
     * Call start callback
     * @param roomId Call channel id
     * @param callerUserId Caller user id
     * @param currentUserId Current user id
     * @param timestamp Call start time, the difference from January 1, 1970, in ms
     */
    fun onCallConnected(roomId: String,
                        callUserId: Int,
                        currentUserId: Int,
                        timestamp: Long) {}

    /**
     * Call end callback
     * @param roomId Call channel id
     * @param hangupUserId User id hung up
     * @param currentUserId Current user id
     * @param timestamp Call start time, the difference from January 1, 1970, in ms
     * @param duration Call duration, in ms
     */
    fun onCallDisconnected(roomId: String,
                           hangupUserId: Int,
                           currentUserId: Int,
                           timestamp: Long,
                           duration: Long) {}

    /**
     * When calling, determine whether to join RTC
     * @param eventInfo Extended information received when calling
     * @return true: can join, false: cannot join
     */
    fun canJoinRtcOnCalling(eventInfo: Map<String, Any>) : Boolean?

    /**
     * Token is about to expire (external token needs to be obtained and updated)
     */
    fun tokenPrivilegeWillExpire() {}

    /** Log callback
     *  @param message: Log information
     *  @param logLevel: Log priority: 0: normal log, 1: warning log, 2: error log
     */
    fun callDebugInfo(message: String, logLevel: CallLogLevel) {}
}

data class AGError(
    val msg: String,
    val code: Int
)

interface ICallApi {

    /**
     * Initialize configuration
     * @param config
     */
    fun initialize(config: CallConfig)

    /**
     * Release cache
     */
    fun deinitialize(completion: (() -> Unit))

    /**
     * Update your own RTC/RTM token
     */
    fun renewToken(rtcToken: String)

    /**
     * Prepare call environment, need to call successfully before making a call. If you need to change the RTC channel number of the call, you can repeat the call to ensure that it is called when the call is not in progress (not calling, connecting, connected)
     * @param prepareConfig
     * @param completion
     */
    fun prepareForCall(prepareConfig: PrepareConfig, completion: ((AGError?) -> Unit)?)

    /**
     * Add callback listener
     * @param listener
     */
    fun addListener(listener: ICallApiListener)

    /**
     * Remove callback listener
     * @param listener
     */
    fun removeListener(listener: ICallApiListener)

    /**
     * Initiate a call invitation, caller calls, establish RTC call connection with the remote user through the RTC channel number set by prepareForCall, default video call
     * @param remoteUserId Called user id
     * @param completion
     */
    fun call(remoteUserId: Int, completion: ((AGError?) -> Unit)?)

    /**
     * Initiate a call invitation, caller calls, establish RTC call connection with the remote user through the RTC channel number set by prepareForCall, default video call
     * @param remoteUserId Called user id
     * @param callType Call type: 0: video call, 1: audio call
     * @param callExtension Call extension field, can be retrieved through kFromUserExtension field when receiving remote messages that change state (e.g. calling/connecting)
     * @param completion
     */
    fun call(remoteUserId: Int, callType: CallType, callExtension: Map<String, Any>, completion: ((AGError?) -> Unit)?)

    /**
     * Cancel the ongoing call, caller calls
     * @param completion
     */
    fun cancelCall(completion: ((AGError?) -> Unit)?)

    /** Accept call, caller calls, caller will receive onAccept
     *
     * @param remoteUserId: Called user id
     * @param completion: <#completion description#>
     */
    fun accept(remoteUserId: Int, completion: ((AGError?) -> Unit)?)

    /**
     * Accept call, callee calls
     * @param remoteUserId Called user id
     * @param reason Rejection reason
     * @param completion
     */
    fun reject(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?)

    /**
     * End call, both caller and callee can call
     * @param remoteUserId User id hung up
     * @param reason Hung up reason
     * @param completion
     */
    fun hangup(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?)

    /**
     * Get the callId of the current call, callId is the unique identifier for the current call process, through which the Agora backend service can query the key node duration and state transition time nodes of the current call
     * @return callId, empty if it's not a call message
     */
    fun getCallId(): String
}