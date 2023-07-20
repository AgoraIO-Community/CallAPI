# CallAPI Example

*[English](README.md) | 中文*

本文档主要介绍如何快速跑通 CallAPI示例工程

## 环境准备
- 最低兼容 Android 5.0（SDK API Level 21）
- Android Studio 3.5及以上版本
- Android 5.0 及以上的手机设备

## 运行示例

- 克隆或者直接下载项目源码
- 在项目的[local.properties](local.properties) 中填入声网的AppId和Certificate
```
AG_APP_ID=""
AG_APP_CERTIFICATE=""
```
- 获取声网sdk
  下载[包含RTM 2.0的RTC SDK最新版本](https://download.agora.io/null/Agora_Native_SDK_for_Android_rel.v4.1.1.30_49294_FULL_20230512_1606_264137.zip)并将文件解压到以下目录
  [app/libs](app/libs) : agora-rtc-sdk.jar
  [app/src/main/jniLibs](app/src/main/jniLibs) : so(arm64-v8a/armeabi-v7a/x86/x86_64)

- 最后打开 Android Studio 运行项目即可开始您的体验
  
## 快速接入

- 拷贝上一步包含SDK的[app/libs](app/libs)和[app/src/main/jniLibs](app/src/main/jniLibs)到自己的工程相同目录上
- 拷贝[app/src/main/java/io.agora.onetoone/callAPI](app/src/main/java/io.agora.onetoone/callAPI)到自己的工程相同目录上
- 打开 Android Studio
- 创建CallAPI实例
  ```kotlin
    val api = CallApiImpl(this)
  ```
- 初始化(纯1v1)
  ```kotlin
    val config = CallConfig(
      BuildConfig.AG_APP_ID,
      enterModel.currentUid.toInt(),
      null,
      _createRtcEngine(),
      CallMode.Pure1v1,
      CallRole.CALLER,  //纯1v1只能设置成主叫
      mLeftCanvas,
      mRightCanvas,
      false
    )
    config.localView = mRightCanvas
    config.remoteView = mLeftCanvas

    val tokenConfig = CallTokenConfig()
    tokenConfig.roomId = enterModel.tokenRoomId
    tokenConfig.rtcToken = enterModel.rtcToken
    tokenConfig.rtmToken = enterModel.rtmToken

    api.initialize(config, tokenConfig) {
      // 需要主动调用prepareForCall
      val prepareConfig = PrepareConfig.callerConfig()
      prepareConfig.autoLoginRTM = true
      prepareConfig.autoSubscribeRTM = true
      api.prepareForCall(prepareConfig) { err ->
        completion?.invoke(err == null)
      }
    }
  ```
- 初始化(秀场转1v1模式)
  ```kotlin
    val config = CallConfig(
      BuildConfig.AG_APP_ID,
      enterModel.currentUid.toInt(),
      enterModel.showRoomId,
      _createRtcEngine(),
      CallMode.ShowTo1v1,
      role,
      mLeftCanvas,
      mRightCanvas,
      true
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
    api.initialize(config, tokenConfig) {
      if (enterModel.isBrodCaster) {
        completion?.invoke(true)
      }
      // 如果是主叫并且想加快呼叫，可以在init完成之后调用prepare
      val prepareConfig = PrepareConfig.callerConfig()
      prepareConfig.autoLoginRTM = true
      prepareConfig.autoSubscribeRTM = true
      api.prepareForCall(prepareConfig) { err ->
        completion?.invoke(err == null)
      }
    }
  ```

- 设置回调
  ```kotlin
    api.addListener(this)

    override fun onCallStateChanged(
      state: CallStateType,
      stateReason: CallReason,
      eventReason: String,
      elapsed: Long,
      eventInfo: Map<String, Any>
    ) {
    }
    
    override fun onOneForOneCache(oneForOneRoomId: String, fromUserId: Int, toUserId: Int) {
    }

    override fun onCallEventChanged(event: CallEvent, elapsed: Long) {
    }
  ```
- 变更成呼叫状态，onCallStateChanged会返回state = .calling
  ```kotlin
    override fun onCallStateChanged(
        state: CallStateType,
        stateReason: CallReason,
        eventReason: String,
        elapsed: Long,
        eventInfo: Map<String, Any>
    ) {
        val publisher = eventInfo.getOrDefault(CallApiImpl.kPublisher, enterModel.currentUid)
        // 触发状态的用户是自己才处理
        if (publisher != enterModel.currentUid) {return}

        if (CallStateType.Calling == state) {
          //如果是呼叫中
        }
    }
  ```
- 如果是秀场转1v1，默认不需要处理，如果需要处理可以吧CallConfig的autoAccept设置为false表示不可自动接受呼叫，如果非自动接受呼叫，被叫需要自行同意或者拒绝，主叫可以取消呼叫
  ```swift
    //同意,需要根据fromRoomId获取对应token
    api.accept(fromRoomId, fromUserId, rtcToken) { err ->
    }

    // 拒绝
    api.reject(fromRoomId, fromUserId, "reject by user") { err ->
    }

    //取消呼叫
    api.cancelCall { err ->
    }
  ```
- 如果同意，onCallStateChanged会先变成连接中(state = .connecting)，然后渲染出远端画面后则会变成state = .connected，表示呼叫成功
- 如果拒绝，onCallStateChanged会返回state = .prepared, event = .localRejected/.remoteRejected
- 如未同意/拒绝，onCallStateChanged会返回state = .prepared, event = .callingTimeout
- 如果呼叫需要结束，可以调用挂断，此时本地onCallStateChanged会返回state = .prepared, event = .localHangup，远端则会收到state = .prepared, event = .remoteHangup
  ```swift
    api.hangup(enterModel.showRoomId) {
    }
  ```
## 许可证

CallAPI 使用 MIT 许可证，详情见 LICENSE 文件

