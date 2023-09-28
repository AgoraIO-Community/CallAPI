# CallAPI Example

*English | [中文](README_zh.md)*

This document mainly introduces how to quickly get through the CallAPI example project.

## Requirements
- Minimum Compatibility with Android 5.0（SDK API Level 21）
- Android Studio 3.5 and above versions
- Mobile devices with Android 5.0 and above

## Getting Started

- Clone or download source code
- Fill in the AppId/Certificate in the [gradle.properties](gradle.properties) of the project
```
AG_APP_ID=
AG_APP_CERTIFICATE=
```
- Run the project with Android Studio to begin your experience
  
## Integration

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
      CallRole.CALLER,  // Pure 1v1 can only be set as the caller
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
        null,
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
  
    override fun onCallEventChanged(event: CallEvent, elapsed: Long) {
    }
  ```
- Call
  - If it is the caller, call the call method to call the remote user
    ```kotlin
      callApi.call(remoteRoomId, remoteUserId) { err ->
      }
    ```
  - If it is the callee, Change to call state, onCallStateChanged will return state == calling.
  ```kotlin
  override fun onCallStateChanged(
        state: CallStateType,
        stateReason: CallReason,
        eventReason: String,
        elapsed: Long,
        eventInfo: Map<String, Any>
    ) {
        val publisher = eventInfo.getOrDefault(CallApiImpl.kPublisher, enterModel.currentUid)
        // handle trigger state when publisher is not local user
        if (publisher != enterModel.currentUid) {return}

        if (state == CallStateType.Calling) {
            // If it is a call in progress
        }
    }
  ``` 
  
- If it is a show to 1v1 mode, it does not need to be processed by default. If it needs to be processed, you can set the autoAccept in CallConfig to false to indicate that the call cannot be automatically accepted. If the call is not automatically accepted, the callee needs to agree or reject it on their own, and the caller can cancel the call.
  ```kotlin
    // accept, need fromRoomId to fetch tokens
    api.accept(fromRoomId, fromUserId, rtcToken) { err ->
    }

    // reject
    api.reject(fromUserId, "reject by user") { err ->
    }

    // cancel call
    api.cancelCall { err ->
    }
  ```
- If agreed, onCallStateChanged will first become connected(state=CallStateType.Connecting), and then after rendering the remote screen, it will become CallStateType.Connected, indicating that the call was successful.
- If rejected, onCallStateChanged will return state=CallStateType.Prepared, event=CallReason.LocalRejected/RemoteRejected
- If not agreed/rejected, onCallStateChanged will return state=CallStateType.Prepared, event=CallReason.CallingTimeout.
- If the call needs to end, you can call hang up. At this time, the local onCallStateChanged will return state=CallStateType.Prepared, event=CallReason.LocalHangup, and the remote will receive CallStateType.Prepared, event=CallReason.RemoteHangup.
  ```kotlin
    api.hangup(enterModel.showUserId.toInt()) {
    }
  ```
## 许可证

Call API uses MIT License, see LICENSE file for details.
