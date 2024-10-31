package io.agora.onetoone.signalClient

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.hyphenate.EMCallBack
import com.hyphenate.EMConnectionListener
import com.hyphenate.EMMessageListener
import com.hyphenate.chat.EMClient
import com.hyphenate.chat.EMMessage
import com.hyphenate.chat.EMOptions
import com.hyphenate.chat.EMTextMessageBody
import com.hyphenate.exceptions.HyphenateException
import es.dmoral.toasty.Toasty
import io.agora.onetoone.AGError
import io.agora.rtm.PublishOptions
import io.agora.rtm.RtmConstants
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

fun createEasemobSignalClient(context: Context, appKey: String, userId: Int) = CallEasemobSignalClient(context, appKey, userId)

class CallEasemobSignalClient(
    private val context: Context,
    private val appKey: String,
    private val userId: Int = 0
): ISignalClient, EMMessageListener, EMConnectionListener, CallBaseSignalClient() {

    companion object {
        private const val TAG = "CALL_HY_MSG_MANAGER"
    }

    init {
        val options = EMOptions()
        options.appKey = appKey
        // Other EMOptions configuration.
        EMClient.getInstance().init(context, options)
        // Register message listener
        EMClient.getInstance().chatManager().addMessageListener(this)
        EMClient.getInstance().addConnectionListener(this)
    }

    var isConnected: Boolean = false

    private val mHandler = Handler(Looper.getMainLooper())

    fun login(completion: ((success: Boolean) -> Unit)?) {
        Thread {
            // Registration
            try {
                // Synchronous method, will block the current thread.
                EMClient.getInstance().createAccount(userId.toString(), userId.toString())

            } catch (e: HyphenateException) {
                // Failure
                Log.e(TAG, "createAccount failed: ${e.message}, code: ${e.errorCode}")
            }

            // Login
            EMClient.getInstance().login(userId.toString(), userId.toString(), object : EMCallBack {
                // Login success callback
                override fun onSuccess() {
                    Log.d(TAG, "login success")
                    isConnected = true
                    EMClient.getInstance().chatManager().loadAllConversations()
                    EMClient.getInstance().groupManager().loadAllGroups()
                    completion?.invoke(true)
                }

                // Login failure callback, includes error information
                override fun onError(code: Int, error: String) {
                    Log.e(TAG, "login failed, code:$code, msg:$error")
                    completion?.invoke(false)
                }

                override fun onProgress(i: Int, s: String) {}
            })
        }.start()
    }

    fun clean() {
        isConnected = false
        listeners.clear()
        EMClient.getInstance().removeConnectionListener(this)
        EMClient.getInstance().logout(false)
    }

    override fun sendMessage(
        userId: String,
        message: String,
        completion: ((AGError?) -> Unit)?
    ) {
        if (userId.isEmpty() || userId == "0") {
            val errorStr = "sendMessage fail, invalid userId[$userId]"
            completion?.invoke(AGError(errorStr, -1))
            return
        }
        innerSendMessage(userId, message, completion)
    }

    private fun innerSendMessage(
        userId: String,
        message: String,
        completion: ((AGError?) -> Unit)?
    ) {
        if (userId.isEmpty()) {
            completion?.invoke(AGError("send message fail! roomId is empty", -1))
            return
        }
        val options = PublishOptions()
        options.setChannelType(RtmConstants.RtmChannelType.USER)
        val startTime = System.currentTimeMillis()

        // `content` is the text content to be sent, `toChatUsername` is the recipient's account.
        val msg = EMMessage.createTextSendMessage(message, userId)
        // Conversation type: single chat is EMMessage.ChatType.Chat
        msg.chatType = EMMessage.ChatType.Chat
        msg.deliverOnlineOnly(true)
        // When sending a message, you can set an instance of `EMCallBack` to obtain the message sending status. You can update the message display status in this callback, for example, prompts after message sending failures, etc.
        msg.setMessageStatusCallback(object : EMCallBack {
            override fun onSuccess() {
                // Message sent successfully
                runOnUiThread { completion?.invoke(null) }
            }

            override fun onError(code: Int, error: String) {
                // Message sending failed
                runOnUiThread { completion?.invoke(AGError(error, code)) }
            }

            override fun onProgress(progress: Int, status: String) {}
        })
        // Send message
        EMClient.getInstance().chatManager().sendMessage(msg)
    }

    // ---------------- EMMessageListener ----------------
    override fun onMessageReceived(messages: MutableList<EMMessage>?) {
        runOnUiThread {
            messages?.forEach {
                val body = it.body as EMTextMessageBody
                listeners.forEach {
                    it.onMessageReceive(body.message)
                }
            }
        }
    }

    // ---------------- EMConnectionListener ----------------
    override fun onConnected() {
        runOnUiThread {
            Toasty.normal(context, "Connected to Easemob", Toast.LENGTH_SHORT).show()
        }
        isConnected = true
    }

    override fun onDisconnected(errorCode: Int) {
        runOnUiThread {
            Toasty.normal(context, "Disconnected from Easemob", Toast.LENGTH_SHORT).show()
        }
        isConnected = false
    }

    // ---------------- inner private ----------------
    private fun runOnUiThread(runnable: Runnable) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            runnable.run()
        } else {
            mHandler.post(runnable)
        }
    }
}