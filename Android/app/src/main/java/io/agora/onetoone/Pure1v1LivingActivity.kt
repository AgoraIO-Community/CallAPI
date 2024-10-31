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
import io.agora.onetoone.signalClient.*
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

        // initialize
        initMessageManager { }

        PermissionHelp(this).checkCameraAndMicPerms(
            {
            },
            {
                Toasty.normal(this@Pure1v1LivingActivity, getString(R.string.app_no_permission), Toast.LENGTH_SHORT).show()
            },
            false
        )
    }

    private fun initMessageManager(completion: ((Boolean) -> Unit)) {
        if (enterModel.isRtm) {
            // Manage RTM using RtmManager
            rtmManager = createRtmManager(BuildConfig.AG_APP_ID, enterModel.currentUid.toInt())
            // rtm login
            rtmManager?.login(enterModel.rtmToken) {
                if (it == null) {
                    // Initialize call API after login success
                    initCallApi(completion)
                } else {
                    completion.invoke(false)
                }
            }
            // Listen to RTM manager events
            rtmManager?.addListener(object : ICallRtmManagerListener {
                override fun onConnected() {
                    mViewBinding.root.post {
                        Toasty.normal(this@Pure1v1LivingActivity, getString(R.string.app_rtm_connected), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onDisconnected() {
                    mViewBinding.root.post {
                        Toasty.normal(this@Pure1v1LivingActivity, getString(R.string.app_rtm_disconnected), Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                override fun onTokenPrivilegeWillExpire(channelName: String) {
                    // Renew token
                    tokenPrivilegeWillExpire()
                }
            })
        } else {
            emClient = createEasemobSignalClient(this, BuildConfig.IM_APP_KEY, enterModel.currentUid.toInt())
            emClient?.login {
                if (it) {
                    // Initialize call API after login success
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

    private fun updateCallState(state: CallStateType, stateReason: CallStateReason?) {
        mCallState = state
        when(mCallState) {
            CallStateType.Calling ->{
                if (stateReason == CallStateReason.LocalVideoCall || stateReason == CallStateReason.RemoteVideoCall) {
                    mViewBinding.vRight.isVisible = true
                    mViewBinding.vLeft.isVisible = true
                } else if (stateReason == CallStateReason.LocalAudioCall || stateReason == CallStateReason.RemoteAudioCall) {
                    mViewBinding.vRight.isVisible = false
                    mViewBinding.vLeft.isVisible = false
                }
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

    // Check the signaling channel connection status
    private fun checkConnectionAndNotify(): Boolean {
        if (enterModel.isRtm) {
            val manager = rtmManager ?: return false
            if (!manager.isConnected) {
                Toasty.normal(this, getString(R.string.app_rtm_connect_fail), Toast.LENGTH_SHORT).show()
                return false
            }
            return true
        } else {
            val client = emClient ?: return false
            if (!client.isConnected) {
                Toasty.normal(this, getString(R.string.app_easemob_connect_fail), Toast.LENGTH_SHORT).show()
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
        mViewBinding.tvCurrentId.text = "${getString(R.string.app_input_title_local_uid)}：${enterModel.currentUid}"
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
            rtmManager?.logout()
            rtmManager = null
            emClient?.clean()
            emClient = null
            finish()
        }
    }

    private fun callAction() {
        // Check the signaling channel connection status
        if (!checkConnectionAndNotify()) return
        if (this.mCallState == CallStateType.Prepared) else {
            initCallApi { success ->
            }
            Toasty.normal(this, getString(R.string.app_call_api_initializing), Toast.LENGTH_SHORT).show()
            return
        }
        val roomId = (mViewBinding.etTargetUid.text ?: "").toString()
        val targetUserId = roomId.toIntOrNull()
        if (roomId.isEmpty() || targetUserId == null) {
            Toasty.normal(this, getString(R.string.app_no_target_user), Toast.LENGTH_SHORT).show()
            return
        }
        SPUtil.putString(kTargetUserId, roomId)

        callTypeDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_select_call_type))
            .setMessage(getString(R.string.app_select_call_type_desc))
            .setPositiveButton(getString(R.string.app_call_type_audio)) { p0, p1 ->
                api.call(targetUserId, CallType.Audio, mapOf("key1" to "value1", "key2" to "value2")) { error ->
                    // Call failed, hang up immediately
                    if (error != null && mCallState == CallStateType.Calling) {
                        api.cancelCall {  }
                    }
                }
            }.setNegativeButton(getString(R.string.app_call_type_video)) { p0, p1 ->
                api.call(targetUserId) { error ->
                    // Call failed, hang up immediately
                    if (error != null && mCallState == CallStateType.Calling) {
                        api.cancelCall {  }
                    }
                }
            }.create()
        callTypeDialog?.setCancelable(false)
        callTypeDialog?.show()
    }

    private fun hangupAction() {
        // Check the signaling channel connection status
        if (!checkConnectionAndNotify()) return
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
        Log.d(TAG, "onCallStateChanged state: ${state.value}, stateReason: ${stateReason.value}, eventReason: $eventReason, eventInfo: $eventInfo")
        runOnUiThread {
            val publisher = eventInfo.getOrDefault(CallApiImpl.kPublisher, enterModel.currentUid)
            if (publisher != enterModel.currentUid) {
                return@runOnUiThread
            }
            updateCallState(state, stateReason)

            when (state) {
                CallStateType.Calling -> {
                    val fromUserId = eventInfo[kFromUserId] as? Int ?: 0
                    val toUserId = eventInfo[kRemoteUserId] as? Int ?: 0

                    if (connectedUserId != null && connectedUserId != fromUserId) {
                        api.reject(fromUserId, "already calling") {
                        }
                        return@runOnUiThread
                    }
                    // Only handle if the triggering user is oneself
                    if (enterModel.currentUid.toIntOrNull() == toUserId) {
                        connectedUserId = fromUserId
                        callDialog = AlertDialog.Builder(this)
                            .setTitle(getString(R.string.app_call_dialog_prompt))
                            .setMessage("${getString(R.string.app_user)} $fromUserId ${getString(R.string.app_invites_you_to_1v1_call)}")
                            .setPositiveButton(getString(R.string.app_agree)) { p0, p1 ->
                                // Check the signaling channel connection status
                                if (!checkConnectionAndNotify()) return@setPositiveButton
                                api.accept(fromUserId) { err ->
                                    if (err != null) {
                                        //  If there is an error receiving the message, initiate a rejection and return to the initial state
                                        api.reject(fromUserId, err.msg) {}
                                    }
                                }
                            }.setNegativeButton(getString(R.string.app_reject)) { p0, p1 ->
                                // Check the signaling channel connection status
                                if (!checkConnectionAndNotify()) return@setNegativeButton
                                api.reject(fromUserId, "reject by user") { err ->
                                }
                            }.create()
                        callDialog?.setCancelable(false)
                        callDialog?.show()
                    } else if (enterModel.currentUid.toIntOrNull() == fromUserId) {
                        connectedUserId = toUserId
                        callDialog = AlertDialog.Builder(this)
                            .setTitle(getString(R.string.app_call_dialog_prompt))
                            .setMessage("${getString(R.string.app_calling_to_user)} $toUserId ")
                            .setNegativeButton(getString(R.string.app_cancel)) { p0, p1 ->
                                // Check the signaling channel connection status
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
                        "${getString(R.string.app_call_did_begin)}${eventInfo.getOrDefault(CallApiImpl.kCostTimeMap, "")}",
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
                        CallStateReason.LocalHangup,
                        CallStateReason.RemoteHangup -> {
                            Toasty.normal(this, getString(R.string.app_call_did_finish), Toast.LENGTH_SHORT).show()
                        }
                        CallStateReason.LocalRejected,
                        CallStateReason.RemoteRejected -> {
                            Toasty.normal(this, getString(R.string.app_call_is_busy), Toast.LENGTH_SHORT).show()
                        }
                        CallStateReason.CallingTimeout -> {
                            Toasty.normal(this, getString(R.string.app_call_timeout), Toast.LENGTH_SHORT).show()
                        }
                        CallStateReason.RemoteCallBusy -> {
                            Toasty.normal(this, getString(R.string.app_call_timeout), Toast.LENGTH_SHORT).show()
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
    }

    override fun onCallEventChanged(event: CallEvent, eventReason: String?) {
        Log.d(TAG, "onCallEventChanged: $event, eventReason: $eventReason")
        when(event) {
            CallEvent.RemoteLeft -> {
                // The demo ends an abnormal call by listening for the remote user's departure.
                // In real business scenarios, it is recommended to use the server.
                // Listen for RTC user disconnections to kick users out.
                // The client listens for kick events to end abnormal calls
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
                    RtcConnection(enterModel.currentUid, enterModel.currentUid.toInt()) // The demo uses the local UID string as the channel name for convenience
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
            mViewBinding.tvText.text = "Call started, \nRTC channel id: $roomId, \nCaller user id: $callUserId, \nCurrent user id: $currentUserId, \nStart timestamp: $timestamp ms"
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
            mViewBinding.tvText.text = "Call ended, \nRTC channel id: $roomId, \nHanging up user id: $hangupUserId, \nCurrent user id: $currentUserId, \nEnd timestamp: $timestamp ms, \nCall duration: $duration ms"
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
        HttpManager.token007("", enterModel.currentUid) { token ->
            runOnUiThread {
                if (token != null) {
                    rtcTokenTemp = token
                    rtmTokenTemp = token
                    runnable.run()
                }
            }
        }
        // Audience updates the broadcaster's channel token
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