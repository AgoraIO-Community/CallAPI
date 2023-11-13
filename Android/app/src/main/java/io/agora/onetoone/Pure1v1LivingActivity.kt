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
import io.agora.onetoone.databinding.ActivityPure1v1LivingBinding
import io.agora.onetoone.http.HttpManager
import io.agora.onetoone.model.EnterRoomInfoModel
import io.agora.onetoone.utils.Ov1Logger
import io.agora.onetoone.utils.PermissionHelp
import io.agora.onetoone.utils.SPUtil
import io.agora.rtc2.*
import io.agora.rtc2.video.CameraCapturerConfiguration
import io.agora.rtc2.video.VideoEncoderConfiguration
import io.agora.rtm.*

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

    private val kRemoteUserId = "remoteUserId"
    private val kFromUserId = "fromUserId"
    private val kFromRoomId = "fromRoomId"
    private val kTargetUserId = "targetUserId"

    private val enterModel by lazy {
        val bundle = intent.extras
        bundle!!.getSerializable(KEY_ENTER_ROOM_MODEL) as EnterRoomInfoModel
    }

    var videoEncoderConfig: VideoEncoderConfiguration? = null
    private var connectedUserId: Int? = null
    private var connectedRoomId: String? = null

    private val TAG = "Pure1v1LivingActivity_LOG"

    private val mViewBinding by lazy { ActivityPure1v1LivingBinding.inflate(LayoutInflater.from(this)) }

    private lateinit var rtcEngine: RtcEngineEx
    private var rtmClient: RtmClient? = null

    val api = CallApiImpl(this)

    private var mCallState = CallStateType.Idle

    private val mLeftCanvas by lazy { TextureView(this) }
    private val mRightCanvas by lazy { TextureView(this) }
    private var callDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mViewBinding.root)

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

        rtcEngine = _createRtcEngine()
        setupView()
        updateCallState(CallStateType.Idle)
        // 外部创建RTMClient
        rtmClient = _createRtmClient()
        initCallApi { success ->
        }

        PermissionHelp(this).checkCameraAndMicPerms(
            {
            },
            { finish() },
            true
        )
    }

    private fun initCallApi(completion: ((Boolean) -> Unit)) {
        // 外部创建需要自行管理login
        rtmClient?.login(enterModel.rtmToken, object: ResultCallback<Void?> {
            override fun onSuccess(p0: Void?) {
                _initialize(rtmClient, if (enterModel.isBrodCaster) CallRole.CALLEE else CallRole.CALLER) { success ->
                    Log.d(TAG, "_initialize: $success")
                    completion.invoke(success)
                }
            }
            override fun onFailure(p0: ErrorInfo?) {
                Log.e(TAG, "login error = ${p0.toString()}")
                completion.invoke(false)
            }
        })
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        closeAction()
    }
    private fun updateCallState(state: CallStateType) {
        mCallState = state
        when(mCallState) {
            CallStateType.Calling ->{
                mViewBinding.vLeft.isVisible = true
                mViewBinding.vRight.isVisible = true
                mViewBinding.btnCall.isVisible = false
                mViewBinding.btnHangUp.isVisible = false
            }
            CallStateType.Connected -> {
                mViewBinding.btnHangUp.isVisible = true
            }
            CallStateType.Prepared,
            CallStateType.Idle,
            CallStateType.Failed -> {
                mViewBinding.vLeft.isVisible = false
                mViewBinding.vRight.isVisible = false
                mViewBinding.btnCall.isVisible = true
                mViewBinding.btnHangUp.isVisible = false
            }
            else -> {}
        }
    }

    private fun _initialize(rtmClient: RtmClient?, role: CallRole, completion: ((Boolean) -> Unit)?) {
        val config = CallConfig(
            appId = BuildConfig.AG_APP_ID,
            userId = enterModel.currentUid.toInt(),
            userExtension = null,
            rtcEngine = rtcEngine,
            rtmClient = rtmClient,
            mode = CallMode.Pure1v1,
            role = CallRole.CALLER,
            localView = mLeftCanvas,
            remoteView = mRightCanvas,
            autoAccept = false
        )
        if (role == CallRole.CALLER) {
            config.localView = mRightCanvas
            config.remoteView = mLeftCanvas
        } else {
            config.localView = mLeftCanvas
            config.remoteView = mRightCanvas
        }

        val tokenConfig = CallTokenConfig()
        tokenConfig.roomId = enterModel.tokenRoomId
        tokenConfig.rtcToken = enterModel.rtcToken
        tokenConfig.rtmToken = enterModel.rtmToken

        api.initialize(config, tokenConfig) { error ->
            if (error != null) {
                completion?.invoke(false)
                return@initialize
            }
        }
        api.addListener(this)
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

    private fun _createRtmClient(): RtmClient {
        val rtmConfig = RtmConfig.Builder(BuildConfig.AG_APP_ID, enterModel.currentUid).build()
        if (rtmConfig.userId.isEmpty()) {
            Log.d(TAG, "userId is empty")
        }
        if (rtmConfig.appId.isEmpty()) {
            Log.d(TAG, "appId is empty")
        }
        return RtmClient.create(rtmConfig)
    }

    private fun setupView() {
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        mLeftCanvas.layoutParams = layoutParams
        mRightCanvas.layoutParams = layoutParams
        mViewBinding.vLeft.addView(mLeftCanvas)
        mViewBinding.vRight.addView(mRightCanvas)

        mViewBinding.tvCurrentId.text = "当前用户id：${enterModel.currentUid}"
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
            RtmClient.release()

            finish()
        }
    }

    private fun callAction() {
        if (this.mCallState == CallStateType.Prepared) else {
            initCallApi { success ->
            }
            Toasty.normal(this, "CallAPi初始化中", Toast.LENGTH_SHORT).show()
            return
        }
        val roomId = (mViewBinding.etTargetUid.text ?: "").toString()
        val targetUserId = roomId.toIntOrNull()
        if (roomId.isEmpty() || targetUserId == null) {
            Toasty.normal(this, "无目标用户", Toast.LENGTH_SHORT).show()
            return
        }
        SPUtil.putString(kTargetUserId, roomId)
        api.call(roomId, targetUserId) { error ->
        }
    }

    private fun hangupAction() {
        api.hangup(connectedUserId ?: 0) {
        }
    }

    override fun onDestroy() {
        api.removeListener(this)
        super.onDestroy()
    }

    override fun onCallStateChanged(
        state: CallStateType,
        stateReason: CallReason,
        eventReason: String,
        elapsed: Long,
        eventInfo: Map<String, Any>
    ) {
        val publisher = eventInfo.getOrDefault(CallApiImpl.kPublisher, enterModel.currentUid)
        if (publisher != enterModel.currentUid) {return}
        updateCallState(state)

        when (state) {
            CallStateType.Calling -> {
                val fromUserId = eventInfo[kFromUserId] as? Int ?: 0
                val fromRoomId = eventInfo[kFromRoomId] as? String ?: ""
                val toUserId = eventInfo[kRemoteUserId] as? Int ?: 0

                if (connectedUserId != null && connectedUserId != fromUserId) {
                    api.reject(fromUserId, "already calling") {
                    }
                    return
                }
                connectedRoomId = fromRoomId
                // 触发状态的用户是自己才处理
                if (enterModel.currentUid.toIntOrNull() == toUserId) {
                    connectedUserId = fromUserId
                    runOnUiThread {
                        callDialog = AlertDialog.Builder(this)
                            .setTitle("提示")
                            .setMessage("用户 $fromUserId 邀请您1对1通话")
                            .setPositiveButton("同意") { p0, p1 ->
                                HttpManager.token007(fromRoomId, enterModel.currentUid, 1) { rtcToken ->
                                    if (rtcToken != null) {
                                        api.accept(fromRoomId, fromUserId, rtcToken) { err ->
                                        }
                                    } else {
                                        Toasty.normal(this, "get RTC token failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }.setNegativeButton("拒绝") { p0, p1 ->
                                api.reject(fromUserId, "reject by user") { err ->
                                }
                            }.create()
                        callDialog?.setCancelable(false)
                        callDialog?.show()
                    }
                } else if (enterModel.currentUid.toIntOrNull() == fromUserId) {
                    connectedUserId = toUserId
                    callDialog = AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("呼叫用户 $toUserId 中")
                        .setNegativeButton("取消") { p0, p1 ->
                            api.cancelCall { err ->
                            }
                        }.create()
                    callDialog?.setCancelable(false)
                    callDialog?.show()
                }
            }
            CallStateType.Connected -> {
                Toasty.normal(this, "通话开始${eventInfo.getOrDefault(CallApiImpl.kDebugInfo, "")}", Toast.LENGTH_LONG).show()
                callDialog?.dismiss()
                callDialog = null

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
                    CallReason.LocalHangup, CallReason.RemoteHangup -> {
                        Toasty.normal(this, "通话结束", Toast.LENGTH_SHORT).show()
                    }
                    CallReason.LocalRejected,
                    CallReason.RemoteRejected -> {
                        Toasty.normal(this, "通话被拒绝", Toast.LENGTH_SHORT).show()
                    }
                    CallReason.CallingTimeout -> {
                        Toasty.normal(this, "无应答", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
                callDialog?.dismiss()
                callDialog = null
                connectedUserId = null
            }
            CallStateType.Failed -> {
                Toasty.normal(this, eventReason, Toast.LENGTH_LONG).show()
                callDialog?.dismiss()
                callDialog = null
                connectedUserId = null
                closeAction()
            }
            else -> {}
        }
    }

    override fun onCallEventChanged(event: CallEvent, elapsed: Long) {
        Log.d(TAG, "onCallEventChanged: ${event}, elapsed: $elapsed")
        when(event) {
            CallEvent.RemoteLeave -> {
                hangupAction()
            } else -> {}
        }
    }

    override fun tokenPrivilegeWillExpire() {
        //更新自己的token
        val tokenConfig = CallTokenConfig()
        tokenConfig.roomId = enterModel.showRoomId
        val runnable = Runnable {
            if (tokenConfig.rtcToken.isNotEmpty() && tokenConfig.rtmToken.isNotEmpty()) {
                api.renewToken(tokenConfig)
            }
        }
        val channelName = connectedRoomId ?: ""
        HttpManager.token007(channelName, enterModel.currentUid, 1) { rtcToken ->
            runOnUiThread {
                if (rtcToken != null) {
                    tokenConfig.rtcToken = rtcToken
                    runnable.run()
                }
            }
        }
        HttpManager.token007(channelName, enterModel.currentUid, 2) { rtmToken ->
            runOnUiThread {
                if (rtmToken != null) {
                    tokenConfig.rtmToken = rtmToken
                    runnable.run()
                }
            }
        }
        //如果是被叫(即1v1的频道是对方的频道)，需要更新对方频道的token
        val connectedRoomId = this.connectedRoomId
        if (connectedRoomId != null && connectedRoomId != enterModel.currentUid) {
            HttpManager.token007(connectedRoomId, enterModel.currentUid, 1) { rtcToken ->
                runOnUiThread {
                    if (rtcToken != null) {
                        api.renewRemoteCallerChannelToken(connectedRoomId, rtcToken)
                    }
                }
            }
        }
    }

    override fun callDebugInfo(message: String) {
        Ov1Logger.d(TAG, message)
    }

    override fun callDebugWarning(message: String) {
        Ov1Logger.e(TAG, message)
    }
}