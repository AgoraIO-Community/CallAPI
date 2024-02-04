# CallAPI Example

本文档主要介绍如何快速跑通 CallAPI示例工程

## 1. 环境准备
- Xcode 14.0及以上版本
- 最低支持系统：iOS 13.0
- 请确保您的项目已设置有效的开发者签名

## 2. 运行示例

- 克隆或者直接下载项目源码
- 获取声网App ID -------- [声网Agora - 文档中心 - 如何获取 App ID](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-id)
  
  > 点击创建应用。
  > <br><img src="https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/create_app_1.jpg" width="500px">
  > <br>选择你要创建的应用类型。 
  > <br><img src="https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/create_app_2.jpg" width="500px">
  > 

- 获取App 证书 ----- [声网Agora - 文档中心 - 获取 App 证书](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-%E8%AF%81%E4%B9%A6)
  
  > 在声网控制台的项目管理页面，找到你的项目，点击配置。
  > <br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/1641871111769.png" width="500px">
  > <br>点击主要证书下面的复制图标，即可获取项目的 App 证书。
  > <br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/1637637672988.png" width="500px">
  > 
  
- 开启RTM
  > <br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config1.jpg" width="500px">
  > <br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config2.jpg" width="500px">
  > <br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config3.jpg" width="500px">
  >

- <a id="custom-report">开通声网自定义数据上报和分析服务</a>
  > 该服务当前处于免费内测期，如需试用该服务，请联系 sales@agora.io

- 在项目的[KeyCenter.swift](Example/CallAPI/KeyCenter.swift) 中填入上两部获取到的声网AppId和Certificate
  ```swift
  static var AppId: String = <#Your AppId#>
  static var Certificate: String = <#Your Certificate#>
  ```


- 打开终端，进入到[Podfile](Example/Podfile)目录下，执行`pod install`命令

- 最后打开[CallAPI.xcworkspace](Example/CallAPI.xcworkspace)，运行即可开始您的体验

## 3. 项目介绍
### 3.1 概述
  > CallAPI是声网面向一对一秒开设计的场景化API解决方案，可以让开发者在直播场景下，获得极速秒开、丝滑切换体验。

### 3.2 角色介绍
  - 主叫
    > 是发起呼叫并邀请对方进行通话的一方。主叫方主动发起呼叫请求，建立起视频通话连接，并发送邀请给被叫方。
  - 被叫
    > 是接收呼叫请求并被邀请进行通话的一方。被叫方在收到主叫方的呼叫邀请后可以接受或拒绝呼叫，如果接受呼叫，则与主叫方建立起视频通话连接。

### 3.3 核心功能：
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

- 把示例代码的[iOS](/iOS/)目录拷贝至自己的工程里，例如与Podfile文件同级
- 在Podfile文件里加入
  ```
  pod 'CallAPI', :path => './iOS'
  ```
- 如果您项目里已经使用了声网的RTC或RTM SDK
  - 请确保SDK不低于下述版本:
    - AgoraRtm_iOS: 2.1.8
    - AgoraRtcEngine_Special_iOS: 4.1.1.17
  - 如果外部依赖的版本与上述依赖不同，请修改[CallAPI.podspec](/iOS/CallAPI.podspec)文件对应SDK的版本
    ```
    s.dependency 'AgoraRtcEngine_Special_iOS', '4.1.1.17'
    s.dependency 'AgoraRtm_iOS', '2.1.8'
    ```
- 打开终端，执行`pod install`命令，CallAPI代码即可集成进项目里。
- 初始化设置。
  - 创建CallAPI实例。
    ```swift
      let api = CallApiImpl()
    ```
  - 初始化CallApi。
    ```swift
      // 初始化config
      let config = CallConfig()
      config.appId = KeyCenter.AppId
      config.userId = currentUid
      config.rtcEngine = rtcEngine   // rtc实例
      config.rtmClient = rtmClient   // 如果有则传，否则为nil
      api.initialize(config: config) 
    ```
- 设置回调。
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
- 准备通话环境。
    ```swift   
      let prepareConfig = PrepareConfig()
      prepareConfig.rtcToken = ...   // 设置rtc token(万能token)
      prepareConfig.rtmToken = ...   // 设置rtm token
      prepareConfig.roomId = "\(currentUid)"
      prepareConfig.localView = rightView
      prepareConfig.remoteView = leftView
      prepareConfig.autoJoinRTC = false  // 如果期望立即加入自己的RTC呼叫频道，则需要设置为true
      api.prepareForCall(prepareConfig: prepareConfig) { err in
          // 成功即可以开始进行呼叫
      }
    ```
    > **注意1：rtcToken必须使用`万能token`，否则接听呼叫会看不到远端音视频**
    

    > **注意2：每次发起呼叫前推荐都调用prepareForCall更新roomId，保证通话的安全性**
- 呼叫
  - 如果是主叫，调用call方法呼叫远端用户。
    ```swift
      api.call(remoteUserId: remoteUserId) { err in
      }
    ```
  - 发起呼叫后，主叫和被叫都会收到onCallStateChanged会返回`(state: .calling)`，变更成呼叫状态。
    > **`注意: 由于声网RTC只支持同时推送一路视频流，因此收到"calling"状态时需要把外部开启的音视频推流关闭，否则呼叫会出现异常`**
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
- 如果被叫方同意呼叫
  - 通过 onCallStateChanged 会返回`连接中状态(state: .connecting)`，然后在远端画面渲染完成后，状态将变为`已连接(state: .connected)`，表示呼叫成功。这个状态变化过程反映了呼叫的建立和视频画面的渲染。
- 如果被叫方拒绝呼叫
  - onCallStateChanged 会返回`(state: .prepared, stateReason: .localRejected)`(被叫)或`(state: .prepared, stateReason: .remoteRejected)`(主叫)。
- 如果被叫方未作出回应(同意或拒绝)
  - onCallStateChanged 会返回`(state: .prepared, stateReason: .callingTimeout)`。表示呼叫超时，未能成功建立连接。
- 如需结束呼叫，可以调用挂断函数
  - 此时onCallStateChanged 将返回 `(state: .prepared, stateReason: .localHangup)`(本地用户)或`(state: .prepared, stateReason: .remoteHangup)`(远端用户)。这表示呼叫已经被挂断，连接已经断开。
    ```swift
      api.hangup(remoteUserId: showUserId, reason: "hangup by user") { error in
      }
    ```
- 释放通话缓存
  - 释放后需要重新initialize，此时onCallStateChanged将返回(`state: .idle)`
    ```swift
      api.deinitialize()
    ```

- 场景调用CallAPI的时序图
  - 1v1场景
   <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_pure1v1.zh.png" width="500px"><br><br>
  - 秀场转1v1
    <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_showto1v1.zh.png" width="500px"><br><br>

## 5. 进阶集成
- 使用外部初始化的RTM。
  ```swift
    // 如果外部已经使用了rtm，可以传入rtm实例
    let rtmClient:AgoraRtmClientKit? = _createRtmClient() 
    // 如果外部创建了rtmClient，需要保证是已经登录的状态之后在进行后续设置
    rtmClient?.login(token) {[weak self] resp, error in
      if let error = error {return}
      // 登录成功即可进行CallApi的初始化
      let config = CallConfig()
      config.rtmClient = rtmClient 
      ...

      api.initialize(config: config) 
    }
  ```
  **`注意：如果通过外部传入rtmClient，则需要外部维持登陆状态`**

- 修改被叫推流策略以节省费用。
  - 修改[CallApiImpl.swift](/iOS/CallAPI/Classes/CallApiImpl.swift)中对应状态(calling -> accepted)，从`收到呼叫即推流和收流`改为`接受后再推流和收流`。
    ```swift
      /// 被叫呼叫中加入RTC的策略
      enum CalleeJoinRTCPolicy: Int {
          case calling    //在接到呼叫时即加入频道并推送音视频流，被叫时费用较高但出图更快
          case accepted   //在点击接受后才加入频道并推送音视频流，被叫时费用较低但出图较慢
      }

      //let calleeJoinRTCPolicy: CalleeJoinRTCPolicy = .calling
      let calleeJoinRTCPolicy: CalleeJoinRTCPolicy = .accepted
    ```
- 外部有额外采集推送音视频流的操作
  -  由于CallApi内部会在通话时开启、结束通话时关闭采集音视频，因此如果在结束通话后外部需要手动开启音视频采集，例如当onCallStateChanged返回`(state: prepared)`时，可以开启采集。
     ```swift
       rtcEngine.enableLocalAudio(true)
       rtcEngine.enableLocalVideo(true)
     ```
- 设置消息信令自定义消息
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
- 通话异常定位。
  - 在双端连接过程中(state为calling/connecting/connected时)可以通过 `getCallId` 方法获取当次通话双端的呼叫id。
  - 通过CalAPI内部的日志上报，可以在声网后台查询到当次通话的各个节点耗时，请确保已经[开通声网自定义数据上报和分析服务](#custom-report)。

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

- token即将要过期回调
  ```swift
    /// token即将要过期(需要外部获取新token调用renewToken更新)
    @objc optional func tokenPrivilegeWillExpire()
  ```
  **收到该回调后，从业务服务器获取新的Rtc/Rtm token，然后通过调用renewToken进行更新**

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

- token更新，当收到tokenPrivilegeWillExpire后可以获取新的token更新
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
  ```swift
    func call(remoteUserId: UInt, completion: ((NSError?)->())?)
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
      - 2.2.3 订阅远端的视频流，不订阅音频流。
      - 2.2.4 同时，为了避免错过首个 I 帧解码导致可能的首帧渲染慢，您需要创建一个临时的画布，并使用 `setupRemoteVideoEx` 方法将被叫用户的视频流渲染到该画布中。
    - 2.3 当收到收到被叫的接受消息后，开始订阅远端音频流。
    - 2.4 当收到被叫方的首帧并且已经接收到被叫方的同意后，即可认为连接成功。此时，您可以将之前创建的临时画布添加到视图中，完成视频的渲染。
  - 3.加快被叫出图速度
    - 3.1 **`[可选][推荐]`** 当收到主叫呼叫后
      - 3.1.1 立即加入主叫的RTC频道。
      - 3.1.2 往主叫RTC频道推送音视频流。
      - 3.1.3 然后订阅远端的视频流，不订阅音频流。
      - 3.1.4 同时，为了避免错过首个 I 帧解码导致可能的首帧渲染慢，您需要创建一个临时的画布，并使用 `setupRemoteVideoEx` 方法将主叫用户的视频流渲染到该画布中。
    **`请注意，[步骤3.1]会导致额外费用。如果对费用比较敏感，您可以选择忽略此步骤`**。
    - 3.2 当点击接受后
      - 3.2.1 如果收到呼叫时没有执行 **`[步骤3.1]`** ，那么需要在此处执行 **`[步骤3.1]`** 。
      - 3.2.2 开始订阅远端音频流。
    - 3.3 当收到主叫方的首帧后，即可确认连接成功。此时，您可以将之前创建的临时画布添加到可视化视图中，从而完成视频渲染的过程。
  - 4.时序图
      <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_solution_1v1.zh.png" width="500px"><br><br>
  
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
