package io.agora.onetoone

import android.os.Handler
import android.os.Looper

enum class CallConnectCostType(val value: String) {
    RemoteUserRecvCall("remoteUserRecvCall"),       // Caller successfully calls, received call success indicates it has been delivered to the callee
    AcceptCall("acceptCall"),                       // Caller receives the callee's acceptance of the call (onAccept) / callee clicks accept (accept)
    LocalUserJoinChannel("localUserJoinChannel"),   // Local user joins the channel
    LocalFirstFrameDidCapture("localFirstFrameDidCapture"), // The first frame of local video has been captured (only for video calls)
    LocalFirstFrameDidPublish("localFirstFrameDidPublish"), // Local user successfully pushes the first frame (audio or video)
    RemoteUserJoinChannel("remoteUserJoinChannel"), // Remote user joins the channel
    RecvFirstFrame("recvFirstFrame")                // Received the first frame from the remote side
}

class CallConnectInfo {
    // Time to start retrieving the video stream
    var startRetrieveFirstFrame: Long? = null
        private set

    // Whether the first frame of the remote video has been retrieved
    var isRetrieveFirstFrame: Boolean = false

    // Call type
    var callType: CallType = CallType.Video

    // Call session ID
    var callId: String = ""

    // Channel name during the call
    var callingRoomId: String? = null

    // Remote user during the call
    var callingUserId: Int? = null

    // Call start time
    var callConnectedTs: Long = 0

    // Whether the local user has accepted
    var isLocalAccepted: Boolean = false

    // Call start time
    private var _callTs: Long? = null
    var callTs: Long?
        get() = _callTs
        set(value) {
            _callTs = value
            callCostMap.clear()
        }

    val callCostMap = mutableMapOf<String, Long>()

    // Timer for initiating the call, used to handle timeout
    private val mHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
        set(value) {
            val oldValue = field
            field = value
            oldValue?.let { mHandler.removeCallbacks(it) }
        }

    fun scheduledTimer(runnable: Runnable?, time: Long = 0) {
        val oldRunnable = timerRunnable
        if (oldRunnable != null) {
            mHandler.removeCallbacks(oldRunnable)
            timerRunnable = null
        }
        if (runnable != null) {
            timerRunnable = runnable
            mHandler.postDelayed(runnable, time)
        }
    }

    fun clean() {
        scheduledTimer(null)
        callingRoomId = null
        callingUserId = null
        callTs = null
        callId = ""
        isRetrieveFirstFrame = false
        startRetrieveFirstFrame = null
        isLocalAccepted = false
        callConnectedTs = 0
    }

    fun set(callType: CallType? = null, userId: Int, roomId: String, callId: String? = null, isLocalAccepted: Boolean = false) {
        if (callType != null) {
            this.callType = callType
        }
        this.callingUserId = userId
        this.callingRoomId = roomId
        this.isLocalAccepted = isLocalAccepted
        if (callId != null) {
            this.callId = callId
        }
        if (callTs == null) {
            callTs = System.currentTimeMillis()
        }
        if (startRetrieveFirstFrame == null) {
            startRetrieveFirstFrame = System.currentTimeMillis()
        }
    }
}