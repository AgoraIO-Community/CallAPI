package io.agora.onetoone.message

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import io.agora.onetoone.AGError
import io.agora.onetoone.CallMessageListener
import io.agora.onetoone.ICallMessageManager
import io.agora.onetoone.extension.getCostMilliseconds
import io.agora.rtm.*

interface CallRtmMessageListener: CallMessageListener, RtmEventListener {
    // TODO 这里可以定义新的接口
}

fun createRtmMessageManager(client: RtmClient?, appId: String, userId: Int, rtmToken: String) = CallRtmMessageImpl(client, appId, userId, rtmToken)

class CallRtmMessageImpl(
    private val client: RtmClient?,
    private val appId: String = "",
    private val userId: Int = 0,
    private val rtmToken: String
): ICallMessageManager, RtmEventListener {

    companion object {
        private const val TAG = "CALL_RTM_MSG_MANAGER"
        // 发送的消息id
        private const val kMessageId = "messageId"
    }

    private val mHandler = Handler(Looper.getMainLooper())

    private var rtmClient: RtmClient

    // RTM是否已经登录
    private var isLoginedRtm = false

    // 是否外部传入的rtm，如果是则不需要手动logout
    private var isExternalRtmClient = false

    // 消息id
    private var messageId: Int = 0

    // 回调
    private val listeners = mutableListOf<CallMessageListener>()

    init {
        val rtm = client
        if (rtm != null) {
            //如果外部传入rtmClient，默认登陆成功
            isLoginedRtm = true
            isExternalRtmClient = true
            rtmClient = rtm
        } else {
            rtmClient = createRtmClient()
            initialize {}
        }
        rtmClient.addEventListener(this)
        rtmClient.setParameters("{\"rtm.msg.tx_timeout\": 3000}")
        callMessagePrint("init-- CallMessageManager ")
    }

    override fun onSendMessage(
        userId: String,
        message: Map<String, Any>,
        completion: ((AGError?) -> Unit)?
    ) {
        if (userId.isEmpty() || userId == "0") {
            val errorStr = "sendMessage fail, invalid userId[$userId]"
            callMessagePrint(errorStr)
            completion?.invoke(AGError(errorStr, -1))
            return
        }
        messageId += 1
        messageId %= Int.MAX_VALUE
        val map = message.toMutableMap()
        map[kMessageId] = messageId
        sendMessage(userId, map, completion)
    }

    override fun addListener(listener: CallMessageListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: CallMessageListener) {
        listeners.add(listener)
    }

    override fun release() {
        deInitialize()
    }

    /// 更新RTM token
    /// - Parameter rtmToken: <#rtmToken description#>
    fun renewToken(rtmToken: String) {
        if (!isLoginedRtm) {
            //没有登陆成功，但是需要自动登陆，可能是初始token问题，这里重新initialize
            callMessagePrint("renewToken need to reinit")
            rtmClient.logout(object : ResultCallback<Void> {
                override fun onSuccess(responseInfo: Void?) {}
                override fun onFailure(errorInfo: ErrorInfo?) {}
            })
            initialize { }
        }
        rtmClient.renewToken(rtmToken, object : ResultCallback<Void> {
            override fun onSuccess(responseInfo: Void?) {
                callMessagePrint("rtm renewToken")
            }
            override fun onFailure(errorInfo: ErrorInfo?) {
            }
        })
    }

    private fun sendMessage(userId: String, message: Map<String, Any>, completion:((AGError?)->Unit)?) {
        if (userId.isEmpty()) {
            completion?.invoke(AGError("send message fail! roomId is empty", -1))
            return
        }
        val msgId = message[kMessageId] as? Int ?: 0
        val json = Gson().toJson(message)
        val options = PublishOptions()
        options.setChannelType(RtmConstants.RtmChannelType.USER)
        val startTime = System.currentTimeMillis()
        callMessagePrint("_sendMessage[$msgId] to '$userId', message: $message")
        rtmClient.publish(userId, json.toByteArray(), options, object : ResultCallback<Void> {
            override fun onSuccess(p0: Void?) {
                callMessagePrint("_sendMessage[$msgId] publish cost ${startTime.getCostMilliseconds()} ms")
                runOnUiThread { completion?.invoke(null) }
            }
            override fun onFailure(errorInfo: ErrorInfo) {
                val msg = errorInfo.errorReason
                val code = RtmConstants.RtmErrorCode.getValue(errorInfo.errorCode)
                callMessagePrint("_sendMessage[$msgId]: fail: $msg cost: ${startTime.getCostMilliseconds()} ms", 1)
                runOnUiThread { completion?.invoke(AGError(msg, code)) }
            }
        })
    }

    private fun createRtmClient(): RtmClient {
        val rtmConfig = RtmConfig.Builder(appId, userId.toString()).build()
        if (rtmConfig.userId.isEmpty()) {
            callMessagePrint("userId is empty", 2)
        }
        if (rtmConfig.appId.isEmpty()) {
            callMessagePrint("appId is empty", 2)
        }
        var rtmClient: RtmClient? = null
        try {
            rtmClient = RtmClient.create(rtmConfig)
        } catch (e: Exception) {
            callMessagePrint("create rtm client fail: ${e.message}", 2)
        }
        return rtmClient!!
    }

    private fun loginRTM(rtmClient: RtmClient, token: String, completion: (ErrorInfo?) -> Unit) {
        if (isLoginedRtm) {
            completion(null)
            return
        }
        rtmClient.logout(object : ResultCallback<Void?> {
            override fun onSuccess(responseInfo: Void?) {}
            override fun onFailure(errorInfo: ErrorInfo?) {}
        })
        callMessagePrint("will login")
        rtmClient.login(token, object : ResultCallback<Void?> {
            override fun onSuccess(p0: Void?) {
                callMessagePrint("login completion")
                isLoginedRtm = true
                runOnUiThread { completion(null) }
            }

            override fun onFailure(p0: ErrorInfo?) {
                callMessagePrint("login completion: ${p0?.errorCode}")
                isLoginedRtm = false
                runOnUiThread { completion(p0) }
            }
        })
    }

    private fun deInitialize() {
        rtmClient.removeEventListener(this)
        if (!isExternalRtmClient) {
            rtmClient.logout(object : ResultCallback<Void> {
                override fun onSuccess(responseInfo: Void?) {}
                override fun onFailure(errorInfo: ErrorInfo?) {}
            })
            RtmClient.release()
        }
    }

    /** 根据配置初始化RTM
     * @param prepareConfig: <#prepareConfig description#>
     * @param completion: <#completion description#>
     */
    private fun initialize(completion: (AGError?) -> Unit) {
        callMessagePrint("initialize")
        rtmClient.addEventListener(this)
        if (rtmToken.isEmpty()) {
            val reason = "RTM Token is Empty"
            completion(AGError(reason, -1))
            return
        }
        val rtmClient = this.rtmClient
        if (!isLoginedRtm) {
            loginRTM(rtmClient, rtmToken) { err ->
                if (err != null) {
                    val errorCode = RtmConstants.RtmErrorCode.getValue(err.errorCode)
                    completion.invoke(AGError(err.errorReason, errorCode))
                    return@loginRTM
                }
                completion.invoke(null)
            }
        } else {
            completion.invoke(null)
        }
    }

    // --------------- MARK: AgoraRtmClientDelegate -------------
    override fun onConnectionStateChanged(
        channelName: String?,
        state: RtmConstants.RtmConnectionState?,
        reason: RtmConstants.RtmConnectionChangeReason?
    ) {
        callMessagePrint("rtm connectionStateChanged: $state reason: $reason")
//        runOnUiThread {
//            if (reason == RtmConstants.RtmConnectionChangeReason.TOKEN_EXPIRED) {
//                listeners.forEach {
//                    (it as CallRtmMessageListener).onTokenPrivilegeWillExpire(channelName)
//                }
//            } else if (reason == RtmConstants.RtmConnectionChangeReason.LOST) {
//                listeners.forEach {
//                    (it as CallRtmMessageListener).onConnectionFail()
//                }
//            }
//        }
    }

    override fun onTokenPrivilegeWillExpire(channelName: String?) {
        callMessagePrint("rtm onTokenPrivilegeWillExpire[${channelName ?: "nil"}]")
//        runOnUiThread {
//            listeners.forEach {
//                (it as CallRtmMessageListener).onTokenPrivilegeWillExpire(channelName)
//            }
//        }
    }

    override fun onMessageEvent(event: MessageEvent?) {
        runOnUiThread {
            val message = event?.message?.data as? ByteArray ?: return@runOnUiThread
            val jsonString = String(message, Charsets.UTF_8)
            listeners.forEach {
                it.messageReceive(jsonString)
            }
        }
    }

    override fun onPresenceEvent(event: PresenceEvent?) {}
    override fun onTopicEvent(event: TopicEvent?) {}
    override fun onLockEvent(event: LockEvent?) {}
    override fun onStorageEvent(event: StorageEvent?) {}

    private fun callMessagePrint(message: String, logLevel: Int = 0) {
        val tag = "[MessageManager]"
        listeners.forEach {
            it.debugInfo("$tag$message)", logLevel)
        }
    }

    private fun runOnUiThread(runnable: Runnable) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            runnable.run()
        } else {
            mHandler.post(runnable)
        }
    }
}