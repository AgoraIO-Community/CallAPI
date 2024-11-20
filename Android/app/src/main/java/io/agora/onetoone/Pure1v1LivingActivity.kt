package io.agora.onetoone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import es.dmoral.toasty.Toasty
import io.agora.onetoone.databinding.ActivityPure1v1LivingBinding
import io.agora.onetoone.http.HttpManager
import io.agora.onetoone.model.EnterRoomInfoModel
import io.agora.onetoone.signalClient.*
import io.agora.onetoone.utils.Ov1Logger
import io.agora.onetoone.utils.PermissionHelp
import io.agora.onetoone.utils.SPUtil
import io.agora.rtc2.*
import io.agora.rtc2.video.CameraCapturerConfiguration
import io.agora.rtc2.video.VideoEncoderConfiguration

class Pure1v1LivingActivity : AppCompatActivity(),  ICallApiListener {

    companion object {

        const val KEY_ENTER_ROOM_MODEL = "KEY_ENTER_ROOM_MODEL"

        fun launch(context: Context, model: EnterRoomInfoModel) {
            val intent = Intent(context, Pure1v1LivingActivity::class.java)
            val bundle = Bundle()
            bundle.putSerializable(KEY_ENTER_ROOM_MODEL, model)
            intent.putExtras(bundle)
            context.startActivity(intent)
        }
    }

    private val kTargetUserId = "targetUserId"

    private val enterModel by lazy {
        val bundle = intent.extras
        bundle!!.getSerializable(KEY_ENTER_ROOM_MODEL) as EnterRoomInfoModel
    }

    var videoEncoderConfig: VideoEncoderConfiguration? = null
    private var connectedUserId: Int? = null
    private var connectedChannel: String? = null

    private val TAG = "Pure1v1LivingActivity_LOG"

    private val mViewBinding by lazy { ActivityPure1v1LivingBinding.inflate(LayoutInflater.from(this)) }

    private lateinit var rtcEngine: RtcEngineEx
    private var rtmManager: CallRtmManager? = null
    private var emClient: CallEasemobSignalClient? = null
    private lateinit var prepareConfig: PrepareConfig
    private lateinit var api: CallApiImpl

    private var mCallState = CallStateType.Idle

    private var callDialog: AlertDialog? = null
    private var callTypeDialog: AlertDialog ?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mViewBinding.root)

        api = CallApiImpl(this)
        var isEncoderConfig = false
        val encoderConfig = VideoEncoderConfiguration()
        enterModel.dimensionsWidth.toIntOrNull()?.let {
            isEncoderConfig = true
            encoderConfig.dimensions.width = it
        }
        enterModel.dimensionsHeight.toIntOrNull()?.let {
            isEncoderConfig = true
            encoderConfig.dimensions.height = it
        }
        enterModel.frameRate.toIntOrNull()?.let {
            isEncoderConfig = true
            encoderConfig.frameRate = it
        }
        enterModel.bitrate.toIntOrNull()?.let {
            isEncoderConfig = true
            encoderConfig.bitrate = it
        }
        if (isEncoderConfig) {
            this.videoEncoderConfig = encoderConfig
        }
        prepareConfig = PrepareConfig()
        prepareConfig.rtcToken = enterModel.rtcToken
        prepareConfig.firstFrameWaittingDisabled = enterModel.firstFrameWaittingDisabled

        rtcEngine = _createRtcEngine()
        setupView()
        updateCallState(CallStateType.Idle, null)

        // Initialize call api
        // 初始化 call api
        initMessageManager { }

        PermissionHelp(this).checkCameraAndMicPerms(
            {
            },
            {
                Toasty.normal(this@Pure1v1LivingActivity, getString(R.string.toast_no_permission), Toast.LENGTH_SHORT).show()
            },
            false
        )
    }

    private fun initMessageManager(completion: ((Boolean) -> Unit)) {
        if (enterModel.isRtm) {
            // Use RtmManager to manage RTM
            // 使用RtmManager管理RTM
            rtmManager = createRtmManager(BuildConfig.AG_APP_ID, enterModel.currentUid.toInt())
            // RTM login
            // rtm login
            rtmManager?.login(enterModel.rtmToken) {
                if (it == null) {
                    // Initialize call api after successful login
                    // login 成功后初始化 call api
                    initCallApi(completion)
                } else {
                    completion.invoke(false)
                }
            }
            // Listen to rtm manager events
            // 监听 rtm manager 事件
            rtmManager?.addListener(object : ICallRtmManagerListener {
                override fun onConnected() {
                    mViewBinding.root.post {
                        Toasty.normal(this@Pure1v1LivingActivity, getString(R.string.toast_rtm_connected), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onDisconnected() {
                    mViewBinding.root.post {
                        Toasty.normal(this@Pure1v1LivingActivity, getString(R.string.toast_rtm_disconnected), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onTokenPrivilegeWillExpire(channelName: String) {
                    // Renew token
                    // 重新获取token
                    tokenPrivilegeWillExpire()
                }
            })
        } else {
            emClient = createEasemobSignalClient(this, BuildConfig.IM_APP_KEY, enterModel.currentUid.toInt())
            emClient?.login {
                if (it) {
                    // Initialize call api after successful login
                    // login 成功后初始化 call api
                    initCallApi(completion)
                } else {
                    completion.invoke(false)
                }
            }
        }
    }

    private fun initCallApi(completion: ((Boolean) -> Unit)) {
        val config = CallConfig(
            appId = BuildConfig.AG_APP_ID,
            userId = enterModel.currentUid.toInt(),
            rtcEngine = rtcEngine,
            signalClient = if (enterModel.isRtm) createRtmSignalClient(rtmManager!!.getRtmClient()) else emClient!!
        )
        api.initialize(config)

        prepareConfig.roomId = enterModel.currentUid
        prepareConfig.localView = mViewBinding.vRight
        prepareConfig.remoteView = mViewBinding.vLeft

        api.addListener(this)
        api.prepareForCall(prepareConfig){ error ->
            completion.invoke(error == null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        closeAction()
    }

    private fun updateCallState(state: CallStateType, stateReason: CallStateReason?) {
        mCallState = state
        when(mCallState) {
            CallStateType.Calling ->{
                if (stateReason == CallStateReason.LocalVideoCall || stateReason == CallStateReason.RemoteVideoCall) {
                    mViewBinding.vRight.isVisible = true
                    mViewBinding.vLeft.isVisible = true
                    mViewBinding.btnVideo.isVisible = true
                } else if (stateReason == CallStateReason.LocalAudioCall || stateReason == CallStateReason.RemoteAudioCall) {
                    mViewBinding.vRight.isVisible = false
                    mViewBinding.vLeft.isVisible = false
                    mViewBinding.btnVideo.isVisible = false
                }
                mViewBinding.vRight.alpha = 1f

                mViewBinding.btnCall.isVisible = false
                mViewBinding.btnHangUp.isVisible = false
                mViewBinding.btnVideo.setText(R.string.pure_1v1_video_off)
                mViewBinding.btnAudio.setText(R.string.pure_1v1_audio_off)
            }
            CallStateType.Connected -> {
                mViewBinding.vLeft.alpha = 1f
                mViewBinding.btnHangUp.isVisible = true
                mViewBinding.btnAudio.isVisible = true
                mViewBinding.btnVideo.isVisible = true
            }
            CallStateType.Prepared,
            CallStateType.Idle,
            CallStateType.Failed -> {
                mViewBinding.vLeft.alpha = 0f
                mViewBinding.vRight.alpha = 0f
                mViewBinding.btnCall.isVisible = true
                mViewBinding.btnHangUp.isVisible = false
                mViewBinding.btnAudio.isVisible = false
                mViewBinding.btnVideo.isVisible = false
            }
            else -> {}
        }
    }

    // Check signal channel connection status
    // 检查信令通道链接状态
    private fun checkConnectionAndNotify(): Boolean {
        if (enterModel.isRtm) {
            val manager = rtmManager ?: return false
            if (!manager.isConnected) {
                Toasty.normal(this, getString(R.string.toast_rtm_not_logged_in), Toast.LENGTH_SHORT).show()
                return false
            }
            return true
        } else {
            val client = emClient ?: return false
            if (!client.isConnected) {
                Toasty.normal(this, getString(R.string.toast_easemob_not_logged_in), Toast.LENGTH_SHORT).show()
            }
            return client.isConnected
        }
    }

    private fun _createRtcEngine(): RtcEngineEx {
        var rtcEngine: RtcEngineEx? = null
        val config = RtcEngineConfig()
        config.mContext = this
        config.mAppId = BuildConfig.AG_APP_ID
        config.mEventHandler = object : IRtcEngineEventHandler() {
            override fun onError(err: Int) {
                super.onError(err)
                Log.e(TAG, "IRtcEngineEventHandler onError:$err")
            }
        }
        config.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
        config.mAudioScenario = Constants.AUDIO_SCENARIO_CHORUS
        try {
            rtcEngine = RtcEngine.create(config) as RtcEngineEx
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "RtcEngine.create() called error: $e")
        }
        return rtcEngine ?: throw RuntimeException("RtcEngine create failed!")
    }

    private fun setupView() {
        mViewBinding.tvCurrentId.text = getString(R.string.label_current_user_id, enterModel.currentUid)
        mViewBinding.etTargetUid.setText(SPUtil.getString(kTargetUserId, ""))
        mViewBinding.btnQuitChannel.setOnClickListener {
            closeAction()
        }
        mViewBinding.statisticLayout.tvEncodeDimensions.isVisible = enterModel.isBrodCaster
        mViewBinding.statisticLayout.tvEncodeFrameRate.isVisible = enterModel.isBrodCaster
        mViewBinding.statisticLayout.tvEncodeBitrate.isVisible = enterModel.isBrodCaster
        mViewBinding.statisticLayout.tvStatistic.setOnClickListener {
            val isTlStatistic = mViewBinding.statisticLayout.tlStatistic.isVisible
            mViewBinding.statisticLayout.tlStatistic.isVisible = !isTlStatistic
        }
        mViewBinding.btnCall.visibility = View.GONE
        mViewBinding.btnHangUp.visibility = View.GONE

        var btnCallThrottling = false
        mViewBinding.btnCall.setOnClickListener {
            if (!btnCallThrottling) {
                mViewBinding.etTargetUid.clearFocus()
                callAction()
                btnCallThrottling = true
                it.postDelayed({ btnCallThrottling = false }, 1000L)
            }
        }
        var btnHangUpThrottling = false
        mViewBinding.btnHangUp.setOnClickListener {
            if (!btnHangUpThrottling) {
                mViewBinding.etTargetUid.clearFocus()
                hangupAction()
                btnHangUpThrottling = true
                it.postDelayed({ btnHangUpThrottling = false }, 1000L)
            }
        }
        mViewBinding.btnAudio.setOnClickListener {
            audioAction()
        }
        mViewBinding.btnVideo.setOnClickListener {
            videoAction()
        }
        ViewCompat.setOnApplyWindowInsetsListener(mViewBinding.root) { _, insets ->
            val systemInset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.d(
                TAG,
                "systemInset l:${systemInset.left},t:${systemInset.top},r:${systemInset.right},b:${systemInset.bottom}"
            )
            mViewBinding.root.setPaddingRelative(
                systemInset.left + mViewBinding.root.paddingLeft,
                0,
                systemInset.right + mViewBinding.root.paddingRight,
                0
            )
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun closeAction() {
        api.deinitialize {
            api.removeListener(this)
            rtcEngine.stopPreview()
            rtcEngine.leaveChannel()
            RtcEngine.destroy()
            rtmManager?.logout()
            rtmManager = null
            emClient?.clean()
            emClient = null
            finish()
        }
    }

    private fun callAction() {
        // Check signal channel connection status
        // 检查信令通道链接状态
        if (!checkConnectionAndNotify()) return
        if (this.mCallState != CallStateType.Prepared) {
            initCallApi { _ ->
            }
            Toasty.normal(this, getString(R.string.toast_call_api_initializing), Toast.LENGTH_SHORT).show()
            return
        }
        val roomId = (mViewBinding.etTargetUid.text ?: "").toString()
        val targetUserId = roomId.toIntOrNull()
        if (roomId.isEmpty() || targetUserId == null) {
            Toasty.normal(this, getString(R.string.toast_no_target_user), Toast.LENGTH_SHORT).show()
            return
        }
        SPUtil.putString(kTargetUserId, roomId)

        callTypeDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.call_type_dialog_title))
            .setMessage(getString(R.string.call_type_dialog_message))
            .setPositiveButton(getString(R.string.call_type_audio)) { p0, p1 ->
                api.call(targetUserId, CallType.Audio, mapOf("key1" to "value1", "key2" to "value2")) { error ->
                    // Call fails, hang up immediately
                    // call 失败立刻挂断
                    if (error != null && mCallState == CallStateType.Calling) {
                        api.cancelCall {  }
                    }
                }
            }.setNegativeButton(getString(R.string.call_type_video))  { p0, p1 ->
                api.call(targetUserId) { error ->
                    // Call fails, hang up immediately
                    // call 失败立刻挂断
                    if (error != null && mCallState == CallStateType.Calling) {
                        api.cancelCall {  }
                    }
                }
            }.create()
        callTypeDialog?.setCancelable(false)
        callTypeDialog?.show()
    }

    private fun hangupAction() {
        // Check signal channel connection status
        // 检查信令通道链接状态
        if (!checkConnectionAndNotify()) return
        api.hangup(connectedUserId ?: 0, "hangup by user") {
        }
    }

    private var isAudioMuted = false
    private fun audioAction() {
        val channelName = connectedChannel ?: return
        val uid = enterModel.currentUid
        val connection = RtcConnection(channelName, uid.toInt())
        isAudioMuted = !isAudioMuted
        val ret: Int
        if (isAudioMuted) {
            ret = rtcEngine.muteLocalAudioStreamEx(true, connection)
            mViewBinding.btnAudio.setText(R.string.pure_1v1_audio_on)
        } else {
            ret = rtcEngine.muteLocalAudioStreamEx(false, connection)
            mViewBinding.btnAudio.setText(R.string.pure_1v1_audio_off)
        }
        Log.d(TAG, "isAudioMute: $isAudioMuted ret: $ret")
    }

    private var isVideoMuted = false
    private fun videoAction() {
        val channelName = connectedChannel ?: return
        val uid = enterModel.currentUid
        val connection = RtcConnection(channelName, uid.toInt())
        isVideoMuted = !isVideoMuted
        val ret: Int
        if (isVideoMuted) {
            rtcEngine.stopPreview()
            ret = rtcEngine.muteLocalVideoStreamEx(true, connection)
            mViewBinding.btnVideo.setText(R.string.pure_1v1_video_on)
        } else {
            rtcEngine.startPreview()
            ret = rtcEngine.muteLocalVideoStreamEx(false, connection)
            mViewBinding.btnVideo.setText(R.string.pure_1v1_video_off)
        }
        Log.d(TAG, "isVideoMuted: $isVideoMuted ret: $ret")
    }

    override fun onDestroy() {
        api.removeListener(this)
        super.onDestroy()
    }

    override fun onCallStateChanged(
        state: CallStateType,
        stateReason: CallStateReason,
        eventReason: String,
        eventInfo: Map<String, Any>
    ) {
        Log.d(TAG, "onCallStateChanged state: ${state.value}, stateReason: ${stateReason.value}, eventReason: $eventReason, eventInfo: $eventInfo")
        runOnUiThread {
            val publisher = eventInfo.getOrDefault(CallApiImpl.kPublisher, enterModel.currentUid)
            if (publisher != enterModel.currentUid) {
                return@runOnUiThread
            }
            updateCallState(state, stateReason)

            when (state) {
                CallStateType.Calling -> {
                    val fromUserId = eventInfo[CallApiImpl.kFromUserId] as? Int ?: 0
                    val fromRoomId = eventInfo[CallApiImpl.kFromRoomId] as? String ?: ""
                    val toUserId = eventInfo[CallApiImpl.kRemoteUserId] as? Int ?: 0
                    if (connectedUserId != null && connectedUserId != fromUserId) {
                        api.reject(fromUserId, "already calling") {
                        }
                        return@runOnUiThread
                    }
                    // Only handle if target user is self
                    // 触发状态的用户是自己才处理
                    if (enterModel.currentUid.toIntOrNull() == toUserId) {
                        connectedUserId = fromUserId
                        connectedChannel = fromRoomId
                        callDialog = AlertDialog.Builder(this)
                            .setTitle(getString(R.string.alert_title))
                            .setMessage(getString(R.string.alert_incoming_call, fromUserId))
                            .setPositiveButton(getString(R.string.alert_accept)) { p0, p1 ->
                                // Check signal channel connection status
                                // 检查信令通道链接状态
                                if (!checkConnectionAndNotify()) return@setPositiveButton
                                api.accept(fromUserId) { err ->
                                    if (err != null) {
                                        // If accept message fails, reject and return to initial state
                                        // 如果接受消息出错，则发起拒绝，回到初始状态
                                        api.reject(fromUserId, err.msg) {}
                                    }
                                }
                            }.setNegativeButton(getString(R.string.alert_reject)) { p0, p1 ->
                                // Check signal channel connection status
                                // 检查信令通道链接状态
                                if (!checkConnectionAndNotify()) return@setNegativeButton
                                api.reject(fromUserId, "reject by user") { err ->
                                }
                            }.create()
                        callDialog?.setCancelable(false)
                        callDialog?.show()
                    } else if (enterModel.currentUid.toIntOrNull() == fromUserId) {
                        connectedUserId = toUserId
                        connectedChannel = fromRoomId
                        callDialog = AlertDialog.Builder(this)
                            .setTitle(getString(R.string.alert_title))
                            .setMessage(getString(R.string.alert_calling_user, toUserId))
                            .setNegativeButton(getString(R.string.alert_cancel)) { p0, p1 ->
                                // Check signal channel connection status
                                // 检查信令通道链接状态
                                if (!checkConnectionAndNotify()) return@setNegativeButton
                                api.cancelCall { err ->
                                }
                            }.create()
                        callDialog?.setCancelable(false)
                        callDialog?.show()
                    }
                }
                CallStateType.Connected -> {
                    Toasty.normal(
                        this,
                        getString(R.string.toast_call_started, eventInfo.getOrDefault(CallApiImpl.kCostTimeMap, "")),
                        Toast.LENGTH_LONG
                    ).show()
                    callDialog?.dismiss()
                    callDialog = null

                    videoEncoderConfig?.let { config ->
                        rtcEngine.setVideoEncoderConfiguration(config)
                        val cameraConfig =
                            CameraCapturerConfiguration(CameraCapturerConfiguration.CAMERA_DIRECTION.CAMERA_FRONT)
                        cameraConfig.captureFormat.width = config.dimensions.width
                        cameraConfig.captureFormat.height = config.dimensions.height
                        cameraConfig.captureFormat.fps = config.frameRate
                        rtcEngine.setCameraCapturerConfiguration(cameraConfig)
                    }
                }
                CallStateType.Prepared -> {
                    when (stateReason) {
                        CallStateReason.LocalHangup, CallStateReason.RemoteHangup -> {
                            Toasty.normal(this, getString(R.string.toast_call_ended), Toast.LENGTH_SHORT).show()
                        }
                        CallStateReason.LocalRejected,
                        CallStateReason.RemoteRejected -> {
                            Toasty.normal(this, getString(R.string.toast_call_rejected), Toast.LENGTH_SHORT).show()
                        }
                        CallStateReason.CallingTimeout -> {
                            Toasty.normal(this, getString(R.string.toast_no_answer), Toast.LENGTH_SHORT).show()
                        }
                        CallStateReason.RemoteCallBusy -> {
                            Toasty.normal(this, getString(R.string.toast_user_busy), Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                    callDialog?.dismiss()
                    callDialog = null
                    connectedUserId = null
                    connectedChannel = null
                    isVideoMuted = false
                    isAudioMuted = false
                }
                CallStateType.Failed -> {
                    Toasty.normal(this, eventReason, Toast.LENGTH_LONG).show()
                    callDialog?.dismiss()
                    callDialog = null
                    connectedUserId = null
                    connectedChannel = null
                    isVideoMuted = false
                    isAudioMuted = false
                    closeAction()
                }
                else -> {}
            }
        }
    }

    override fun onCallEventChanged(event: CallEvent, eventReason: String?) {
        Log.d(TAG, "onCallEventChanged: $event, eventReason: $eventReason")
        when(event) {
            CallEvent.RemoteLeft -> {
                // Demo monitors remote user leaving to end abnormal calls. In real business scenarios, it is recommended to use server-side monitoring of RTC user offline for kicking users, and client-side monitoring of kicks to end abnormal calls
                // Demo通过监听远端用户离开进行结束异常通话，真实业务场景推荐使用服务端监听RTC用户离线来进行踢人，客户端通过监听踢人来结束异常通话
                hangupAction()
            }
            CallEvent.JoinRTCStart -> {
                rtcEngine.addHandlerEx(
                    object : IRtcEngineEventHandler() {
                        override fun onJoinChannelSuccess(
                            channel: String?,
                            uid: Int,
                            elapsed: Int
                        ) {
                            super.onJoinChannelSuccess(channel, uid, elapsed)
                            Log.d(TAG, "onJoinChannelSuccess, channel:$channel, uid:$channel")
                        }

                        override fun onRemoteAudioStateChanged(
                            uid: Int,
                            state: Int,
                            reason: Int,
                            elapsed: Int
                        ) {
                            super.onRemoteAudioStateChanged(uid, state, reason, elapsed)
                            Log.d(TAG, "onRemoteAudioStateChanged, uid:$uid, state:$state, reason:$reason")
                        }
                    },
                    RtcConnection(enterModel.currentUid, enterModel.currentUid.toInt()) // demo 为了方便将本端uid的字符串作为了频道名
                )
            }
            else -> {}
        }
    }

    override fun onCallError(
        errorEvent: CallErrorEvent,
        errorType: CallErrorCodeType,
        errorCode: Int,
        message: String?
    ) {
        Log.d(TAG, "onCallError: $errorEvent")
    }

    override fun onCallConnected(
        roomId: String,
        callUserId: Int,
        currentUserId: Int,
        timestamp: Long
    ) {
        super.onCallConnected(roomId, callUserId, currentUserId, timestamp)
        Log.d(TAG, "onCallConnected, roomId: $roomId, callUserId: $callUserId, currentUserId: $currentUserId, timestamp: $timestamp")
        runOnUiThread {
            mViewBinding.tvText.text = getString(R.string.call_status_start, roomId, callUserId, currentUserId, timestamp)
        }
    }

    override fun onCallDisconnected(
        roomId: String,
        hangupUserId: Int,
        currentUserId: Int,
        timestamp: Long,
        duration: Long
    ) {
        super.onCallDisconnected(roomId, hangupUserId, currentUserId, timestamp, duration)
        Log.d(TAG, "onCallDisconnected, roomId: $roomId, hangupUserId: $hangupUserId, currentUserId: $currentUserId, timestamp: $timestamp, duration:$duration")
        runOnUiThread {
            mViewBinding.tvText.text = getString(R.string.call_status_end, roomId, hangupUserId, currentUserId, timestamp, duration)
        }
    }

    override fun canJoinRtcOnCalling(eventInfo: Map<String, Any>): Boolean {
        return true
    }

    override fun tokenPrivilegeWillExpire() {
        var rtcTokenTemp = ""
        var rtmTokenTemp = ""
        val runnable = Runnable {
            if (rtcTokenTemp.isNotEmpty() && rtmTokenTemp.isNotEmpty()) {
                api.renewToken(rtcTokenTemp)
                if (enterModel.isBrodCaster) {
                    rtcEngine.renewToken(enterModel.showRoomToken)
                }
            }
        }
        HttpManager.token007("", enterModel.currentUid) { token ->
            runOnUiThread {
                if (token != null) {
                    rtcTokenTemp = token
                    rtmTokenTemp = token
                    runnable.run()
                }
            }
        }
        // Update broadcaster channel token for audience
        // 观众更新主播频道token
        if (!enterModel.isBrodCaster) {
            HttpManager.token007(enterModel.showRoomId, enterModel.currentUid) { rtcToken ->
                runOnUiThread {
                    if (rtcToken != null) {
                        enterModel.showRoomToken = rtcToken
                        rtcEngine.renewToken(enterModel.showRoomToken)
                    }
                }
            }
        }
    }

    override fun callDebugInfo(message: String, logLevel: CallLogLevel) {
        when (logLevel) {
            CallLogLevel.Normal -> Ov1Logger.d(TAG, message)
            CallLogLevel.Warning -> Ov1Logger.w(TAG, message)
            CallLogLevel.Error -> Ov1Logger.e(TAG, message)
        }
    }
}