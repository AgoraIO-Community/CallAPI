package io.agora.onetoone.message

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.hyphenate.EMCallBack
import com.hyphenate.EMMessageListener
import com.hyphenate.chat.EMClient
import com.hyphenate.chat.EMMessage
import com.hyphenate.chat.EMOptions
import com.hyphenate.chat.EMTextMessageBody
import com.hyphenate.exceptions.HyphenateException
import io.agora.onetoone.AGError
import io.agora.onetoone.ICallMessageListener
import io.agora.onetoone.ICallMessageManager
import io.agora.rtm.PublishOptions
import io.agora.rtm.RtmConstants

fun createHyphenateMessageManager(context: Context, appKey: String, userId: Int) = CallHyphenateMessageImpl(context, appKey, userId)

class CallHyphenateMessageImpl(
    private val context: Context,
    private val appKey: String,
    private val userId: Int = 0
): ICallMessageManager, EMMessageListener {

    companion object {
        private const val TAG = "CALL_HY_MSG_MANAGER"
        // 发送的消息id
        private const val kMessageId = "messageId"
    }

    init {
        val options = EMOptions()
        options.appKey = appKey
        // 其他 EMOptions 配置。
        EMClient.getInstance().init(context, options)
        // 注册消息监听
        EMClient.getInstance().chatManager().addMessageListener(this)

        // 注册
        try {
            // 同步方法，会阻塞当前线程。
            EMClient.getInstance().createAccount(userId.toString(), userId.toString())

        } catch (e: HyphenateException) {
            //失败
            Log.e(TAG, "createAccount failed: ${e.message}")
        }

        //成功
        EMClient.getInstance().login(userId.toString(), userId.toString(), object : EMCallBack {
            // 登录成功回调
            override fun onSuccess() {
                Log.d(TAG, "login success")
                EMClient.getInstance().chatManager().loadAllConversations()
                EMClient.getInstance().groupManager().loadAllGroups()
            }

            // 登录失败回调，包含错误信息
            override fun onError(code: Int, error: String) {
                Log.e(TAG, "login failed, code:$code, msg:$error")
            }

            override fun onProgress(i: Int, s: String) {}
        })
    }

    private val mHandler = Handler(Looper.getMainLooper())

    // 消息id
    private var messageId: Int = 0

    // 回调
    private val listeners = mutableListOf<ICallMessageListener>()

    override fun onSendMessage(
        userId: String,
        message: Map<String, Any>,
        completion: ((AGError?) -> Unit)?
    ) {
        if (userId.isEmpty() || userId == "0") {
            val errorStr = "sendMessage fail, invalid userId[$userId]"
            completion?.invoke(AGError(errorStr, -1))
            return
        }
        messageId += 1
        messageId %= Int.MAX_VALUE
        val map = message.toMutableMap()
        map[kMessageId] = messageId
        sendMessage(userId, map, completion)
    }

    private fun sendMessage(
        userId: String,
        message: Map<String, Any>,
        completion: ((AGError?) -> Unit)?
    ) {
        if (userId.isEmpty()) {
            completion?.invoke(AGError("send message fail! roomId is empty", -1))
            return
        }
        val msgId = message[kMessageId] as? Int ?: 0
        val json = Gson().toJson(message)
        val options = PublishOptions()
        options.setChannelType(RtmConstants.RtmChannelType.USER)
        val startTime = System.currentTimeMillis()

        // `content` 为要发送的文本内容，`toChatUsername` 为对方的账号。
        val msg = EMMessage.createTextSendMessage(json, userId)
        // 会话类型：单聊为 EMMessage.ChatType.Chat
        msg.chatType = EMMessage.ChatType.Chat
        // 发送消息时可以设置 `EMCallBack` 的实例，获得消息发送的状态。可以在该回调中更新消息的显示状态。例如消息发送失败后的提示等等。
        msg.setMessageStatusCallback(object : EMCallBack {
            override fun onSuccess() {
                // 发送消息成功
                completion?.invoke(null)
            }

            override fun onError(code: Int, error: String) {
                // 发送消息失败
                completion?.invoke(AGError(error, code))
            }

            override fun onProgress(progress: Int, status: String) {}
        })
        // 发送消息
        EMClient.getInstance().chatManager().sendMessage(msg)
    }

    override fun addListener(listener: ICallMessageListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ICallMessageListener) {
        listeners.remove(listener)
    }

    override fun release() {
        listeners.clear()
        EMClient.getInstance().logout(false)
    }

    // EMMessageListener
    override fun onMessageReceived(messages: MutableList<EMMessage>?) {
        messages?.forEach {
            runOnUiThread {
                val body = it.body as EMTextMessageBody
                listeners.forEach {
                    it.messageReceive(body.message)
                }
            }
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