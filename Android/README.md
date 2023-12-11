# CallAPI Example

*English | [中文](README_zh.md)*

This document mainly introduces how to quickly get through the CallAPI example project.

## Requirements
- Minimum Compatibility with Android 5.0（SDK API Level 21）
- Android Studio 3.5 and above versions
- Mobile devices with Android 5.0 and above

## Getting Started

- Clone the project source code or download it directly.
- Open Android Studio and use it to open the [Android](../Android) directory of the project. This will trigger the IDE to start building the project automatically.
- Wait for the build to complete.
  
- Obtain App ID -------- [声网Agora - 文档中心 - 如何获取 App ID](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-id)
  
  > - Click to create an application
  >   
  >   ![](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/create_app_1.jpg)
  > 
  > - Select the type of application you want to create
  >   
  >   ![](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/create_app_2.jpg)
  > 

- Obtain App Certificate ----- [声网Agora - 文档中心 - 获取 App 证书](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-%E8%AF%81%E4%B9%A6)
  > On the project management page of the Agora console, locate your project and click on Configure.
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/1641871111769.png)
  > Click on the copy icon below the main certificate to obtain the App certificate for the project.
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/1637637672988.png)

  - How to enable RTM
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config1.jpg)
  > 
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config2.jpg)
  > 
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config3.jpg)
  > 
- In the project's root directory, locate the gradle.properties file and fill in the AppId and Certificate for Agora as follows:
```
AG_APP_ID=
AG_APP_CERTIFICATE=
```
- In the top toolbar of Android Studio, click "File" -> select "Sync Project with Gradle Files", wait for Gradle sync to complete, then you can run and debug the project.
  
## Integration

- Copy [lib_callapi/src/main/java/io/agora/onetoone](lib_callapi/src/main/java/io/agora/onetoone) to your own project
- Open Android Studio
- Create CallAPI instance
  ```kotlin
    val api = CallApiImpl(this)
  ```
- Initialization(pure 1v1)
  ```kotlin
    val config = CallConfig(
      appId = BuildConfig.AG_APP_ID,
      userId = enterModel.currentUid.toInt(),
      userExtension = null,
      rtcEngine = _createRtcEngine(),
      rtmClient = _createRtmClient(), //如果已经使用了rtm，可以传入rtm实例，否则可以设置为nil
      mode = CallMode.Pure1v1,
      role = CallRole.CALLER,
      localView = mLeftCanvas,
      remoteView = mRightCanvas,
      autoAccept = false
    )
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
      appId = BuildConfig.AG_APP_ID,
      userId = enterModel.currentUid.toInt(),
      userExtension = null,
      rtcEngine = _createRtcEngine(),
      rtmClient = _createRtmClient(), //如果已经使用了rtm，可以传入rtm实例，否则可以设置为nil
      mode = CallMode.ShowTo1v1,
      role = CallRole.CALLER,
      localView = mLeftCanvas,
      remoteView = mRightCanvas,
      autoAccept = true
    )
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
  >take care ⚠️： If the rtmClient is passed in externally, it is necessary to maintain the login status externally
  
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
    
  - At this point, both the caller and the called will receive onCallStateChanged and return state = . calling, changing to the calling state
    > take care ⚠️: When receiving a call, it is necessary to turn off the external enabled audio and video streaming, otherwise the call will fail

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
## Call timing diagram
### Pure 1v1
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/sequence_pure1v1.en.png)

### Live to 1v1
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/sequence_pure1v1.en.png)

## 许可证

Call API uses MIT License, see LICENSE file for details.
