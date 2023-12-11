# CallAPI Example

*[English](README.md) | 中文*

本文档主要介绍如何快速跑通 CallAPI示例工程

## 环境准备
- 最低兼容 Android 5.0（SDK API Level 21）
- Android Studio 3.5及以上版本
- Android 5.0 及以上的手机设备

## 运行示例

- 克隆或者直接下载项目源码  
- 打开Android Studio，使用它来打开项目的[Android](../Android)目录。这样，IDE会自动开始构建项目
- 等待构建完成

- 获取声网App ID -------- [声网Agora - 文档中心 - 如何获取 App ID](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-id)
  
  > - 点击创建应用
  >   
  >   ![](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/create_app_1.jpg)
  > 
  > - 选择你要创建的应用类型
  >   
  >   ![](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/create_app_2.jpg)
  > 

- 获取App 证书 ----- [声网Agora - 文档中心 - 获取 App 证书](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-%E8%AF%81%E4%B9%A6)
  
  > 在声网控制台的项目管理页面，找到你的项目，点击配置。
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/1641871111769.png)
  > 点击主要证书下面的复制图标，即可获取项目的 App 证书。
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/1637637672988.png)

    - 开启RTM
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config1.jpg)
  > 
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config2.jpg)
  > 
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config3.jpg)
- 在项目根目录找到[gradle.properties](gradle.properties)，填入声网的AppId和Certificate
```
AG_APP_ID=
AG_APP_CERTIFICATE=
```
- 在Android Studio顶部的工具栏中，单击“文件”->选择“与Gradle文件同步项目”，等待Gradle同步完成，即可运行项目并进行调试
  
## 快速接入
- 拷贝[lib_callapi/src/main/java/io/agora/onetoone](lib_callapi/src/main/java/io/agora/onetoone)到自己的工程中
- 打开 Android Studio
- 创建CallAPI实例
  ```kotlin
    val api = CallApiImpl(this)
  ```
- 初始化(纯1v1)
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
    api.initialize(config, tokenConfig) { error ->
    }
  ```
- 初始化(秀场转1v1模式)
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
  >注意⚠️：如果通过外部传入rtmClient，则需要外部维持登陆状态

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
    
    override fun onCallEventChanged(event: CallEvent, elapsed: Long) {
    }
  
  ```
- 呼叫
  - 如果是主叫，调用call方法呼叫远端用户  
    ```kotlin
      callApi.call(remoteRoomId, remoteUserId) { err ->
      }
    ```
    
  - 此时不管主叫还是被叫都会收到onCallStateChanged会返回state = .calling，变更成呼叫状态
    > 注意⚠️: 收到calling时需要把外部开启的音视频推流关闭，否则呼叫会失败  

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

- 如果是秀场转1v1，默认不需要处理，如果需要处理可以吧CallConfig的autoAccept设置为false表示不自动接受呼叫，如果非自动接受呼叫，被叫需要自行同意或者拒绝，主叫可以取消呼叫
  ```kotlin
    //同意,需要根据fromRoomId获取对应token
    api.accept(fromRoomId, fromUserId, rtcToken) { err ->
    }

    // 拒绝
    api.reject(fromUserId, "reject by user") { err ->
    }

    //取消呼叫
    api.cancelCall { err ->
    }
  ```
- 如果同意，onCallStateChanged会先变成连接中(state = CallStateType.Connecting)，然后渲染出远端画面后则会变成state = CallStateType.Connected，表示呼叫成功
- 如果拒绝，onCallStateChanged会返回state = CallStateType.Prepared, event = CallReason.LocalRejected/RemoteRejected
- 如未同意/拒绝，onCallStateChanged会返回state = CallStateType.Prepared, event = CallReason.CallingTimeout
- 如果呼叫需要结束，可以调用挂断，此时本地onCallStateChanged会返回state = CallStateType.Prepared, event = CallReason.LocalHangup，远端则会收到state = CallStateType.Prepared, event = CallReason.RemoteHangup
  ```kotlin
    api.hangup(enterModel.showUserId.toInt()) {
    }
  ```

## 调用时序图
### 纯1v1
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/sequence_pure1v1.zh.png)

### 秀场转1v1
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/sequence_pure1v1.zh.png)
## 许可证

CallAPI 使用 MIT 许可证，详情见 LICENSE 文件

