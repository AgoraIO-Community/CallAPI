package io.agora.onetoone

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.view.*
import io.agora.onetoone.databinding.ActivityMainBinding
import io.agora.onetoone.http.HttpManager
import io.agora.onetoone.model.EnterRoomInfoModel
import io.agora.onetoone.utils.SPUtil

class MainActivity : AppCompatActivity() {

    private val TAG = "LOG_MainActivity"

    private val kIsShowMode = "isShowMode"
    private val kIsBrodCaster = "isBrodCaster"
    private val kLocalUid = "localUid"
    private val kOwnerUid = "ownerUid"

    private val kDimensionsWidth = "dimensionsWidth"
    private val kDimensionsHeight = "dimensionsHeight"
    private val kFrameRate = "frameRate"
    private val kBitrate = "bitrate"

    private val mViewBinding by lazy { ActivityMainBinding.inflate(LayoutInflater.from(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(mViewBinding.root)
        setupView()
        setupSaved()
        updateUI()
    }

    private var mLastClickTime = 0L
    private val mClickInterval: Long = 4000
    private fun setupView() {
        ViewCompat.setOnApplyWindowInsetsListener(mViewBinding.root) { _, insets ->
            val systemInset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            Log.d(
                TAG,
                "systemInset l:${systemInset.left},t:${systemInset.top},r:${systemInset.right},b:${systemInset.bottom}"
            )
            mViewBinding.root.setPaddingRelative(
                systemInset.left + mViewBinding.root.paddingLeft,
                systemInset.top,
                systemInset.right + mViewBinding.root.paddingRight,
                0
            )
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // view and actions
        mViewBinding.rgMode.setOnCheckedChangeListener { _, i ->
            updateUI()
        }
        mViewBinding.rgRole.setOnCheckedChangeListener { _, i ->
            updateUI()
        }
        mViewBinding.btnEnter.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - mLastClickTime > mClickInterval) {
                mLastClickTime = currentTime
                onEnterAction()
            }
        }
    }

    private fun onEnterAction() {
        val isShowMode = (mViewBinding.btnShow.isChecked)
        val isBrodCaster = (mViewBinding.btnBroadcaster.isChecked)
        val currentUserId = mViewBinding.etLocalUid.text.toString()
        val remoteUserId = mViewBinding.etOwnerUid.text.toString()

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "用户id不能为空", Toast.LENGTH_LONG).show()
            return
        }
        if (isBrodCaster) {
            if (currentUserId.toIntOrNull() == null) {
                Toast.makeText(this, "本地用户的id需要是纯数字", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            if (currentUserId.toIntOrNull() == null || remoteUserId.toIntOrNull() == null) {
                Toast.makeText(this, "本地和远端用户的id需要是纯数字", Toast.LENGTH_LONG).show()
                return
            }
        }
        // save
        SPUtil.putBoolean(kIsShowMode, isShowMode)
        SPUtil.putBoolean(kIsBrodCaster, isBrodCaster)
        SPUtil.putString(kLocalUid, currentUserId)
        SPUtil.putString(kOwnerUid, remoteUserId)
        SPUtil.putString(kDimensionsWidth, mViewBinding.etResolutionWidth.text.toString())
        SPUtil.putString(kDimensionsHeight, mViewBinding.etResolutionHeight.text.toString())
        SPUtil.putString(kFrameRate, mViewBinding.etFps.text.toString())

        val showRoomId = if (isBrodCaster) currentUserId else remoteUserId
        val showUserId = if (isBrodCaster) currentUserId else remoteUserId

        val enterModel = EnterRoomInfoModel(
            isBrodCaster,
            currentUserId,
            showRoomId,
            showUserId,
        )
        enterModel.dimensionsWidth = mViewBinding.etResolutionWidth.text.toString()
        enterModel.dimensionsHeight = mViewBinding.etResolutionHeight.text.toString()
        enterModel.frameRate = mViewBinding.etFps.text.toString()

        val runnable = Runnable {
            if (enterModel.rtcToken.isNotEmpty() && enterModel.rtmToken.isNotEmpty() && enterModel.showRoomToken.isNotEmpty()) {
                if (isShowMode) {
                    LivingActivity.launch(this, enterModel)
                } else {
                    Pure1v1LivingActivity.launch(this, enterModel)
                }
            }
        }
        enterModel.tokenRoomId = currentUserId
        val channelName = if (isBrodCaster) "" else enterModel.tokenRoomId
        HttpManager.token007(channelName, currentUserId, 1) { rtcToken ->
            runOnUiThread {
                if (rtcToken != null) {
                    enterModel.rtcToken = rtcToken
                    runnable.run()
                } else {
                    Toast.makeText(this, "get RTC token failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        HttpManager.token007(channelName, currentUserId, 2) { rtmToken ->
            runOnUiThread {
                if (rtmToken != null) {
                    enterModel.rtmToken = rtmToken
                    runnable.run()
                } else {
                    Toast.makeText(this, "get RTM token failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        HttpManager.token007(showRoomId, currentUserId, 1) { rtcToken ->
            runOnUiThread {
                if (rtcToken != null) {
                    enterModel.showRoomToken = rtcToken
                    runnable.run()
                } else {
                    Toast.makeText(this, "get show room token failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSaved() {
        val isShowMode = SPUtil.getBoolean(kIsShowMode, true)
        val isBrodCaster = SPUtil.getBoolean(kIsBrodCaster, true)
        val localUid = SPUtil.getString(kLocalUid, "")
        val ownerUid = SPUtil.getString(kOwnerUid, "")
        mViewBinding.rgMode.check(if (isShowMode) R.id.btnShow else R.id.btnOneToOne)
        mViewBinding.rgRole.check(if (isBrodCaster) R.id.btnBroadcaster else R.id.btnAudience)
        mViewBinding.etLocalUid.setText(localUid)
        mViewBinding.etOwnerUid.setText(ownerUid)

        mViewBinding.etResolutionWidth.setText(SPUtil.getString(kDimensionsWidth, "640"))
        mViewBinding.etResolutionHeight.setText(SPUtil.getString(kDimensionsHeight, "360"))
        mViewBinding.etFps.setText(SPUtil.getString(kFrameRate, "15"))
    }

    private fun updateUI() {
        val modeChecked = mViewBinding.rgMode.checkedRadioButtonId
        val roleChecked = mViewBinding.rgRole.checkedRadioButtonId
        if (modeChecked == R.id.btnShow) {
            mViewBinding.rgRole.isVisible = true
            if (roleChecked == R.id.btnBroadcaster) {
                mViewBinding.tvOwnerUid.isVisible = false
                mViewBinding.etOwnerUid.isVisible = false
                mViewBinding.btnEnter.text = "创建秀场转1v1"
            } else {
                mViewBinding.tvOwnerUid.isVisible = true
                mViewBinding.etOwnerUid.isVisible = true
                mViewBinding.btnEnter.text = "加入秀场转1v1"
            }
        } else {
            mViewBinding.rgRole.isVisible = false
            mViewBinding.tvOwnerUid.isVisible = false
            mViewBinding.etOwnerUid.isVisible = false
            mViewBinding.btnEnter.text = "进入纯1v1"
        }
    }

    override fun onResume() {
        super.onResume()
    }

}