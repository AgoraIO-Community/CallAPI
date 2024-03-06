package io.agora.onetoone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import es.dmoral.toasty.Toasty
import io.agora.onetoone.*
import io.agora.onetoone.databinding.ActivityLivingBinding
import io.agora.onetoone.http.HttpManager
import io.agora.onetoone.model.EnterRoomInfoModel
import io.agora.onetoone.signalClient.*
import io.agora.onetoone.utils.Ov1Logger
import io.agora.onetoone.utils.PermissionHelp
import io.agora.rtc2.*
import io.agora.rtc2.video.CameraCapturerConfiguration
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import io.agora.rtm.*

enum class CallRole(val value: Int) {
    CALLEE(0),
    CALLER(1)
}
class LivingActivity : AppCompatActivity(), ICallApiListener {

    companion object {

        const val KEY_ENTER_ROOM_MODEL = "KEY_ENTER_ROOM_MODEL"

        fun launch(context: Context, model: EnterRoomInfoModel) {
            val intent = Intent(context, LivingActivity::class.java)
            val bundle = Bundle()
            bundle.putSerializable(KEY_ENTER_ROOM_MODEL, model)
            intent.putExtras(bundle)
            context.startActivity(intent)
        }
    }

    private val enterModel by lazy {
        val bundle = intent.extras
        bundle!!.getSerializable(KEY_ENTER_ROOM_MODEL) as EnterRoomInfoModel
    }

    var videoEncoderConfig: VideoEncoderConfiguration? = null
    private var connectedUserId: Int? = null

    private val TAG = "LivingActivity_LOG"

    private val mViewBinding by lazy { ActivityLivingBinding.inflate(LayoutInflater.from(this)) }

    private lateinit var rtcEngine: RtcEngine
    private lateinit var api: CallApiImpl
    private var rtmManager: CallRtmManager? = null
    private var emClient: CallEasemobSignalClient? = null

    private var mCallState = CallStateType.Idle
    private var role: CallRole = CallRole.CALLEE         //角色
    private lateinit var prepareConfig: PrepareConfig

    private val mCenterCanvas by lazy { TextureView(this) }

    private var callDialog: AlertDialog? = null

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
        prepareConfig.rtmToken = enterModel.rtmToken
        prepareConfig.autoJoinRTC = enterModel.autoJoinRTC

        role = if (enterModel.isBrodCaster) CallRole.CALLEE else CallRole.CALLER

        rtcEngine = _createRtcEngine()
        setupView()
        mCallState = CallStateType.Idle
        updateCallState(CallStateType.Idle)

        // 初始化
        initMessageManager {}

        PermissionHelp(this).checkCameraAndMicPerms(
            {
                rtcJoinChannel()
            },
            { finish() },
            true
        )
    }

    private fun initMessageManager(completion: ((Boolean) -> Unit)) {
        if (enterModel.isRtm) {
            // 使用RtmManager管理RTM
            rtmManager = createRtmManager(BuildConfig.AG_APP_ID, enterModel.currentUid.toInt())
            // rtm login
            rtmManager?.login(prepareConfig.rtmToken) {
                if (it == null) {
                    // login 成功后初始化 call api
                    initCallApi(completion)
                } else {
                    completion.invoke(false)
                }
            }
            // 监听 rtm manager 事件
            rtmManager?.addListener(object : ICallRtmManagerListener {
                override fun onConnected() {
                    mViewBinding.root.post {
                        Toasty.normal(this@LivingActivity, "rtm已连接", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onDisconnected() {
                    mViewBinding.root.post {
                        Toasty.normal(this@LivingActivity, "rtm已断开", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onConnectionLost() {
                    // 表示rtm超时断连了，需要重新登录，这里模拟了3s重新登录
                    mViewBinding.root.post {
                        Toasty.normal(this@LivingActivity, "rtm连接错误，需要重新登录", Toast.LENGTH_SHORT)
                            .show()
                    }
                    mViewBinding.root.postDelayed({
                        rtmManager?.logout()
                        rtmManager?.login(prepareConfig.rtmToken) {}
                    }, 3000)
                }

                override fun onTokenPrivilegeWillExpire(channelName: String) {
                    // 重新获取token
                    tokenPrivilegeWillExpire()
                }
            })
        } else {
            emClient = createEasemobSignalClient(this, BuildConfig.IM_APP_KEY, enterModel.currentUid.toInt())
            emClient?.login {
                if (it) {
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
            rtcEngine = rtcEngine as RtcEngineEx,
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

    private fun updateCallState(state: CallStateType) {
        runOnUiThread {
            when (state) {
                CallStateType.Calling -> {
                    publishMedia(false)
                    setupCanvas(null)
                    mViewBinding.vRight.alpha = 1f
                    mViewBinding.vCenter.removeView(mCenterCanvas)
                    mViewBinding.btnHangUp.isVisible = false
                    mViewBinding.btnCall.isVisible = false
                }
                CallStateType.Prepared,
                CallStateType.Idle,
                CallStateType.Failed -> {
                    rtcEngine.enableLocalAudio(true)
                    rtcEngine.enableLocalVideo(true)
                    publishMedia(true)
                    if (mViewBinding.vCenter.indexOfChild(mCenterCanvas) == -1) {
                        mViewBinding.vCenter.addView(mCenterCanvas)
                    }
                    setupCanvas(mCenterCanvas)
                    mCenterCanvas.isVisible = true
                    mViewBinding.vLeft.alpha = 0f
                    mViewBinding.vRight.alpha = 0f
                    mViewBinding.btnCall.isVisible = !enterModel.isBrodCaster
                    mViewBinding.btnHangUp.isVisible = false
                }
                CallStateType.Connected -> {
                    mViewBinding.vLeft.alpha = 1f
                    mCenterCanvas.isVisible = false
                    mViewBinding.vCenter.removeView(mCenterCanvas)
                    mViewBinding.btnHangUp.isVisible = true
                    mViewBinding.btnCall.isVisible = false
                }
                else -> {}
            }
        }
    }

    private fun rtcJoinChannel() {
        val options = ChannelMediaOptions()
        options.clientRoleType = if (enterModel.isBrodCaster) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
        options.publishMicrophoneTrack = enterModel.isBrodCaster
        options.publishCameraTrack = enterModel.isBrodCaster
        options.autoSubscribeAudio = !enterModel.isBrodCaster
        options.autoSubscribeVideo = !enterModel.isBrodCaster
        val ret = rtcEngine.joinChannel(enterModel.showRoomToken, enterModel.showRoomId, enterModel.currentUid.toInt(), options)
        if (ret == Constants.ERR_OK) {
            Log.d(TAG, "join rtc room success")
        }else{
            Log.d(TAG, "join rtc room failed")
        }
    }

    // 检查信令通道链接状态
    private fun checkConnectionAndNotify(): Boolean {
        if (enterModel.isRtm) {
            val manager = rtmManager ?: return false
            if (!manager.isConnected) {
                Toasty.normal(this, "rtm未登录或连接异常", Toast.LENGTH_SHORT).show()
                return false
            }
            return true
        } else {
            val client = emClient ?: return false
            if (!client.isConnected) {
                Toasty.normal(this, "环信未登录或连接异常", Toast.LENGTH_SHORT).show()
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

    private fun setupCanvas(canvasView: TextureView?) {
        if (enterModel.isBrodCaster) {
            _setupLocalVideo(enterModel.currentUid.toInt(), canvasView)
        } else {
            _setupRemoteVideo(enterModel.showRoomId, enterModel.showUserId.toInt(), canvasView)
        }
    }

    private var mLocalCanvas: TextureView? = null
    private fun _setupLocalVideo(uid: Int, canvasView: TextureView?) {
        if (mLocalCanvas != canvasView) {
            mLocalCanvas = canvasView
            val videoCanvas = VideoCanvas(canvasView)
            videoCanvas.uid = uid
            videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN
            videoCanvas.mirrorMode = Constants.VIDEO_MIRROR_MODE_DISABLED

            rtcEngine.enableAudio()
            rtcEngine.enableVideo()
            rtcEngine.setDefaultAudioRoutetoSpeakerphone(true)
            rtcEngine.setupLocalVideo(videoCanvas)
            rtcEngine.startPreview()

            //setup configuration after join channel
            videoEncoderConfig?.let { config ->
                rtcEngine.setVideoEncoderConfiguration(config)
                val cameraConfig = CameraCapturerConfiguration(CameraCapturerConfiguration.CAMERA_DIRECTION.CAMERA_FRONT)
                cameraConfig.captureFormat.width = config.dimensions.width
                cameraConfig.captureFormat.height = config.dimensions.height
                cameraConfig.captureFormat.fps = config.frameRate
                rtcEngine.setCameraCapturerConfiguration(cameraConfig)
            }
        }
    }

    private fun _setupRemoteVideo(roomId: String, uid: Int, canvasView: TextureView?) {
        val videoCanvas = VideoCanvas(canvasView)
        videoCanvas.uid = uid
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN
        videoCanvas.mirrorMode = Constants.VIDEO_MIRROR_MODE_AUTO

        val ret = rtcEngine.setupRemoteVideo(videoCanvas)
        Log.d(TAG, "_setupRemoteVideo ret: $ret, roomId: $roomId, uid: $uid")
    }

    private fun publishMedia(publish: Boolean) {
        val options = ChannelMediaOptions()
        options.publishMicrophoneTrack = publish
        options.publishCameraTrack = publish
        options.autoSubscribeVideo = publish
        options.autoSubscribeAudio = publish
        rtcEngine.updateChannelMediaOptions(options)
    }

    private fun setupView() {
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        mCenterCanvas.layoutParams = layoutParams

        mViewBinding.tvChannelName.text = getString(R.string.app_current_channel, enterModel.showRoomId)
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
        if (enterModel.isBrodCaster) {
            mViewBinding.btnCall.visibility = View.INVISIBLE
            mViewBinding.btnHangUp.visibility = View.INVISIBLE
        }
        var btnCallThrottling = false
        mViewBinding.btnCall.setOnClickListener {
            if (!btnCallThrottling) {
                callAction()
                btnCallThrottling = true
                it.postDelayed({ btnCallThrottling = false }, 1000L)
            }
        }
        var btnHangUpThrottling = false
        mViewBinding.btnHangUp.setOnClickListener {
            if (!btnHangUpThrottling) {
                hangupAction()
                btnHangUpThrottling = true
                it.postDelayed({ btnHangUpThrottling = false }, 1000L)
            }
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
        // 检查信令通道链接状态
        if (!checkConnectionAndNotify()) return
        publishMedia(false)
        api.call(enterModel.showUserId.toInt()) { error ->
            // call 失败立刻挂断
            if (error != null && mCallState == CallStateType.Calling) {
                api.cancelCall {  }
            }
        }
    }

    private fun hangupAction() {
        // 检查信令通道链接状态
        if (!checkConnectionAndNotify()) return
        val connectedUserId = connectedUserId ?: return
        api.hangup(connectedUserId, "hangup by user") {
        }
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
        val publisher = eventInfo.getOrDefault(CallApiImpl.kPublisher, enterModel.currentUid)
        if (publisher != enterModel.currentUid) {
            when (state) {
                CallStateType.Calling, CallStateType.Connecting, CallStateType.Connected -> {
                    setupCanvas(null)
                }
                else -> {
                    setupCanvas(mCenterCanvas)
                }
            }
            return
        }
        Log.d(TAG, "onCallStateChanged state: ${state.value}, stateReason: ${stateReason.value}, eventReason: $eventReason, eventInfo: $eventInfo publisher: $publisher / ${enterModel.currentUid}")
        mCallState = state
        updateCallState(state)

        when (state) {
            CallStateType.Calling -> {
                val fromUserId = eventInfo[CallApiImpl.kFromUserId] as? Int ?: 0
                val toUserId = eventInfo[CallApiImpl.kRemoteUserId] as? Int ?: 0
                if (connectedUserId != null && connectedUserId != fromUserId) {
                    // 检查信令通道链接状态
                    if (!checkConnectionAndNotify()) return
                    api.reject(fromUserId, "already calling") {
                    }
                    return
                }
                // 触发状态的用户是自己才处理
                if (enterModel.currentUid.toIntOrNull() == toUserId) {
                    connectedUserId = fromUserId
                    // 检查信令通道链接状态
                    if (!checkConnectionAndNotify()) return
                    api.accept(remoteUserId = fromUserId) { err ->
                        if (err != null) {
                            //如果接受消息出错，则发起拒绝，回到初始状态
                            api.reject(fromUserId, err.msg) {}
                        }
                    }
                } else if (enterModel.currentUid.toIntOrNull() == fromUserId) {
                    connectedUserId = toUserId
                    callDialog = AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("呼叫用户 $toUserId 中")
                        .setNegativeButton("取消") { p0, p1 ->
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
                callDialog?.dismiss()
                callDialog = null

                Toasty.normal(this, "通话开始${eventInfo.getOrDefault(CallApiImpl.kCostTimeMap, "")}", Toast.LENGTH_LONG).show()

                //setup configuration after join channel
                videoEncoderConfig?.let { config ->
                    rtcEngine.setVideoEncoderConfiguration(config)
                    val cameraConfig = CameraCapturerConfiguration(CameraCapturerConfiguration.CAMERA_DIRECTION.CAMERA_FRONT)
                    cameraConfig.captureFormat.width = config.dimensions.width
                    cameraConfig.captureFormat.height = config.dimensions.height
                    cameraConfig.captureFormat.fps = config.frameRate
                    rtcEngine.setCameraCapturerConfiguration(cameraConfig)
                }
            }
            CallStateType.Prepared -> {
                when (stateReason) {
                    CallStateReason.LocalHangup,
                    CallStateReason.RemoteHangup -> {
                        Toasty.normal(this, "通话结束", Toast.LENGTH_SHORT).show()
                    }
                    CallStateReason.LocalRejected,
                    CallStateReason.RemoteRejected -> {
                        Toasty.normal(this, "通话被拒绝", Toast.LENGTH_SHORT).show()
                    }
                    CallStateReason.CallingTimeout -> {
                        Toasty.normal(this, "无应答", Toast.LENGTH_SHORT).show()
                    }
                    CallStateReason.RemoteCallBusy -> {
                        Toasty.normal(this, "用户正忙", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
                connectedUserId = null
                callDialog?.dismiss()
                callDialog = null
            }
            CallStateType.Failed -> {
                Toasty.normal(this, eventReason, Toast.LENGTH_LONG).show()
                closeAction()
            }
            else -> {}
        }
    }

    override fun onCallEventChanged(event: CallEvent, eventReason: String?) {
        Log.d(TAG, "onCallEventChanged: $event, eventReason: $eventReason")
        when(event) {
            CallEvent.RemoteLeave -> {
                hangupAction()
            } else -> {}
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
            mViewBinding.tvText.text = "通话开始, \nRTC 频道号: $roomId, \n主叫用户id: $callUserId, \n当前用户id: $currentUserId, \n开始时间戳: $timestamp ms"
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
        Log.d(TAG, "onCallDisconnected, roomId: $roomId, hangupUserId: $hangupUserId, currentUserId: $currentUserId, timestamp: $timestamp ms, duration:$duration ms")
        runOnUiThread {
            mViewBinding.tvText.text = "通话结束, \nRTC 频道号: $roomId, \n挂断用户id: $hangupUserId, \n当前用户id: $currentUserId, \n结束时间戳: $timestamp ms， \n通话时长: $duration ms"
        }
    }

    override fun canJoinRtcOnCalling(eventInfo: Map<String, Any>): Boolean? {
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
        HttpManager.token007("", enterModel.currentUid, 1) { rtcToken ->
            runOnUiThread {
                if (rtcToken != null) {
                    rtcTokenTemp = rtcToken
                    runnable.run()
                }
            }
        }
        HttpManager.token007("", enterModel.currentUid, 2) { rtmToken ->
            runOnUiThread {
                if (rtmToken != null) {
                    rtmTokenTemp = rtmToken
                    runnable.run()
                }
            }
        }
        //观众更新主播频道token
        if (!enterModel.isBrodCaster) {
            HttpManager.token007(enterModel.showRoomId, enterModel.currentUid, 1) { rtcToken ->
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