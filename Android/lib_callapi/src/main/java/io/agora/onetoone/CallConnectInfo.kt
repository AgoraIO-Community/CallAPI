package io.agora.onetoone

import android.os.Handler
import android.os.Looper

enum class CallConnectCostType(val value: String) {
    // Caller's call successful, receiving call success indicates delivery to peer (callee)
    // 主叫呼叫成功，收到呼叫成功表示已经送达对端(被叫)
    RemoteUserRecvCall("remoteUserRecvCall"),

    // Caller receives callee's call acceptance (onAccept)/callee clicks accept (accept)
    // 主叫收到被叫接受呼叫(onAccept)/被叫点击接受(accept)
    AcceptCall("acceptCall"),

    // Local user joins channel
    // 本地用户加入频道
    LocalUserJoinChannel("localUserJoinChannel"),

    // Local video first frame captured (video calls only)
    // 本地视频首帧被采集到(仅限视频呼叫)
    LocalFirstFrameDidCapture("localFirstFrameDidCapture"),

    // Local user successfully pushes first frame (audio or video)
    // 本地用户推送首帧（音频或者视频）成功
    LocalFirstFrameDidPublish("localFirstFrameDidPublish"),

    // Remote user joins channel
    // 远端用户加入频道
    RemoteUserJoinChannel("remoteUserJoinChannel"),

    // Received first frame from peer
    // 收到对端首帧
    RecvFirstFrame("recvFirstFrame")
}

class CallConnectInfo {
    // Time when video stream retrieval started
    // 开始获取视频流的时间
    var startRetrieveFirstFrame: Long? = null
        private set

    // Whether remote video first frame has been retrieved
    // 是否获取到对端视频首帧
    var isRetrieveFirstFrame: Boolean = false

    // Call type
    // 呼叫类型
    var callType: CallType = CallType.Video

    // Call session ID
    // 呼叫的session id
    var callId: String = ""

    // Channel name during call
    // 呼叫中的频道名
    var callingRoomId: String? = null

    // Remote user during call
    // 呼叫中的远端用户
    var callingUserId: Int? = null

    // Call start timestamp
    // 通话开始的时间
    var callConnectedTs: Long = 0

    // Whether local user has accepted
    // 本地是否已经同意
    var isLocalAccepted: Boolean = false

    // Call initiation time
    // 呼叫开始的时间
    private var _callTs: Long? = null
    var callTs: Long?
        get() = _callTs
        set(value) {
            _callTs = value
            callCostMap.clear()
        }

    // Timer for call initiation, used for timeout handling
    // 发起呼叫的定时器，用来处理超时
    private val mHandler = Handler(Looper.getMainLooper())

    val callCostMap = mutableMapOf<String, Long>()

    // Timer runnable for call initiation, used for timeout handling
    // 发起呼叫的定时器Runnable，用来处理超时
    private var timerRunnable: Runnable? = null
        set(value) {
            val oldVlaue = field
            field = value
            oldVlaue?.let { mHandler.removeCallbacks(it) }
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