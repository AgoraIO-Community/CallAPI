package io.agora.onetoone.signalClient

import io.agora.onetoone.AGError

/*
 * Signaling callback protocol
 */
interface ISignalClientListener {
    /**
     * Callback for receiving messages
     * @param message The content of the message
     */
    fun onMessageReceive(message: String)

    /**
     * Signaling log callback
     * @param message The content of the log message
     * @param logLevel The priority of the log
     */
    fun debugInfo(message: String, logLevel: Int)
}

/*
 * Abstract signaling protocol; can use a custom implementation for the information channel
 */
interface ISignalClient {
    /**
     * Send a message to the signaling system from CallApi
     * @param userId The target user's ID
     * @param message The message object
     * @param completion Completion callback
     */
    fun sendMessage(userId: String, message: String, completion: ((AGError?) -> Unit)?)

    /**
     * Register a callback for the signaling system
     * @param listener ISignalClientListener object
     */
    fun addListener(listener: ISignalClientListener)

    /**
     * Remove a callback for the signaling system
     * @param listener ISignalClientListener object
     */
    fun removeListener(listener: ISignalClientListener)
}