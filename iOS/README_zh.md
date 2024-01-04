# CallAPI Example

*[English](README.md) | 中文*

本文档主要介绍如何快速跑通 CallAPI示例工程

## 1. 环境准备
- Xcode 14.0及以上版本
- 最低支持系统：iOS 13.0
- 请确保您的项目已设置有效的开发者签名

## 2. 运行示例

- 克隆或者直接下载项目源码
- 获取声网App ID -------- [声网Agora - 文档中心 - 如何获取 App ID](https://docs.agora.io/cn/Agora%20Platform/get_appid_token?platform=All%20Platforms#%E8%8E%B7%E5%8F%96-app-id)
  
  > 点击创建应用   
  > ![](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/create_app_1.jpg)
  > 
  > 选择你要创建的应用类型  
  > ![](https://accktvpic.oss-cn-beijing.aliyuncs.com/pic/github_readme/create_app_2.jpg)
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

### 3.5 优化呼叫性能和可靠性
#### 3.5.1 加快出图速度
  - 1.使用[万能Token](https://doc.shengwang.cn/doc/rtc/ios/best-practice/wildcard-token)
    - 为了提高通话质量和稳定性，我们采用万能 Token，可以节省因加入不同频道获取 Token 的时间，这意味着，在使用我们的服务时，您无需频繁获取 Token，而只需使用一个固定的 Token 即可。这样不仅可以提高您的使用效率，还可以让您更加专注于通话内容本身。
  - 2.加快主叫出图速度
    - 2.1 **`[可选]`** 初始化时，可以提前加入自己的 RTC 频道。**`请注意，这种行为可能会导致额外的费用。如果对费用比较在意，您可以选择忽略此步骤`**。
    - 2.2 在发起呼叫时，需要加入自己的 RTC 频道，并发送音视频流，然后订阅远端的视频流。同时，为了避免错过首个 I 帧解码导致可能的首帧渲染慢，您需要创建一个临时的画布，并使用 `setupRemoteVideoEx` 方法将被叫用户的视频流渲染到该画布中。
    - 2.3 在接收到被叫方的接受后，开始订阅远端音频流。
    - 2.4 当收到被叫方的首帧并且已经接收到被叫方的同意后，即可认为连接成功。此时，您可以将之前创建的临时画布添加到视图中，完成视频的渲染。
  - 3.加快被叫出图速度
    - 3.1 **`[可选][默认]`** 在收到呼叫后，应立即加入主叫的RTC频道，并推送音视频流。同时，需要订阅视频流并创建一个临时画布。使用 `setupRemoteVideoEx` 方法将被叫用户的视频流渲染到该临时画布中，这样可以避免错过首个 I 帧解码，从而避免可能的首帧渲染慢的问题，**`请注意，这个行为在 CallApi 中是默认的，并且会导致额外费用。如果对费用比较敏感，建议您修改内部参数以延迟触发该行为。这样可以更好地控制费用，并根据实际需求进行操作`**。
    - 3.2 当点击接受后
      - 3.2.1 如果收到呼叫时没有执行 **`[步骤3.1]`** ，那么需要在此处执行 **`[步骤3.1]`** 。
      - 3.2.2 开始订阅远端音频流。
    - 3.3 当收到主叫方的首帧后，即可确认连接成功。此时，您可以将之前创建的临时画布添加到可视化视图中，从而完成视频渲染的过程。
  
#### 3.5.2 提升消息送达率
  - 增加消息回执(如果信令通道有则忽略)
  - 增加超时重试(如果信令通道有则忽略)
  - 选择送达率高的信令通道，例如声网RTM
#### 3.5.3 提升安全性
  - 为了保障通话的私密性和安全性，我们可以预先分配多个频道号，每次呼叫通过不同频道来呼叫，以确保通话内容的私密性。
  - 采用多端统计的计费策略，确保通话费用准确无误，并且保证安全性。

#### 3.5.4 时序图
- 
 ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_solution_1v1.zh.png)

### 3.6 影响通话速度的指标
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
  
## 4. 快速集成

- 把示例代码的[iOS](iOS)目录拷贝至自己的工程里，例如与Podfile文件同级
- 在Podfile文件里加入
  ```
  pod 'CallAPI', :path => './iOS'
  ```
- 请确保项目中使用正确的声网SDK依赖，保证和CallApi的依赖不冲突:
  - AgoraRtm_iOS: 2.1.8
  - AgoraRtcEngine_Special_iOS: 4.1.1.17

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
      config.rtcEngine = _createRtcEngine()
      config.rtmClient = rtmClient   // 如果有则传，否则为nil
    ```
  - 准备通话环境。
    ```swift   
      self.api.initialize(config: config) 
      let prepareConfig = PrepareConfig()
      prepareConfig.rtcToken = ...   // 设置rtc token(万能token)
      prepareConfig.rtmToken = ...   // 设置rtm token
      prepareConfig.roomId = "\(currentUid)"
      prepareConfig.localView = rightView
      prepareConfig.remoteView = leftView
      prepareConfig.autoAccept = false  // 如果期望收到呼叫自动接通，则需要设置为true
      prepareConfig.autoJoinRTC = false  // 如果期望立即加入自己的RTC呼叫频道，则需要设置为true
      api.prepareForCall(prepareConfig: prepareConfig) { err in
          // 成功即可以开始进行呼叫
      }
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
                                     elapsed: Int,
                                     eventInfo: [String : Any]) {
      }

      @objc func onCallEventChanged(with event: CallEvent, elapsed: Int) {
        
      }
    ```
- 呼叫
  - 如果是主叫，调用call方法呼叫远端用户。
    ```swift
      callApi.call(remoteUserId: remoteUserId) { err in
      }
    ```
  - 此时不管主叫还是被叫都会收到onCallStateChanged会返回state = .calling，变更成呼叫状态。
    **`注意: 收到"calling"状态时需要把外部开启的音视频推流关闭，否则呼叫会失败`**
      ```swift
        public func onCallStateChanged(with state: CallStateType,
                                       stateReason: CallReason,
                                       eventReason: String,
                                       elapsed: Int,
                                       eventInfo: [String : Any]) {
            let publisher = UInt(eventInfo[kPublisher] as? String ?? "") ?? currentUid
            
            // 触发状态的用户是自己才处理
            guard publisher == currentUid else {
                return
            }
            
            if state == .calling {
                // 如果是呼叫中

                // 主叫用户的uid
                let fromUserId = eventInfo[kFromUserId] as? UInt ?? 0
                // 目标用户的uid，为当前用户
                let toUserId = eventInfo[kRemoteUserId] as? UInt ?? 0
            }
        }
      ```
- 如果在 PrepareConfig 中 autoAccept 设置为 true，则无需显式调用 accept 方法，CallApi 将会自动接受呼叫。若将 autoAccept 设置为 false，则被叫方需要手动同意或拒绝呼叫，而主叫方可以选择取消呼叫。
  ```swift
    // 同意
    api.accept(remoteUserId: fromUserId) { err in
    }

    // 拒绝
    api.reject(remoteUserId: fromUserId, reason: "reject by user") { err in
    }

    // 取消呼叫
    api.cancelCall { err in
    }
  ```
- 如果被叫方同意呼叫，通过 onCallStateChanged 事件会先切换为连接中状态（state: .connecting），然后在远端画面渲染完成后，状态将变为已连接（state: .connected），表示呼叫成功。这个状态变化过程反映了呼叫的建立和视频画面的渲染。
- 如果被叫方拒绝呼叫，onCallStateChanged 会返回(state: .prepared)，(stateReason: .localRejected)(被叫)或(stateReason: .remoteRejected)(主叫)。
- 如果被叫方未作出回应(同意或拒绝)，onCallStateChanged 会返回(state: .prepared)，(stateReason: .callingTimeout)。表示呼叫超时，未能成功建立连接。
- 如需结束呼叫，可以调用挂断函数。此时onCallStateChanged 将返回 (state: .prepared), (stateReason: .localHangup)(本地用户)或(stateReason: .remoteHangup)(远端用户)。这表示呼叫已经被挂断，连接已经断开。
  ```swift
    api.hangup(remoteUserId: showUserId) { error in
    }
  ```
- 释放通话缓存，释放后需要重新initialize。
  ```swift
    api.deinitialize()
  ```

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
  - 修改[CallApiImpl.swift](/iOS/CallAPI/Classes/CallApiImpl.swift)中对应状态(onCall->accept)，从`收到呼叫即推流和收流`改为`接受后再推流和收流`，以保证在接受呼叫之后才推流。
    ```swift
      //let calleeJoinRTCType: CalleeJoinRTCType = .onCall
      let calleeJoinRTCType: CalleeJoinRTCType = .accept
    ```
- 通话异常定位。
  - 在双端连接过程中(state为calling/connecting/connected时)可以通过 `getCallId` 方法获取当次通话双端的呼叫id。
  - 通过CalAPI内部的日志上报，可以在声网后台查询到当次通话的各个节点耗时，请确保已经[开通声网自定义数据上报和分析服务](#custom-report)。

## 6. 场景调用时序图
### 6.1 1v1场景
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_pure1v1.zh.png)

### 6.2 秀场转1v1场景
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_showto1v1.zh.png)



