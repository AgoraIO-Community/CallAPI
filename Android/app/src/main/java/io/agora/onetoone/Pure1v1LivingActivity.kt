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
    private val kTargetUserId = "targetUserId"

    private val enterModel by lazy {
        val bundle = intent.extras
        bundle!!.getSerializable(KEY_ENTER_ROOM_MODEL) as EnterRoomInfoModel
    }

    var videoEncoderConfig: VideoEncoderConfiguration? = null
    private var connectedUserId: Int? = null

    private val TAG = "Pure1v1LivingActivity_LOG"

    private val mViewBinding by lazy { ActivityPure1v1LivingBinding.inflate(LayoutInflater.from(this)) }

    private lateinit var rtcEngine: RtcEngineEx
    private var rtmClient: RtmClient? = null
    private lateinit var prepareConfig: PrepareConfig
    private lateinit var api: CallApiImpl

    private var mCallState = CallStateType.Idle

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
        //prepareConfig.autoAccept = enterModel.autoAccept

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
        rtmClient?.login(prepareConfig.rtmToken, object: ResultCallback<Void?> {
            override fun onSuccess(p0: Void?) {
                _initialize(rtmClient) { success ->
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
                mViewBinding.vRight.alpha = 1f
                mViewBinding.btnCall.isVisible = false
                mViewBinding.btnHangUp.isVisible = false
            }
            CallStateType.Connected -> {
                mViewBinding.vLeft.alpha = 1f
                mViewBinding.btnHangUp.isVisible = true
            }
            CallStateType.Prepared,
            CallStateType.Idle,
            CallStateType.Failed -> {
                mViewBinding.vLeft.alpha = 0f
                mViewBinding.vRight.alpha = 0f
                mViewBinding.btnCall.isVisible = true
                mViewBinding.btnHangUp.isVisible = false
            }
            else -> {}
        }
    }

    private fun _initialize(rtmClient: RtmClient?, completion: ((Boolean) -> Unit)?) {
        val config = CallConfig(
            appId = BuildConfig.AG_APP_ID,
            userId = enterModel.currentUid.toInt(),
            rtcEngine = rtcEngine,
            rtmClient = rtmClient,
        )
        api.initialize(config)

        prepareConfig.roomId = enterModel.currentUid
        prepareConfig.localView = mViewBinding.vRight
        prepareConfig.remoteView = mViewBinding.vLeft
        api.addListener(this)
        api.prepareForCall(prepareConfig) { error ->
            completion?.invoke(error == null)
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
        api.call(targetUserId) { error ->
        }
    }

    private fun hangupAction() {
        api.hangup(connectedUserId ?: 0, "hangup by user") {
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
        if (publisher != enterModel.currentUid) {return}
        updateCallState(state)

        when (state) {
            CallStateType.Calling -> {
                val fromUserId = eventInfo[kFromUserId] as? Int ?: 0
                val toUserId = eventInfo[kRemoteUserId] as? Int ?: 0

                if (connectedUserId != null && connectedUserId != fromUserId) {
                    api.reject(fromUserId, "already calling") {
                    }
                    return
                }
                // 触发状态的用户是自己才处理
                if (enterModel.currentUid.toIntOrNull() == toUserId) {
                    connectedUserId = fromUserId
                    callDialog = AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("用户 $fromUserId 邀请您1对1通话")
                        .setPositiveButton("同意") { p0, p1 ->
                            api.accept(fromUserId) { err ->
                            }
                        }.setNegativeButton("拒绝") { p0, p1 ->
                            api.reject(fromUserId, "reject by user") { err ->
                            }
                        }.create()
                    callDialog?.setCancelable(false)
                    callDialog?.show()
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
                Toasty.normal(this, "通话开始${eventInfo.getOrDefault(CallApiImpl.kCostTimeMap, "")}", Toast.LENGTH_LONG).show()
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
                    CallStateReason.LocalHangup, CallStateReason.RemoteHangup -> {
                        Toasty.normal(this, "通话结束", Toast.LENGTH_SHORT).show()
                    }
                    CallStateReason.LocalRejected,
                    CallStateReason.RemoteRejected -> {
                        Toasty.normal(this, "通话被拒绝", Toast.LENGTH_SHORT).show()
                    }
                    CallStateReason.CallingTimeout -> {
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
        Log.d(TAG, "onCallDisconnected, roomId: $roomId, hangupUserId: $hangupUserId, currentUserId: $currentUserId, timestamp: $timestamp, duration:$duration")
        runOnUiThread {
            mViewBinding.tvText.text = "通话结束, \nRTC 频道号: $roomId, \n挂断用户id: $hangupUserId, \n当前用户id: $currentUserId, \n结束时间戳: $timestamp ms， \n通话时长: $duration ms"
        }
    }

    override fun canJoinRTC(joinTiming: CalleeJoinRTCTiming): Boolean? {
        if (joinTiming == CalleeJoinRTCTiming.Calling) return true
        return false
    }

    override fun tokenPrivilegeWillExpire() {
        var rtcTokenTemp = ""
        var rtmTokenTemp = ""
        val runnable = Runnable {
            if (rtcTokenTemp.isNotEmpty() && rtmTokenTemp.isNotEmpty()) {
                api.renewToken(rtcTokenTemp, rtmTokenTemp)
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