package io.agora.onetoone.message

import io.agora.onetoone.AGError

interface CallMessageListener {
    fun messageReceive(message: String)
    fun debugInfo(message: String, logLevel: Int)
}

interface ICallMessageManager {
    fun onSendMessage(userId: String, message: Map<String, Any>, completion: ((AGError?)-> Unit)?)
    fun addListener(listener: CallMessageListener)
    fun removeListener(listener: CallMessageListener)
    fun release()
}