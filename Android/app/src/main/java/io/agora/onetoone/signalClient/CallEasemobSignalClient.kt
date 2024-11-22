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
import io.agora.onetoone.R
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
        // Other EMOptions configurations
        // 其他 EMOptions 配置
        EMClient.getInstance().init(context, options)
        // Register message listener
        // 注册消息监听
        EMClient.getInstance().chatManager().addMessageListener(this)
        EMClient.getInstance().addConnectionListener(this)
    }

    var isConnected: Boolean = false

    private val mHandler = Handler(Looper.getMainLooper())

    fun login(completion: ((success: Boolean) -> Unit)?) {
        Thread {
            // Registration
            // 注册
            try {
                // Synchronous method, will block current thread
                // 同步方法，会阻塞当前线程
                EMClient.getInstance().createAccount(userId.toString(), userId.toString())

            } catch (e: HyphenateException) {
                // Failed
                // 失败
                Log.e(TAG, "createAccount failed: ${e.message}, code: ${e.errorCode}")
            }

            // Login
            // 登陆
            EMClient.getInstance().login(userId.toString(), userId.toString(), object : EMCallBack {
                // Login success callback
                // 登录成功回调
                override fun onSuccess() {
                    Log.d(TAG, "login success")
                    isConnected = true
                    EMClient.getInstance().chatManager().loadAllConversations()
                    EMClient.getInstance().groupManager().loadAllGroups()
                    completion?.invoke(true)
                }

                // Login failure callback with error information
                // 登录失败回调，包含错误信息
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

        // `content` is the text content to send, `toChatUsername` is the recipient's account
        // `content` 为要发送的文本内容，`toChatUsername` 为对方的账号
        val msg = EMMessage.createTextSendMessage(message, userId)
        // Chat type: single chat is EMMessage.ChatType.Chat
        // 会话类型：单聊为 EMMessage.ChatType.Chat
        msg.chatType = EMMessage.ChatType.Chat
        msg.deliverOnlineOnly(true)
        // When sending a message, you can set an instance of `EMCallBack` to get the message sending status
        // Can update message display status in this callback, such as message sending failure prompts, etc.
        // 发送消息时可以设置 `EMCallBack` 的实例，获得消息发送的状态
        // 可以在该回调中更新消息的显示状态。例如消息发送失败后的提示等等
        msg.setMessageStatusCallback(object : EMCallBack {
            override fun onSuccess() {
                // Message sent successfully
                // 发送消息成功
                runOnUiThread { completion?.invoke(null) }
            }

            override fun onError(code: Int, error: String) {
                // Message sending failed
                // 发送消息失败
                runOnUiThread { completion?.invoke(AGError(error, code)) }
            }

            override fun onProgress(progress: Int, status: String) {}
        })
        // Send message
        // 发送消息
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
            Toasty.normal(context, context.getString(R.string.toast_easemob_connected), Toast.LENGTH_SHORT).show()
        }
        isConnected = true
    }

    override fun onDisconnected(errorCode: Int) {
        runOnUiThread {
            Toasty.normal(context, context.getString(R.string.toast_easemob_disconnected), Toast.LENGTH_SHORT).show()
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