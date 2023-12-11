# CallAPI Example

*[English](README.md) | 中文*

本文档主要介绍如何快速跑通 CallAPI示例工程

## 环境准备
- Xcode 13.0及以上版本
- 最低支持系统：iOS 10.0
- 请确保您的项目已设置有效的开发者签名

## 运行示例

- 克隆或者直接下载项目源码
- 在项目的[KeyCenter.swift](Example/CallAPI/KeyCenter.swift) 中填入声网的AppId和Certificate
```
static var AppId: String = <#Your AppId#>
static var Certificate: String = <#Your Certificate#>
```


- 打开终端，进入到[Podfile](Example/Podfile)目录下，执行`pod install`命令

- 最后打开[CallAPI.xcworkspace](Example/CallAPI.xcworkspace)，运行即可开始您的体验
  
## 快速接入

- 把示例代码的[iOS](iOS)目录拷贝至自己的工程里，例如与Podfile文件同级
- 在Podfile文件里加入
  ```
  pod 'CallAPI', :path => './iOS'
  ```
- 打开终端，执行`pod install`命令，CallAPI代码即可集成进项目里
- 创建CallAPI实例
  ```swift
    let api = CallApiImpl()
  ```
- 初始化(纯1v1模式)
  ```swift
    let config = CallConfig()
    config.mode = .pure1v1
    config.appId = KeyCenter.AppId
    config.userId = currentUid
    config.autoAccept = false
    config.rtcEngine = _createRtcEngine()
    config.rtmClient = _createRtmClient() //如果已经使用了rtm，可以传入rtm实例，否则可以设置为nil
    config.localView = rightView
    config.remoteView = leftView
        
    self.api.initialize(config: config, token: tokenConfig!) { error in
        //error
    }
  ```
- 初始化(秀场转1v1模式)
  ```swift
    let config = CallConfig()
    config.role = role
    config.appId = KeyCenter.AppId
    config.userId = currentUid
    config.rtcEngine = _createRtcEngine()
    config.rtmClient = _createRtmClient() //如果已经使用了rtm，可以传入rtm实例，否则可以设置为nil    
    config.localView = rightView
    config.remoteView = leftView

    // 如果是被叫会隐式调用prepare
    self.api.initialize(config: config, token: tokenConfig!) { error in
        if let error = error {
            completion(false)
            return
        }
        self.role = role
        guard role == .caller else {
            completion(true)
            return
        }
        // 如果是主叫并且想加快呼叫，可以在init完成之后调用prepare
        let prepareConfig = PrepareConfig.callerConfig()
        prepareConfig.autoLoginRTM = true
        prepareConfig.autoSubscribeRTM = true
        self.api.prepareForCall(prepareConfig: prepareConfig) { err in
            completion(err == nil)
        }
    }
  ```
  >注意⚠️：如果通过外部传入rtmClient，则需要外部维持登陆状态

- 设置回调
  ```swift
    api.addListener(listener: self)
    extension ShowTo1v1RoomViewController:CallApiListenerProtocol {
        public func onCallStateChanged(with state: CallStateType,
                                       stateReason: CallReason,
                                       eventReason: String,
                                       elapsed: Int,
                                       eventInfo: [String : Any]) {
        }

        @objc func onCallEventChanged(with event: CallEvent, elapsed: Int) {

        }
    }
  ```
- 呼叫
  - 如果是主叫，调用call方法呼叫远端用户
    ```swift
      callApi.call(roomId: remoteRoomId, remoteUserId: remoteUserId) { err in
      }
    ```
  - 此时不管主叫还是被叫都会收到onCallStateChanged会返回state = .calling，变更成呼叫状态
    > 注意⚠️: 收到calling时需要把外部开启的音视频推流关闭，否则呼叫会失败

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
                //如果是呼叫中
            }
        }
      ```
- 如果是秀场转1v1，默认不需要处理呼叫响应，如果需要处理可以吧CallConfig的autoAccept设置为false表示不自动接受呼叫，如果非自动接受呼叫，被叫需要自行同意或者拒绝，主叫可以取消呼叫
  ```swift
    //同意,需要根据fromRoomId获取对应token
    api.accept(roomId: fromRoomId, remoteUserId: fromUserId, rtcToken: rtcToken) { err in
    }

    // 拒绝
    api.reject(roomId: fromRoomId, remoteUserId: fromUserId, reason: "reject by user") { err in
    }

    //取消呼叫
    api.cancelCall { err in
    }
  ```
- 如果同意，onCallStateChanged会先变成连接中(state = .connecting)，然后渲染出远端画面后则会变成state = .connected，表示呼叫成功
- 如果拒绝，onCallStateChanged会返回state = .prepared, event = .localRejected/.remoteRejected
- 如未同意/拒绝，onCallStateChanged会返回state = .prepared, event = .callingTimeout
- 如果呼叫需要结束，可以调用挂断，此时本地onCallStateChanged会返回state = .prepared, event = .localHangup，远端则会收到state = .prepared, event = .remoteHangup
  ```swift
    api.hangup(userId: showUserId) { error in
    }
  ```

## 调用时序图
### 纯1v1
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/sequence_pure1v1.zh.png)

### 秀场转1v1
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/sequence_pure1v1.zh.png)

## 许可证

CallAPI 使用 MIT 许可证，详情见 LICENSE 文件

