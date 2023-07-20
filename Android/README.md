# CallAPI Example

*English | [中文](README_zh.md)*

This document mainly introduces how to quickly get through the CallAPI example project.

## Requirements
- Minimum Compatibility with Android 5.0（SDK API Level 21）
- Android Studio 3.5 and above versions
- Mobile devices with Android 5.0 and above

## Getting Started

- Clone or download source code
- Fill in the AppId/Certificate in the [local.properties](local.properties) of the project
```
AG_APP_ID=""
AG_APP_CERTIFICATE=""
```
- Obtain Agora SDK
  Download [the rtc sdk with rtm 2.0](https://download.agora.io/null/Agora_Native_SDK_for_Android_rel.v4.1.1.30_49294_FULL_20230512_1606_264137.zip) and then unzip it to the directions belows:
  [AUIKit/Android/auikit/libs](app/libs) : agora-rtc-sdk.jar
  [AUIKit/Android/auikit/src/main/jniLibs](app/src/main/jniLibs) : so(arm64-v8a/armeabi-v7a/x86/x86_64)

- Run the project with Android Studio to begin your experience
  
## Integration

- Copy the [app/libs](app/libs) and [app/src/main/jniLibs](app/src/main/jniLibs) containing the SDK from the previous step to the same directory as your own project
- Copy [app/src/main/java/io.agora.onetoone/callAPI](app/src/main/java/io.agora.onetoone/callAPI) to the same directory as your own project
- Open Android Studio
- Create CallAPI instance
  ```kotlin
    val api = CallApiImpl(this)
  ```
- Initialization(pure 1v1)
  ```kotlin
    val config = CallConfig(
      BuildConfig.AG_APP_ID,
      enterModel.currentUid.toInt(),
      null,
      _createRtcEngine(),
      CallMode.Pure1v1,
      CallRole.CALLER,  // Pure 1v1 can only be set as the callier
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
      // Requires active call to prepareForCall
      val prepareConfig = PrepareConfig.callerConfig()
      prepareConfig.autoLoginRTM = true
      prepareConfig.autoSubscribeRTM = true
      api.prepareForCall(prepareConfig) { err ->
        completion?.invoke(err == null)
      }
    }
  ```
- Initialize(Show to 1v1 mode)
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
    // If it is called, it will implicitly call prepare
    api.initialize(config, tokenConfig) {
      if (enterModel.isBrodCaster) {
        completion?.invoke(true)
      }
      // If the caller wants to speed up the call, you can call prepare after init is complete
      val prepareConfig = PrepareConfig.callerConfig()
      prepareConfig.autoLoginRTM = true
      prepareConfig.autoSubscribeRTM = true
      api.prepareForCall(prepareConfig) { err ->
        completion?.invoke(err == null)
      }
    }
  ```

- Set callback
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
- Change to call state, onCallStateChanged will return state=. calling.
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
- If it is a show to 1v1 mode, it does not need to be processed by default. If it needs to be processed, you can set the autoAccept in CallConfig to false to indicate that the call cannot be automatically accepted. If the call is not automatically accepted, the callee needs to agree or reject it on their own, and the caller can cancel the call.
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
- If agreed, onCallStateChanged will first become connected(state=.connecting), and then after rendering the remote screen, it will become state=.connected, indicating that the call was successful.
- If rejected, onCallStateChanged will return state=.prepared, event=.localRejected/.remoteRejected.
- If not agreed/rejected, onCallStateChanged will return state=.prepared, event=.callingTimeout.
- If the call needs to end, you can call hang up. At this time, the local onCallStateChanged will return state=. prepared, event=. localHangup, and the remote will receive state=. prepared, event=. remoteHangup.
  ```swift
    api.hangup(enterModel.showRoomId) {
    }
  ```
## 许可证

Call API uses MIT License, see LICENSE file for details.