package io.agora.onetoone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import es.dmoral.toasty.Toasty
import io.agora.onetoone.*
import io.agora.onetoone.databinding.ActivityLivingBinding
import io.agora.onetoone.http.HttpManager
import io.agora.onetoone.model.EnterRoomInfoModel
import io.agora.onetoone.utils.Ov1Logger
import io.agora.onetoone.utils.PermissionHelp
import io.agora.onetoone.utils.SPUtil
import io.agora.rtc2.*
import io.agora.rtc2.video.CameraCapturerConfiguration
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import io.agora.rtm.*

class LivingActivity : AppCompatActivity(),  ICallApiListener {

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

    private val kMapListSave = "tsMapListSaveKey"

    private val enterModel by lazy {
        val bundle = intent.extras
        bundle!!.getSerializable(KEY_ENTER_ROOM_MODEL) as EnterRoomInfoModel
    }

    var videoEncoderConfig: VideoEncoderConfiguration? = null
    private var connectedUserId: Int? = null

    private val TAG = "LivingActivity_LOG"

    private val mViewBinding by lazy { ActivityLivingBinding.inflate(LayoutInflater.from(this)) }

    private lateinit var rtcEngine: RtcEngine

    val api = CallApiImpl(this)

    private var mCallState = CallStateType.Idle
    private var role: CallRole = CallRole.CALLEE         //角色
    private var rtmClient: RtmClient? = null

    private val mLeftCanvas by lazy { TextureView(this) }
    private val mRightCanvas by lazy { TextureView(this) }
    private val mCenterCanvas by lazy { TextureView(this) }

    private var isAutoCall = false

    private var infoMaps = mutableListOf<Map<String, Int>>()

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

        role = if (enterModel.isBrodCaster) CallRole.CALLEE else CallRole.CALLER

        readInfoMaps()
        rtcEngine = _createRtcEngine()
        setupView()
        updateCallState(CallStateType.Idle)
        // 外部创建RTMClient
        rtmClient = _createRtmClient()
        // 外部创建需要自行管理login
        rtmClient?.login(enterModel.rtmToken, object: ResultCallback<Void?> {
            override fun onSuccess(p0: Void?) {
                _initialize(rtmClient, if (enterModel.isBrodCaster) CallRole.CALLEE else CallRole.CALLER) { success ->
                    Log.d(TAG, "_initialize: $success")
                }
            }
            override fun onFailure(p0: ErrorInfo?) {
                Log.e(TAG, "login error = ${p0.toString()}")
            }
        })
        // 内部创建rtmClient
//        _initialize(null, if (enterModel.isBrodCaster) CallRole.CALLEE else CallRole.CALLER) { success ->
//            Log.d(TAG, "_initialize: $success")
//        }
        PermissionHelp(this).checkCameraAndMicPerms(
            {
                rtcJoinChannel()
            },
            { finish() },
            true
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        closeAction()
    }

    private fun updateCallState(state: CallStateType) {
        mCallState = state
        when(mCallState) {
            CallStateType.Calling ->{
                publishMedia(false)
                setupCanvas(null)
                mViewBinding.vLeft.isVisible = false
                mViewBinding.vRight.isVisible = false
                mCenterCanvas.isVisible = false
                mViewBinding.vCenter.removeView(mCenterCanvas)
                mViewBinding.btnHangUp.isVisible = false
                mViewBinding.btnCall.isVisible = false
            }
            CallStateType.Prepared,
            CallStateType.Idle,
            CallStateType.Failed -> {
                publishMedia(true)
                setupCanvas(mCenterCanvas)
                mViewBinding.vCenter.removeAllViews()
                mViewBinding.vCenter.addView(mCenterCanvas)
                mViewBinding.vLeft.isVisible = false
                mViewBinding.vRight.isVisible = false
                mCenterCanvas.isVisible = true
                mViewBinding.btnCall.isVisible = !enterModel.isBrodCaster
                mViewBinding.btnHangUp.isVisible = false
            }
            CallStateType.Connected -> {
                mViewBinding.vLeft.isVisible = true
                mViewBinding.vRight.isVisible = true
                mCenterCanvas.isVisible = false
                mViewBinding.vCenter.removeView(mCenterCanvas)
                mViewBinding.btnHangUp.isVisible = true
                mViewBinding.btnCall.isVisible = false
            }
            else -> {}
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

    private fun _initialize(rtmClient: RtmClient?, role: CallRole, completion: ((Boolean) -> Unit)?) {
        val config = CallConfig(
            appId = BuildConfig.AG_APP_ID,
            userId = enterModel.currentUid.toInt(),
            userExtension = null,
            rtcEngine = _createRtcEngine(),
            rtmClient = rtmClient,
            mode = CallMode.ShowTo1v1,
            role = role,
            localView = mLeftCanvas,
            remoteView = mRightCanvas,
            autoAccept = true
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

        // 如果是被叫会隐式调用prepare
        api.initialize(config, tokenConfig) { error ->
            if (error != null) {
                completion?.invoke(false)
                return@initialize
            }
            if (enterModel.isBrodCaster) {
                completion?.invoke(true)
            }
            // 如果是主叫并且想加快呼叫，可以在init完成之后调用prepare
            val prepareConfig = PrepareConfig.callerConfig()
            prepareConfig.autoLoginRTM = true
            prepareConfig.autoSubscribeRTM = true
//            prepareConfig.autoJoinRTC = true
            api.prepareForCall(prepareConfig) { err ->
                completion?.invoke(err == null)
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
            videoCanvas.mirrorMode = Constants.VIDEO_MIRROR_MODE_AUTO

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
        mLeftCanvas.layoutParams = layoutParams
        mRightCanvas.layoutParams = layoutParams
        mCenterCanvas.layoutParams = layoutParams
        mViewBinding.vLeft.addView(mLeftCanvas)
        mViewBinding.vRight.addView(mRightCanvas)
        mViewBinding.vCenter.addView(mCenterCanvas)

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
            mViewBinding.btnAutoCall.visibility = View.INVISIBLE
            mViewBinding.btnShowAvgTs.visibility = View.INVISIBLE
            mViewBinding.btnReset.visibility = View.INVISIBLE
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
        mViewBinding.btnAutoCall.setOnClickListener {
            it.isSelected = !it.isSelected
            isAutoCall = it.isSelected
            if (isAutoCall) {
                mViewBinding.btnAutoCall.text = "自动呼叫：开"
                if (mCallState == CallStateType.Prepared) {
                    callAction()
                }
            } else {
                mViewBinding.btnAutoCall.text = "自动呼叫：关"
            }
        }
        mViewBinding.btnShowAvgTs.setOnClickListener {
            showAvgTs()
        }
        mViewBinding.btnReset.setOnClickListener {
            infoMaps.clear()
            saveInfoMaps()
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

    private fun showAvgTs() {
        val tsMap = mutableMapOf<String, Float>()
        val count = infoMaps.count().toFloat()
        Log.d(TAG, "showAvgTs: $infoMaps")
        infoMaps.forEach { infoMap ->
            infoMap.forEach { e ->
                val oldValue = (tsMap[e.key] ?: 0).toFloat()
                tsMap[e.key] = (oldValue + e.value) / count
            }
        }
        var toastStr = "[$count]次呼叫平均耗时\n"
        val tsArray = mutableListOf<String>()
        tsMap.forEach { e ->
            tsArray.add("${e.key}: ${e.value} ms\n")
        }
        tsArray.forEach { str ->
            toastStr += str
        }
        Toasty.normal(this, toastStr, Toast.LENGTH_SHORT).show()
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
        api.call(enterModel.showRoomId, enterModel.showUserId.toInt()) { error ->
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
            CallStateType.Connected -> {
                connectedUserId = eventInfo[CallApiImpl.kFromUserId] as? Int
                val infoMap = eventInfo[CallApiImpl.kDebugInfoMap] as? Map<String, Int>
                if (infoMap != null && infoMap.keys.count() == 6) {
                    infoMaps.add(infoMap.toMap())
                    saveInfoMaps()
                }
                Toasty.normal(this, "通话开始${eventInfo.getOrDefault(CallApiImpl.kDebugInfo, "")}", Toast.LENGTH_LONG).show()

                if (isAutoCall) {
                    Handler().postDelayed({
                        hangupAction()
                    }, 1000)
                }
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
                    CallReason.LocalHangup, CallReason.RemoteHangup -> {
                        Toasty.normal(this, "通话结束", Toast.LENGTH_SHORT).show()
                        if (isAutoCall) {
                            Handler().postDelayed({
                                callAction()
                            }, 1000)
                        }
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
            }
            CallStateType.Failed -> {
                Toasty.normal(this, eventReason, Toast.LENGTH_LONG).show()
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
                //主播用万能token自己更新主播频道token
                if (enterModel.isBrodCaster) {
                    rtcEngine.renewToken(enterModel.showRoomToken)
                }
            }
        }
        val channelName = if (enterModel.isBrodCaster) "" else tokenConfig.roomId
        HttpManager.token007(channelName, enterModel.currentUid, 1) { rtcToken ->
            runOnUiThread {
                if (rtcToken != null) {
                    tokenConfig.rtcToken = rtcToken
                    enterModel.showRoomToken = rtcToken
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

    override fun callDebugInfo(message: String) {
        Ov1Logger.d(TAG, message)
    }

    override fun callDebugWarning(message: String) {
        Ov1Logger.e(TAG, message)
    }
    private fun saveInfoMaps() {
        val jsonStr = Gson().toJson(infoMaps)
        SPUtil.putString(kMapListSave, jsonStr)
    }
    private fun readInfoMaps() {
        val content = SPUtil.getString(kMapListSave, "[]")
        val listType = object : TypeToken<List<Map<String, Int>>>() {}.type
        val list = Gson().fromJson<MutableList<Map<String, Int>>?>(content, listType)
        infoMaps = list.toMutableList()
    }
}