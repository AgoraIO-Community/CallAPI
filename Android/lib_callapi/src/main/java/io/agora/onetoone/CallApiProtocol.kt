package io.agora.onetoone

import android.view.ViewGroup
import io.agora.onetoone.signalClient.ISignalClient
import io.agora.rtc2.RtcEngineEx

open class CallConfig(
    // Agora App Id
    var appId: String = "",
    // User ID, used to send signaling messages
    var userId: Int = 0,
    // RTC engine instance
    var rtcEngine: RtcEngineEx,
    // ISignalClient instance
    var signalClient: ISignalClient
){}

open class PrepareConfig(
    var roomId: String = "",                      // Your own RTC channel name, used to let the remote user join this RTC channel when calling
    var rtcToken: String = "",                    // RTC token, must use a universal token, the channel name should be an empty string when creating the token
    var localView: ViewGroup? = null,             // Canvas to display the local stream
    var remoteView: ViewGroup? = null,            // Canvas to display the remote stream
    var callTimeoutMillisecond: Long = 15000L,    // Call timeout duration in milliseconds; if 0 is passed, no timeout logic will be applied internally
    var userExtension: Map<String, Any>? = null,  // [Optional] User extension fields; can be used to retrieve the kFromUserExtension field when receiving messages from the remote user that change state (e.g., calling/connecting)
    var firstFrameWaittingDisabled: Boolean = false  // Whether to disable waiting for the first frame in the connected state; true: yes, the caller considers the call successful upon receiving the acceptance message, and the callee considers the call successful upon clicking accept; note that using this method may allow the call to connect without audio and video permissions, and due to weak network conditions, the video may not be visible; false: no, it will wait for the first audio frame (audio call) or the first video frame (video call)
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
    Calling(2),         // Calling
    Connecting(3),      // Connecting
    Connected(4),       // In a call
    Failed(10);         // An error occurred

    companion object {
        fun fromValue(value: Int): CallStateType {
            return values().find { it.value == value } ?: Idle
        }
    }
}

/*
 * Reasons for call state transitions
 */
enum class CallStateReason(val value: Int) {
    None(0),
    JoinRTCFailed(1),           // Failed to join RTC
    RtmSetupFailed(2),          // RTM setup failed
    RtmSetupSuccessed(3),       // RTM setup succeeded
    MessageFailed(4),           // Message sending failed
    LocalRejected(5),           // Local user rejected
    RemoteRejected(6),          // Remote user rejected
    RemoteAccepted(7),          // Remote user accepted
    LocalAccepted(8),           // Local user accepted
    LocalHangup(9),             // Local user hung up
    RemoteHangup(10),           // Remote user hung up
    LocalCancelled(11),         // Local user canceled the call
    RemoteCancelled(12),        // Remote user canceled the call
    RecvRemoteFirstFrame(13),   // Received the first frame from remote (for video call, it's the first video frame; for audio call, it's the first audio frame)
    CallingTimeout (14),        // Call timed out
    CancelByCallerRecall(15),   // The same caller calling different channels results in cancellation
    RtmLost(16),                // RTM timeout disconnection
    RemoteCallBusy(17),         // Remote user is busy
    RemoteCallingTimeout(18),   // Remote call timed out
    LocalVideoCall(30),         // Local initiated video call
    LocalAudioCall(31),         // Local initiated audio call
    RemoteVideoCall(32),        // Remote initiated video call
    RemoteAudioCall(33),        // Remote initiated audio call
}

/*
 * Call events
 */
enum class CallEvent(val value: Int) {
    None(0),
    Deinitialize(1),                // Called deinitialize
    //MissingReceipts(2),             // No message receipts received [deprecated]
    CallingTimeout(3),              // Call timed out
    RemoteCallingTimeout(4),        // Cloud call timed out
    JoinRTCSuccessed(5),            // Successfully joined RTC
    //RtmSetupFailed(6),                  // RTM setup failed [deprecated, please use onCallErrorOccur(state: rtmSetupFail)]
    RtmSetupSuccessed(7),           // RTM setup succeeded
    //MessageFailed(8),                   // Message sending failed [deprecated, please use onCallErrorOccur(state: sendMessageFail)]
    StateMismatch(9),               // State transition exception
    JoinRTCStart(10),               // Local has joined RTC channel but not yet successful (JoinChannelEx called)
    RemoteUserRecvCall(99),         // Caller call succeeded
    LocalRejected(100),             // Local user rejected
    RemoteRejected(101),            // Remote user rejected
    OnCalling(102),                 // Transitioned to calling [deprecated, please refer to localVideoCall/localAudioCall/remoteVideoCall/remoteAudioCall]
    RemoteAccepted(103),            // Remote user accepted
    LocalAccepted(104),             // Local user accepted
    LocalHangup(105),               // Local user hung up
    RemoteHangup(106),              // Remote user hung up
    RemoteJoined(107),              // Remote user joined RTC channel
    RemoteLeft(108),               // Remote user left RTC channel, RTC channel (eventReason please refer to AgoraUserOfflineReason)
    LocalCancelled(109),            // Local user canceled the call
    RemoteCancelled(110),           // Remote user canceled the call
    LocalJoined(111),               // Local user joined RTC channel
    LocalLeft(112),                // Local user left RTC channel
    RecvRemoteFirstFrame(113),      // Received the first frame from remote
    //CancelByCallerRecall(114),      // The same caller calling different channels results in cancellation [deprecated]
    RtmLost(115),                   // RTM timeout disconnection
    //RtcOccurError(116),             // RTC error occurred [deprecated, please use onCallErrorOccur(state: rtcOccurError)]
    RemoteCallBusy(117),            // Remote user is busy
    //StartCaptureFail(118),          // Failed to start capture [deprecated, please use onCallErrorOccur(state: startCaptureFail)]
    CaptureFirstLocalVideoFrame(119),       // Captured the first video frame
    PublishFirstLocalVideoFrame(120),       // Successfully published the first video frame
    PublishFirstLocalAudioFrame(130),        // Successfully published the first audio frame [supported from 2.1.0]
    LocalVideoCall(140),         // Local initiated video call
    LocalAudioCall(141),         // Local initiated audio call
    RemoteVideoCall(142),        // Remote initiated video call
    RemoteAudioCall(142),        // Remote initiated audio call
}

/*
 * Call error events
 */
enum class CallErrorEvent(val value: Int) {
    NormalError(0),         // General error
    RtcOccurError(100),     // RTC error occurred
    StartCaptureFail(110),  // Failed to start capture
    // RtmSetupFail(200),      // RTM initialization failed [deprecated, changed to messageManager manually initializing]
    SendMessageFail(210)    // Message error; if using CallRtmMessageManager, it is AgoraRtmErrorCode; for custom channels, it is the corresponding channel's error code
}

/*
 * Error code types for call error events
 */
enum class CallErrorCodeType(val value: Int) {
    Normal(0),   // Business type error, none at present
    Rtc(1),      // RTC error, use AgoraErrorCode
    Message(2)   // RTM error, use AgoraRtmErrorCode
}

/*
 * Log levels
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
     * @param stateReason Reason for state change
     * @param eventReason Event type description
     * @param eventInfo Extended information; parameters vary by event type, where the key "publisher" indicates the ID of the state changer; if empty, it indicates a change in one's own state
     */
    fun onCallStateChanged(state: CallStateType,
                           stateReason: CallStateReason,
                           eventReason: String,
                           eventInfo: Map<String, Any>)

    /**
     * Internal detailed event change callback
     * @param event: Event
     * @param eventReason: Event reason, default null; different meanings based on different events
     */
    fun onCallEventChanged(event: CallEvent, eventReason: String?) {}

    /**
     * Internal detailed event change callback
     * @param errorEvent: Error event
     * @param errorType: Error type
     * @param errorCode: Error code
     * @param message: Error message
     */
    fun onCallError(errorEvent: CallErrorEvent,
                    errorType: CallErrorCodeType,
                    errorCode: Int,
                    message: String?) {}

    /**
     * Callback when the call starts
     * @param roomId: The channel ID of the call
     * @param callerUserId: The user ID of the caller
     * @param currentUserId: Your own ID
     * @param timestamp: The timestamp when the call starts, the difference from 19700101, in ms
     */
    fun onCallConnected(roomId: String,
                        callUserId: Int,
                        currentUserId: Int,
                        timestamp: Long) {}

    /**
     * Callback when the call ends
     * @param roomId: The channel ID of the call
     * @param hangupUserId: The user ID of the one who hung up
     * @param currentUserId: Your own ID
     * @param timestamp: The timestamp when the call starts, the difference from 19700101, in ms
     * @param duration: The duration of the call, in ms
     */
    fun onCallDisconnected(roomId: String,
                           hangupUserId: Int,
                           currentUserId: Int,
                           timestamp: Long,
                           duration: Long) {}

    /**
     * When calling, determine if you can join RTC
     * @param eventInfo Extended information received during the call
     * @return true: Can join, false: Cannot join
     */
    fun canJoinRtcOnCalling(eventInfo: Map<String, Any>) : Boolean?

    /**
     * Token is about to expire (requires external retrieval of a new token and call renewToken to update)
     */
    fun tokenPrivilegeWillExpire() {}

    /** Log callback
     *  @param message: Log information
     *  @param logLevel: Log priority: 0: Normal log, 1: Warning log, 2: Error log
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
     * Prepare the call environment; must be successful to make a call. If you need to change the RTC channel number for the call, you can call it repeatedly, ensuring that it is called when not in a call state (not calling, connecting, or connected)
     * @param prepareConfig
     * @param completion
     */
    fun prepareForCall(prepareConfig: PrepareConfig, completion: ((AGError?) -> Unit)?)

    /**
     * Add a listener for callbacks
     * @param listener
     */
    fun addListener(listener: ICallApiListener)

    /**
     * Remove a listener for callbacks
     * @param listener
     */
    fun removeListener(listener: ICallApiListener)

    /**
     * Initiate a call invitation; the caller calls to establish an RTC connection with the remote user based on the RTC channel number set by prepareForCall, defaulting to a video call
     * @param remoteUserId The user ID to call
     * @param completion
     */
    fun call(remoteUserId: Int, completion: ((AGError?) -> Unit)?)

    /**
     * Initiate a call invitation; the caller calls to establish an RTC connection with the remote user based on the RTC channel number set by prepareForCall, defaulting to a video call
     * @param remoteUserId The user ID to call
     * @param callType Call type: 0: Video call, 1: Audio call
     * @param callExtension Fields that need to be extended for the call; can be retrieved through the kFromUserExtension field when receiving messages from the remote user that change state (e.g., calling/connecting)
     * @param completion
     */
    fun call(remoteUserId: Int, callType: CallType, callExtension: Map<String, Any>, completion: ((AGError?) -> Unit)?)

    /**
     * Cancel the ongoing call; the caller calls
     * @param completion
     */
    fun cancelCall(completion: ((AGError?) -> Unit)?)

    /** Accept a call; after calling, the caller will receive onAccept
     *
     * @param remoteUserId: The user ID of the caller
     * @param completion: <#completion description#>
     */
    fun accept(remoteUserId: Int, completion: ((AGError?) -> Unit)?)

    /**
     * Reject a call; called by the callee
     * @param remoteUserId The user ID of the caller
     * @param reason Reason for rejection
     * @param completion
     */
    fun reject(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?)

    /**
     * End a call; both the caller and callee can call this
     * @param remoteUserId The user ID of the one who hung up
     * @param reason Reason for hanging up
     * @param completion
     */
    fun hangup(remoteUserId: Int, reason: String?, completion: ((AGError?) -> Unit)?)

    /**
     * Get the current call's callId; the callId is a unique identifier for the current call process, through which the Agora backend service can query the key node duration and state transition time points of the current call
     * @return callId, empty for messages other than calls
     */
    fun getCallId(): String
}
