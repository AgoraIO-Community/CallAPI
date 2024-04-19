
# CallAPI Example

本文档主要介绍如何快速跑通 CallAPI示例工程

- [CallAPI Example](#callapi-example)
  - [1. 环境准备](#1-环境准备)
  - [2. 运行示例](#2-运行示例)
  - [3. 项目介绍](#3-项目介绍)
    - [3.1 概述](#31-概述)
    - [3.2 角色介绍](#32-角色介绍)
    - [3.3 核心功能](#33-核心功能)
    - [3.4 玩法说明](#34-玩法说明)
  - [4. 快速集成](#4-快速集成)
    - [4.1 把CallApi集成进自己的项目里](#41-把callapi集成进自己的项目里)
    - [4.2 结构图](#42-结构图)
    - [4.3 使用CallApi实现一个通话流程](#43-使用callapi实现一个通话流程)
      - [4.3.1 初始化。](#431-初始化)
      - [4.3.2 设置回调](#432-设置回调)
      - [4.3.3 RtmManager登录](#433-rtmmanager登录)
      - [4.3.4 准备通话环境。](#434-准备通话环境)
      - [4.3.5 （主叫）发起呼叫](#435-主叫发起呼叫)
        - [4.3.5.2 取消呼叫](#4352-取消呼叫)
      - [4.3.6 （被叫）收到呼叫时的处理](#436-被叫收到呼叫时的处理)
        - [4.3.6.1 同意呼叫](#4361-同意呼叫)
        - [4.3.6.2 拒绝呼叫](#4362-拒绝呼叫)
        - [4.3.6.3 未作出回应（同意或拒绝）](#4363-未作出回应同意或拒绝)
      - [4.3.7 （主叫或被叫）通话中时](#437-主叫或被叫通话中时)
        - [4.3.7.1 结束呼叫，可以调用挂断函数](#4371-结束呼叫可以调用挂断函数)
      - [4.3.8 释放通话缓存](#438-释放通话缓存)
  - [5. 进阶集成](#5-进阶集成)
    - [5.1 使用已经初始化过的RTM实例(rtmClient)。](#51-使用已经初始化过的rtm实例rtmclient)
    - [5.2 切换被叫的推流收流时机以节省费用。](#52-切换被叫的推流收流时机以节省费用)
    - [5.3 需要手动开启和关闭音视频流的采集](#53-需要手动开启和关闭音视频流的采集)
    - [5.4 消息中携带自定义数据结构](#54-消息中携带自定义数据结构)
    - [5.5 通话异常定位。](#55-通话异常定位)
    - [5.6 自定义信令消息](#56-自定义信令消息)
    - [5.7 需要监听通话频道的Rtc回调](#57-需要监听通话频道的rtc回调)
  - [6. API说明](#6-api说明)
    - [CallApiListenerProtocol](#callapilistenerprotocol)
    - [CallApiProtocol](#callapiprotocol)
    - [ISignalClient(信令抽象协议)](#isignalclient信令抽象协议)
    - [ISignalClientListener(信令回调协议)](#isignalclientlistener信令回调协议)
  - [7. 实现原理](#7-实现原理)
    - [7.1 优化呼叫性能和可靠性](#71-优化呼叫性能和可靠性)
      - [7.1.1 加快出图速度](#711-加快出图速度)
      - [7.1.2 提升消息送达率](#712-提升消息送达率)
    - [7.2 影响通话速度的指标](#72-影响通话速度的指标)

## 1. 环境准备
- Xcode 14.0及以上版本
- 最低支持系统：iOS 13.0
- 请确保您的项目已设置有效的开发者签名

## 2. 运行示例

- 克隆或者直接下载项目源码
- 申请账号和权限
  > **注意，由于Demo里包含了基于 `Rtm` 和 `环信` 的两种1v1信令呼叫场景，如果您只需要体验其中的一种呼叫场景，可以跳过另一种的申请流程**
  - 进入声网控制台获取 APP ID 和 APP 证书 [控制台入口](https://console.shengwang.cn/overview)

  - 点击创建项目

    ![图片](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/ent-full/sdhy_1.jpg)

  - 选择项目基础配置, 鉴权机制需要选择**安全模式**

    ![图片](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/ent-full/sdhy_2.jpg)

  - 在项目的功能配置中启用"实时消息 RTM"功能
     ```json
     注: 如果没有启动"实时消息 RTM"功能, 将无法体验项目完整功能
     ```

    ![图片](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/ent-full/sdhy_7.jpg)

  - 拿到项目 APP ID 与 APP 证书

    ![图片](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/ent-full/sdhy_3.jpg)
  - [注册环信获取AppKey](https://doc.easemob.com/product/enable_and_configure_IM.html#%E5%88%9B%E5%BB%BA%E5%BA%94%E7%94%A8)
    - 环信相关功能为Demo层的扩展功能，仅提供简单的实现消息收发，一些边缘case异常、版本升级等需要自行维护。
    - CallApi Demo里的自定义环信信令管理使用的是 username + password 方式，如果需要使用更安全的token方式鉴权，需要自行实现

- <a id="custom-report">开通声网自定义数据上报和分析服务</a>
  > 该服务当前处于免费内测期，如需试用该服务，请联系 sales@agora.io

- 在项目的[KeyCenter.swift](Example/CallAPI/KeyCenter.swift) 中填写声网 APP ID 和 APP 证书 以及环信 App Key (如果不需要体验环信自定义信令流程，IMAppKey可以设置为`""`)
  ```swift
  static var AppId: String = <#Your AppId#>
  static var Certificate: String = <#Your Certificate#>
  static var IMAppKey: String = <#Your EaseMob AppKey#>
  ```


- 打开终端，进入到[Podfile](Example/Podfile)目录下，执行`pod install`命令

- 最后打开[CallAPI.xcworkspace](Example/CallAPI.xcworkspace)，运行即可开始您的体验

## 3. 项目介绍
### 3.1 概述
  - CallApi是什么
    - CallAPI是声网面向一对一秒开设计的场景化API解决方案，可以让开发者在直播场景下，获得极速秒开、丝滑切换体验。
  - CallApi功能特点
    - **CallApi是一套开源的纯业务逻辑的呼叫邀请模块，您可以自由定制和修改，而不会限制您的业务流程。**
    - **CallApi不涉及任何UI，您可以根据自己的需求灵活地自定义UI。**

### 3.2 角色介绍
  - 主叫
    > 是发起呼叫并邀请对方进行通话的一方。主叫方主动发起呼叫请求，建立起视频通话连接，并发送邀请给被叫方。
  - 被叫
    > 是接收呼叫请求并被邀请进行通话的一方。被叫方在收到主叫方的呼叫邀请后可以接受或拒绝呼叫，如果接受呼叫，则与主叫方建立起视频通话连接。

### 3.3 核心功能
  - **呼叫**：主叫发起呼叫。
  - **取消呼叫**：主叫发起呼叫后可以在通话成功前发起取消呼叫来中断当前的呼叫。
  - **接受呼叫**：被叫在接收到主叫的呼叫请求后可以接受当次呼叫。
  - **拒绝呼叫**：被叫在接收到主叫的呼叫请求后可以拒绝当次呼叫。
  - **挂断**：主叫/被叫在通话中时可以发起挂断请求来中断本次通话。
  
### 3.4 玩法说明
  - 1v1场景
    > 通常在陌生人社交场景，用户可以根据照片和个人简介筛选到目标感兴趣其他用户，或者通过地理位置、标签随机匹配的方式，2位用户进行1v1私密视频通话的场景玩法。通话中，默认1v1双方用户均开启摄像头和麦克风，双向发送接收音视频流。
  - 秀场转1v1场景
    > 主播在直播过程中，用户可以付费发起1v1视频通话。在通话接通后，主播的原直播间不关闭但不推流，主播转场到1v1与付费用户进行视频通话；当1v1视频通话结束后，主播转场回原直播间继续直播的场景玩法。
  
## 4. 快速集成
### 4.1 把CallApi集成进自己的项目里
  - 把示例代码的[iOS](/iOS/)目录拷贝至自己的工程里，例如与Podfile文件同级
  - 在Podfile文件里加入
    ```
    pod 'CallAPI', :path => './iOS'
    ```
  - 如果在您的项目中已经使用了声网的Rtc或Rtm SDK
    - 请确保SDK不低于下述版本:
      - AgoraRtm_iOS: 2.1.10
      - AgoraRtcEngine_Special_iOS: 4.1.1.26
    - 如果您依赖的版本与上述依赖不同，请修改[CallAPI.podspec](/iOS/CallAPI.podspec)文件对应SDK的版本
      ```
      s.dependency 'AgoraRtcEngine_Special_iOS', '4.1.1.26'
      s.dependency 'AgoraRtm_iOS', '2.1.10'
      ```
  - 打开终端，执行`pod install`命令，CallAPI代码即可集成进项目里。
### 4.2 结构图
<img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/200/class_structure_v2.0.0_2.png" width="500px"><br>

- RtmClient: 声网实时消息 (Rtm) 实例。
- CallRtmManager: RtmClient 的管理类，负责处理Rtm的登录、注销、Token续期、网络状态变化通知等操作，**请注意，该类提供于业务层的Rtm包装类，用于管理 Rtm 的生命周期，不包含在CallApi，您可以根据自己的需要随意修改**。
- CallRtmSignalClient: 基于 Rtm 实现的信令管理类，用于发送经 CallApi 编码后的消息。该对象接收到消息后将同步到 CallApi 进行解码。**请注意，CallRtmSignalClient仅仅负责Rtm消息收发，不负责Rtm的状态管理，如果需要使用或者定制更多的状态管理，请使用CallRtmManager**。
- CallApiImpl:  用于管理声网RTC和信令等功能的1v1场景API实例。
  
### 4.3 使用CallApi实现一个通话流程
#### 4.3.1 初始化。
  - 初始化RtmManager
    ```swift
    let rtmManager = CallRtmManager(appId: KeyCenter.AppId,
                                    userId: "\(currentUid)",
                                    rtmClient: rtmClient)  //如果有则传，否则为nil
    ```
  - 初始化信令
    ```swift
    let signalClient = CallRtmSignalClient(rtmClient: rtmManager.getRtmClient())
    ```
  - 初始化CallApi。
    ```swift
    let api = CallApiImpl()
    
    let config = CallConfig()
    config.appId = KeyCenter.AppId
    config.userId = currentUid
    config.rtcEngine = rtcEngine   
    config.signalClient = signalClient 
    api.initialize(config: config) 
    ```
#### 4.3.2 设置回调
  - 设置RtmManager的回调
    - 设置监听
      ```swift
      rtmManager.delegate = self
      ```
    - 实现回调ICallRtmManagerListener
      ```swift      
      func onConnected() {
          NSLog("onConnected")
          AUIToast.show(text: "rtm已连接")
          //表示连接成功，可以调用callapi进行通话呼叫了
      }
      
      func onDisconnected() {
          NSLog("onDisconnected")
          AUIToast.show(text: "rtm未连接")
          //表示连接没有成功，此时调用callapi会失败
      }
      
      func onTokenPrivilegeWillExpire(channelName: String) {
          //Rtm token过期，需要重新renew token
          tokenPrivilegeWillExpire()
      }
      ```
  - 设置CallApi的回调。
    - 设置监听。
      ```swift
        api.addListener(listener: self)
      ```
    - 实现CallApiListenerProtocol对应的协议。
      ```swift
      public func onCallStateChanged(with state: CallStateType,
                                     stateReason: CallReason,
                                     eventReason: String,
                                     eventInfo: [String : Any]) {
          ...
      }

      @objc func onCallEventChanged(with event: CallEvent, eventReason: String?) {
          ...
      }

      ...
      ```
#### 4.3.3 RtmManager登录
  ```swift 
  rtmManager?.login(rtmToken: rtmToken, completion: { err in
      if let _ = err { return }
      //登录成功，可以开始准备通话环境
  })
  ```
#### 4.3.4 准备通话环境。
    ```swift   
    let prepareConfig = PrepareConfig()
    prepareConfig.rtcToken = ...   // 设置rtc token(万能token)
    prepareConfig.roomId = "\(currentUid)"
    prepareConfig.localView = rightView
    prepareConfig.remoteView = leftView
    prepareConfig.autoJoinRTC = false  // 如果期望立即加入自己的RTC呼叫频道，则需要设置为true
    api.prepareForCall(prepareConfig: prepareConfig) { err in
        if let _ = err { return }
        // 成功即可以开始进行呼叫
    }
    ```
    > **注意1：rtcToken必须使用`万能token`，否则接听呼叫会看不到远端音视频**
    

    > **注意2：主叫发起呼叫后，被叫会获取到主叫在准备通话环境中设置的 roomId，从而加入对应的 RTC 频道进行通话。通话结束后，如果主叫不更新 roomId 就发起新的呼叫，可能会有一定的安全风险。<br>
    以用户 A、B、C 为例，主叫 A 向用户 B 发起呼叫后，B 就获取了 A 的 RTC 频道名（roomId）。呼叫结束后，如果 A 不更新 roomId 就向用户 C 发起呼叫，用户 B 可以通过技术手段使用之前的 roomId 和通配 Token 加入用户 A 的频道进行盗流。<br>
    因此为确保通话安全，我们建议在每次发起呼叫前，都调用 prepareForCall 方法更新 roomId，以保证每次通话使用不同的 RTC 频道，进而确保通话的私密性。**
#### 4.3.5 （主叫）发起呼叫
  - 发起视频呼叫可以使用两个 `call` 方法来发起视频呼叫
    - 调用默认呼叫方法，该方法默认为视频模式呼叫
      ```swift
      api.call(remoteUserId: remoteUserId) { err in
      }
      ```
    - 调用呼叫类型为video的呼叫
      ```swift
      api.call(remoteUserId: remoteUserId,
                             callType: .video,
                             callExtension: [:]) { error in

      }
      ```
  - 调用呼叫类型为audio的 `call` 方法来发起音频呼叫
    ```swift
    api.call(remoteUserId: remoteUserId,
                           callType: .audio,
                           callExtension: [:]) { error in

    }
    ```
  - 发起呼叫后，主叫和被叫都会收到onCallStateChanged会返回`(state: .calling)`，变更成呼叫状态，根据主叫或者被叫，stateReason值分别会是 `localVideoCall(本地发起视频呼叫)`、`localAudioCall(本地发起音频呼叫)`、`remoteVideoCall(远端发起视频呼叫)`、`remoteAudioCall(远端发起音频呼叫)`。
    > **`注意: 由于声网RTC只支持同时发布一路视频流，因此收到"calling"状态时需要把外部开启的音视频推流关闭，否则呼叫会出现异常`**
      ```swift
      public func onCallStateChanged(with state: CallStateType,
                                     stateReason: CallReason,
                                     eventReason: String,
                                     eventInfo: [String : Any]) {
          if state == .calling {
              // 如果是呼叫中
              ...

              // 主叫用户的uid
              let fromUserId = eventInfo[kFromUserId] as? UInt ?? 0
              // 目标用户的uid，为当前用户
              let toUserId = eventInfo[kRemoteUserId] as? UInt ?? 0
          }
      }
      ```

##### 4.3.5.2 取消呼叫
  - 通过 `cancelCall` 方法来取消当前发起的呼叫，
    ```swift
    api.cancelCall { err in 
    }
    ```
  
#### 4.3.6 （被叫）收到呼叫时的处理
##### 4.3.6.1 同意呼叫
  - onCallStateChanged 会返回`连接中状态(state: .connecting)`，然后在远端画面渲染完成后，状态将变为`已连接(state: .connected)`，表示呼叫成功。这个状态变化过程反映了呼叫的建立和视频画面的渲染。
    ```swift
    api.accept(remoteUserId: fromUserId) { err in
    }
    ```
##### 4.3.6.2 拒绝呼叫
  - onCallStateChanged 会返回`(state: .prepared, stateReason: .localRejected)`(被叫)或`(state: .prepared, stateReason: .remoteRejected)`(主叫)。
    ```swift
      api.reject(remoteUserId: fromUserId, reason: "reject by user") { err in
      }
    ```
##### 4.3.6.3 未作出回应（同意或拒绝）
  - onCallStateChanged 会返回`(state: .prepared, stateReason: .callingTimeout)`。表示呼叫超时，未能成功建立连接。
#### 4.3.7 （主叫或被叫）通话中时
##### 4.3.7.1 结束呼叫，可以调用挂断函数
  - onCallStateChanged 会返回 `(state: .prepared, stateReason: .localHangup)`(本地用户)或`(state: .prepared, stateReason: .remoteHangup)`(远端用户)。这表示呼叫已经被挂断，连接已经断开。
    ```swift
    api.hangup(remoteUserId: showUserId, reason: "hangup by user") { error in
    }
    ```
#### 4.3.8 释放通话缓存
  - 释放后需要重新initialize，此时onCallStateChanged将返回(`state: .idle)`
    ```swift
    api.deinitialize()
    ```

- 场景调用CallAPI的时序图
  - 1v1场景
   <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/200/sequence_pure1v1.zh.png" width="500px"><br><br>
  - 秀场转1v1
    <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/200/sequence_showto1v1.zh.png" width="500px"><br><br>

## 5. 进阶集成
### 5.1 使用已经初始化过的RTM实例(rtmClient)。
  ```swift
    // 如果外部已经使用了rtm，可以传入rtm实例
    let rtmClient:AgoraRtmClientKit? = _createRtmClient() 
    // 如果外部创建了rtmClient，需要保证是已经登录的状态之后在进行后续设置
    rtmClient?.login(token) {[weak self] resp, error in
      if let error = error {return}
      //登录成功即可初始化CallRtmManager、CallRtmSignalClient、CallApi
      ...
    }
  ```
  **`注意：如果是自己创建的rtmClient，则需要自行维持登录状态，或者通过RtmManager的login方法进行登录`**

### 5.2 切换被叫的推流收流时机以节省费用。
  - 在CallApi里被叫的默认的推流时机有两种
    - [**默认**]收到呼叫即发布音视频流和收视频流
    - 接受后再发布音视频流和收视频流
  - 通过 `CallApiListenerProtocol` 的可选回调方法 `canJoinRtcOnCalling` 来实现切换
    - 如果 **返回true** 或 **不实现该回调方法**，则使用默认策略 `收到呼叫即发布音视频流和收视频流`
    - 如果 **返回false**，则使用策略 `接受后再发布音视频流和收视频流`
    ```swift
    /// 当收到呼叫时判断是否可以加入Rtc
    /// - Parameter eventInfo: 收到呼叫时的扩展信息
    /// - Returns: true: 可以加入 false: 不可以加入
    @objc optional func canJoinRtcOnCalling(eventInfo: [String: Any]) -> Bool
    ```
### 5.3 需要手动开启和关闭音视频流的采集
  -  由于CallApi内部会在通话时开启、结束通话时关闭采集音视频，因此如果在结束通话后外部需要手动开启音视频采集，例如当onCallStateChanged返回`(state: prepared)`时，可以开启采集。
     ```swift
       rtcEngine.enableLocalAudio(true)
       rtcEngine.enableLocalVideo(true)
     ```
### 5.4 消息中携带自定义数据结构
  - 通过在`PrepareConfig`的`userExtension`属性中设置参数，您可以在发送消息给对端时（例如呼叫/取消呼叫/同意/拒绝等）附加额外的用户扩展信息。对端可以通过回调消息接收到这个`userExtension`，以便在处理消息时获取相关的附加信息
    ```swift
      public func onCallStateChanged(with state: CallStateType,
                                     stateReason: CallStateReason,
                                     eventReason: String,
                                     eventInfo: [String : Any]) {
        let userExtension = eventInfo[kFromUserExtension] as? [String: Any]
        ...          
      }
     ```
### 5.5 通话异常定位。
  - 在双端连接过程中(state为calling/connecting/connected时)可以通过 `getCallId` 方法获取当次通话双端的呼叫id。
  - 通过CalAPI内部的日志上报，可以在声网后台查询到当次通话的各个节点耗时，请确保已经[开通声网自定义数据上报和分析服务](#custom-report)。
### 5.6 自定义信令消息
  - CallApi默认使用的是RTM的信令管理，CallApi通过[CallRtmSignalClient](./CallAPI/Classes/SignalClient/CallRtmSignalClient.swift)来进行发送和接收消息
  - 要实现一个自定义信令管理类，首先需要在对应管理类里实现[ISignalClient](./CallAPI/Classes/SignalClient/ISignalClient.swift)协议，声网已经基于环信实现了一个自定义信令管理类[CallEasemobSignalClient](./Example/CallAPI/SignalClient/CallEasemobSignalClient.swift)，您可以参考该实现，重新实现基于其他平台的信令管理类
  - 该信令管理类需要维持信令的登录注销等，并且需要自行维护信令的异常处理，保证CallApi在调用时处于可用状态
  - 通过如下方式使用自定义信令管理类，例如使用Demo里实现的CallEasemobSignalClient
    ```swift
    //创建信令管理类
    let signalClient = CallEasemobSignalClient(appKey: KeyCenter.IMAppKey, userId: "\(currentUid)")
    //信令登录
    signalClient.login() {[weak self] err in
        guard let self = self else {return}
        if let err = err {
            return
        }

        //初始化CallApi
        let config = CallConfig()
        config.signalClient = signalClient
        ...
        self.api.initialize(config: config)

        //准备通话环境
        let prepareConfig = PrepareConfig()
        ...
        api.prepareForCall(prepareConfig: prepareConfig) { err in
            //进行通话
        }
    }
    ```
  - 如果您实现了其他平台的信令管理类，而且不希望引入Rtm，可以在Podfile里使用如下裁剪方式集成
    ```
    pod 'CallAPI/WithoutRTM', :path => '../'
    ```
    **注意，以该裁剪方式集成的CallApi需要使用自定义信令类，CallRtmSignalClient 已经被移除**
    
### 5.7 需要监听通话频道的Rtc回调
   - CallApi里使用的 `joinChannelEx` 方式加入Rtc频道，因此不可以使用 `rtcEngine.addDelegate` 方式，需要通过 `rtcEngine.addDelegateEx` 并指定对应的频道来添加delegate
      ```swift
      /*
      roomId: 当前Rtc通话频道id
      currentUid: 当前用户uid
      */
      let connection = AgoraRtcConnection(channelId: roomId, localUid: Int(currentUid))
      rtcEngine.addDelegateEx(self, connection: connection)
      ```
    
      当前Rtc通话频道id可以通过 `onCallStateChanged` 为 `calling` 时从`evenInfo` 里解析获取
      
## 6. API说明
### CallApiListenerProtocol
- 状态响应回调，描述由于某个stateReason导致的state变化
  ```swift
  /// 状态响应回调
  /// - Parameters:
  ///   - state: 状态类型
  ///   - stateReason: 状态原因
  ///   - eventReason: 事件类型描述
  ///   - eventInfo: 扩展信息，不同事件类型参数不同，其中key为“publisher”为状态变更者id，空则表示是自己的状态变更
  func onCallStateChanged(with state: CallStateType,
                          stateReason: CallReason,
                          eventReason: String,
                          eventInfo: [String: Any])
  ```

- 详细事件变更回调
  ```swift
  /// 内部详细事件变更回调
  /// - Parameters:
  ///   - event: 事件
  ///   - eventReason: 事件原因，默认nil，根据不同event表示不同的含义
  @objc optional func onCallEventChanged(with event: CallEvent, eventReason: String?)
  ```


- 错误事件回调
  ```swift
  /// 发生错误的回调
  /// - Parameters:
  ///   - errorEvent: 错误事件
  ///   - errorType: 错误码类型
  ///   - errorCode: 错误码
  ///   - message: 错误信息
  @objc optional func onCallError(with errorEvent: CallErrorEvent,
                                  errorType: CallErrorCodeType,
                                  errorCode: Int, 
                                  message: String?)
  ```


- 当收到呼叫时判断是否可以加入Rtc
  ```swift
  /// 当收到呼叫时判断是否可以加入Rtc
  /// - Parameter eventInfo: 收到呼叫时的扩展信息
  /// - Returns: true: 可以加入 false: 不可以加入
  @objc optional func canJoinRtcOnCalling(eventInfo: [String: Any]) -> Bool
  ```


- Rtc token即将要过期回调
  ```swift
  /// token即将要过期(需要外部获取新token调用renewToken更新)
  @objc optional func tokenPrivilegeWillExpire()
  ```
  **收到该回调后，从业务服务器获取新的Rtc token，然后通过调用renewToken进行更新**

- 通话开始的回调
  ```swift
  /// 通话开始的回调
  /// - Parameters:
  ///   - roomId: 通话的频道id
  ///   - callerUserId: 发起呼叫的用户id
  ///   - currentUserId: 自己的id
  ///   - timestamp: 通话开始的时间戳，和19700101的差值，单位ms
  @objc optional func onCallConnected(roomId: String,
                                      callUserId: UInt,
                                      currentUserId: UInt,
                                      timestamp: UInt64)
  ```
- 通话结束的回调
  ```swift
  /// 通话结束的回调
  /// - Parameters:
  ///   - roomId: 通话的频道id
  ///   - hangupUserId: 挂断的用户id
  ///   - currentUserId: 自己的用户id
  ///   - timestamp: 通话结束的时间戳，和19700101的差值，单位ms
  ///   - duration: 通话时长，单位ms
  @objc optional func onCallDisconnected(roomId: String,
                                         hangupUserId: UInt,
                                         currentUserId: UInt,
                                         timestamp: UInt64,
                                         duration: UInt64)
  ```
- 打印日志
  ```swift
  /// 打印日志
  /// - Parameters:
  ///   - message: 日志信息
  ///   - logLevel: 日志优先级: 0: 普通日志，1: 警告日志, 2: 错误日志
  @objc optional func callDebugInfo(message: String, logLevel: CallLogLevel)
  ```


### CallApiProtocol

- 初始化配置，需要设置AppId、用户id、RTC/RTM 实例对象等
  ```swift
  func initialize(config: CallConfig)
  ```

- 不需要再使用CallApi，释放缓存
  ```swift
  func deinitialize(completion: @escaping (()->()))
  ```
  注意，调用了 `deinitialize` ，如需再次使用，需要重新调用 `initialize` 和 `prepareForCall` 才可以进行通话 

- Rtc token更新，当收到tokenPrivilegeWillExpire后可以获取新的token更新
  ```swift
  func renewToken(with rtcToken: String, rtmToken: String)
  ```
- 准备通话环境，需要调用成功才可以进行呼叫，如果需要更换通话的RTC 频道号可以重复调用，确保调用时必须是非通话状态(非calling、connecting、connected)才可调用成功
  ```swift
  func prepareForCall(prepareConfig: PrepareConfig, completion: ((NSError?)->())?)
  ```
- 添加回调的listener
  ```swift
  func addListener(listener: CallApiListenerProtocol)
  ```

- 移除回调的listener
  ```swift
  func removeListener(listener: CallApiListenerProtocol)
  ```

- 发起通话，主叫调用，通过prepareForCall设置的RTC频道号和远端用户建立RTC通话连接
  - 默认视频通话
    ```swift
    func call(remoteUserId: UInt, completion: ((NSError?)->())?)
    ```
  - 自定义通话类型（支持音频通话和视频通话）
    ```swift
    func call(remoteUserId: UInt, 
              callType: CallType,
              callExtension: [String: Any], 
              completion: ((NSError?)->())?)
    ```

- 取消正在发起的通话，主叫调用
  ```swift
  func cancelCall(completion: ((NSError?)->())?)
  ```

- 接受通话，被叫调用
  ```swift
  func accept(remoteUserId: UInt, completion: ((NSError?)->())?)
  ```

- 拒绝通话，被叫调用
  ```swift
  func reject(remoteUserId: UInt, reason: String?, completion: ((NSError?)->())?)
  ```

- 结束通话，主叫和被叫均可调用
  ```swift
  func hangup(remoteUserId: UInt, reason: String?, completion: ((NSError?)->())?)
  ```

- 获取当前通话的callId，callId为当次通话过程中唯一标识，通过该标识声网后台服务可以查询到当前通话的关键节点耗时和状态变迁的时间节点
  ```swift
    func getCallId() -> String
  ```
### ISignalClient(信令抽象协议)
- CallApi往信令系统发消息
  ```swift
  /// CallApi往信令系统发消息
  /// - Parameters:
  ///   - userId: 目标用户id
  ///   - message: 消息对象
  ///   - completion: 完成回调
  func sendMessage(userId: String,
                   message: String,
                   completion: ((NSError?)-> Void)?)
  ```
- 监听信令系统回调
  ```swift
  /// 监听信令系统回调
  /// - Parameter listener: <#listener description#>
  func addListener(listener: ISignalClientListener)
  ```
- 移除信令系统回调
  ```swift
  /// 移除信令系统回调
  /// - Parameter listener: <#listener description#>
  func removeListener(listener: ISignalClientListener)
  ```
### ISignalClientListener(信令回调协议)
- 收到消息的回调
  ```swift
  /// 收到消息的回调
  /// - Parameter message: 收到的消息
  func onMessageReceive(message: String)
  ```

- 信令日志回调
  ```swift
  /// 信令日志回调
  /// - Parameters:
  ///   - message: 日志消息内容
  ///   - logLevel: 日志优先级
  @objc optional func debugInfo(message: String, logLevel: Int)
  ```

## 7. 实现原理
### 7.1 优化呼叫性能和可靠性
#### 7.1.1 加快出图速度
  - 1.使用[万能Token](https://doc.shengwang.cn/doc/rtc/ios/best-practice/wildcard-token)
    - 为了提高通话质量和稳定性，我们采用万能 Token，可以节省因加入不同频道获取 Token 的时间，这意味着，在使用我们的服务时，您无需频繁获取 Token，而只需使用一个固定的 Token 即可。这样不仅可以提高您的使用效率，还可以让您更加专注于通话内容本身。
    - > 注意：为了保障通话的私密性和安全性，推荐每次呼叫都采用**不同的RTC频道号**。
  - 2.加快主叫出图速度
    - 2.1 **`[可选]`** 初始化时，可以提前加入自己的 RTC 频道。**`请注意，这种行为可能会导致额外的费用。如果对费用比较在意，您可以选择忽略此步骤`**。
    - 2.2 在向被叫发起呼叫时
      - 2.2.1 加入自己的 RTC 频道。
      - 2.2.2 往自己的RTC频道发送音视频流。
      - 2.2.3 订阅远端用户的音视频流。
      - 2.2.4 把远端用户静音。
      - 2.2.5 同时，为了避免错过首个 I 帧解码导致可能的首帧渲染慢，您需要创建一个临时的画布，并使用 `setupRemoteVideoEx` 方法将被叫用户的视频流渲染到该画布中。
    - 2.3 当收到被叫方的首帧并且已经接收到被叫方的同意后，即可认为连接成功。
      - 2.3.1 此时，您可以将之前创建的临时画布添加到视图中，完成视频的渲染。
      - 2.3.2 解除远端用户静音。
  - 3.加快被叫出图速度
    - 3.1 **`[可选][推荐]`** 当收到主叫呼叫后
      - 3.1.1 立即加入主叫的RTC频道。
      - 3.1.2 往主叫RTC频道发布音视频流。
      - 3.1.3 然后订阅远端的音视频流。
      - 3.1.4 把远端用户静音。
      - 3.1.5 同时，为了避免错过首个 I 帧解码导致可能的首帧渲染慢，您需要创建一个临时的画布，并使用 `setupRemoteVideoEx` 方法将主叫用户的视频流渲染到该画布中。
    **`请注意，[步骤3.1]会导致额外费用。如果对费用比较敏感，您可以选择忽略此步骤`**。
    - 3.2 当点击接受后
      - 3.2.1 如果收到呼叫时没有执行 **`[步骤3.1]`** ，那么需要在此处执行 **`[步骤3.1]`** 。
    - 3.3 当收到主叫方的首帧后，即可确认连接成功。
      - 3.3.1 此时，您可以将之前创建的临时画布添加到可视化视图中，从而完成视频渲染的过程。
      - 3.3.2 解除远端用户静音。
  - 4.时序图
      <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/210/sequence_solution_1v1.zh.png" width="500px"><br><br>
  
#### 7.1.2 提升消息送达率
  - 增加消息回执(如果信令通道有则忽略)
  - 增加超时重试(如果信令通道有则忽略)
  - 选择送达率高的信令通道，例如声网RTM

### 7.2 影响通话速度的指标
  - 主叫
    - 呼叫-被叫收到呼叫的耗时
    - 呼叫-收到被叫接受呼叫的耗时
    - 呼叫-被叫加入频道的耗时
    - 呼叫-主叫自己加入频道的耗时
    - 呼叫-收到被叫首帧的耗时
  - 被叫
    - 收到呼叫-接受呼叫的耗时
    - 收到呼叫-被叫自己加入频道的耗时
    - 收到呼叫-主叫加入频道的耗时
    - 收到呼叫-收到主叫首帧的耗时
