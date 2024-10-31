package io.agora.onetoone.signalClient

import android.util.Log
import io.agora.onetoone.AGError
import io.agora.rtm.*

interface ICallRtmManagerListener {
    /**
     * RTM connection successful
     */
    fun onConnected()

    /**
     * RTM connection disconnected
     */
    fun onDisconnected()

    /**
     * Token is about to expire, need to renew token
     */
    fun onTokenPrivilegeWillExpire(channelName: String)
}

fun createRtmManager(appId: String, userId: Int, client: RtmClient? = null) = CallRtmManager(appId, userId, client)

class CallRtmManager(
    private val appId: String = "",
    private val userId: Int = 0,
    private val client: RtmClient? = null
): RtmEventListener {

    private var rtmClient: RtmClient

    var isConnected: Boolean = false

    // Whether RTM has logged in
    var isLoginedRtm = false

    // Whether the RTM is externally provided; if so, no need to manually logout
    private var isExternalRtmClient = false

    private val listeners = mutableListOf<ICallRtmManagerListener>()

    init {
        val rtm = client
        if (rtm != null) {
            // If an external rtmClient is provided, assume login is successful by default
            isLoginedRtm = true
            isExternalRtmClient = true
            rtmClient = rtm
            isConnected = true
        } else {
            rtmClient = createRtmClient()
        }
        rtmClient.addEventListener(this)
        rtmClient.setParameters("{\"rtm.msg.tx_timeout\": 3000}")
        callMessagePrint("init-- CallRtmManager")
    }

    /**
     * Get the internal RTM instance
     */
    fun getRtmClient() : RtmClient = rtmClient

    /**
     * Login to RTM
     */
    fun login(rtmToken: String, completion: (AGError?) -> Unit) {
        callMessagePrint("login")
        if (rtmToken.isEmpty() && !isExternalRtmClient) {
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

    /**
     * Logout of RTM
     */
    fun logout() {
        if (!isExternalRtmClient) {
            rtmClient.logout(object : ResultCallback<Void> {
                override fun onSuccess(responseInfo: Void?) {}
                override fun onFailure(errorInfo: ErrorInfo?) {}
            })
            RtmClient.release()
            isConnected = false
        }
    }

    /**
     * Set the listener
     * @param listener ICallRtmManagerListener object
     */
    fun addListener(listener: ICallRtmManagerListener) {
        if (listeners.contains(listener)) return
        listeners.add(listener)
    }

    /**
     * Remove the listener
     * @param listener ICallRtmManagerListener object
     */
    fun removeListener(listener: ICallRtmManagerListener) {
        listeners.add(listener)
    }

    /**
     * Update RTM token
     * @param rtmToken New rtmToken
     */
    fun renewToken(rtmToken: String) {
        if (!isLoginedRtm) {
            // Not logged in successfully, but automatic login is needed; there may be an initial token issue, reinitialize here
            callMessagePrint("renewToken need to reinit")
            rtmClient.logout(object : ResultCallback<Void> {
                override fun onSuccess(responseInfo: Void?) {}
                override fun onFailure(errorInfo: ErrorInfo?) {}
            })
            login(rtmToken) { }
            return
        }
        rtmClient.renewToken(rtmToken, object : ResultCallback<Void> {
            override fun onSuccess(responseInfo: Void?) {
                callMessagePrint("rtm renewToken")
            }
            override fun onFailure(errorInfo: ErrorInfo?) {
            }
        })
    }

    // ------------------ RtmEventListener ------------------
    override fun onConnectionStateChanged(
        channelName: String?,
        state: RtmConstants.RtmConnectionState?,
        reason: RtmConstants.RtmConnectionChangeReason?
    ) {
        super.onConnectionStateChanged(channelName, state, reason)
        callMessagePrint("rtm connectionStateChanged, channelName: $channelName, state: $state reason: $reason")
        channelName ?: return
        if (reason == RtmConstants.RtmConnectionChangeReason.TOKEN_EXPIRED) {
            listeners.forEach { it.onTokenPrivilegeWillExpire(channelName) }
        } else if (reason == RtmConstants.RtmConnectionChangeReason.LOST) {
            isConnected = false
        } else if (state == RtmConstants.RtmConnectionState.CONNECTED) {
            if (isConnected) return
            isConnected = true
            listeners.forEach { it.onConnected() }
        } else {
            if (!isConnected) return
            isConnected = false
            listeners.forEach { it.onDisconnected() }
        }
    }

    // ------------------ inner private ------------------

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
                completion(null)
            }

            override fun onFailure(p0: ErrorInfo?) {
                callMessagePrint("login completion: ${p0?.errorCode}")
                isLoginedRtm = false
                completion(p0)
            }
        })
    }

    private fun callMessagePrint(message: String, logLevel: Int = 0) {
        val tag = "[MessageManager]"
        Log.d(tag, message)
    }
}