# CallAPI Example

*English | [中文](README_zh.md)*

This document mainly introduces how to quickly get through the CallAPI example project.

## Requirements
- Xcode 13.0 and later
- Minimum OS version: iOS 10.0
- Please ensure that your project has a valid developer signature set up

## Getting Started

- Clone or download source code
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
- Fill in the AppId/Certificate in the [KeyCenter.swift](Example/CallAPI/KeyCenter.swift) of the project
```
static var AppId: String = <#Your AppId#>
static var Certificate: String = <#Your Certificate#>
```
- Open the terminal and enter the [Podfile](Example/Podfile) directory, run `pod install`
- Finally, open [CallAPI.xcworkspace](Example/CallAPI.xcworkspace) and run it to start your experience
  
## Integration

- Copy the [iOS](iOS) directory of the sample code to your own project, for example, at the same level as the Podfile file.


- Add to the Podfile file
  ```
  pod 'CallAPI', :path => './iOS'
  pod 'AgoraRtcEngine_iOS', :path => './libs'
  ```
- Open the terminal, execute 'pod install', and the CallAPI code can be integrated into the project.
- Create CallAPI instance
  ```swift
    let api = CallApiImpl()
  ```
- Initialization(pure 1v1)
  ```swift
    let config = CallConfig()
    config.mode = .pure1v1
    config.appId = KeyCenter.AppId
    config.userId = currentUid
    config.autoAccept = false
    config.rtcEngine = _createRtcEngine()
    config.rtmClient = _createRtmClient() //If RTM is already used, it can be passed in as an RTM instance, otherwise it can be set to nil
    config.localView = rightView
    config.remoteView = leftView
        
    self.api.initialize(config: config, token: tokenConfig!) { error in
        // error
    }
  ```
- Initialize(Show to 1v1 mode)
  ```swift
    let config = CallConfig()
    config.role = role
    config.ownerRoomId = showRoomId
    config.appId = KeyCenter.AppId
    config.userId = currentUid
    config.rtcEngine = _createRtcEngine()
    config.rtmClient = _createRtmClient() //If RTM is already used, it can be passed in as an RTM instance, otherwise it can be set to nil
    config.localView = rightView
    config.remoteView = leftView

    // If it is called, it will implicitly call prepare
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
        // If the caller wants to speed up the call, you can call prepare after init is complete
        let prepareConfig = PrepareConfig.callerConfig()
        prepareConfig.autoLoginRTM = true
        prepareConfig.autoSubscribeRTM = true
        self.api.prepareForCall(prepareConfig: prepareConfig) { err in
            completion(err == nil)
        }
    }
  ```
    >take care ⚠️： If the rtmClient is passed in externally, it is necessary to maintain the login status externally

- Set callback
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
- Call
  - If it is the caller, call the call method to call the remote user
    ```swift
      callApi.call(roomId: remoteRoomId, remoteUserId: remoteUserId) { err in
      }
    ```
  - At this point, both the caller and the called will receive onCallStateChanged and return state = . calling, changing to the calling state
    > take care ⚠️: When receiving a call, it is necessary to turn off the external enabled audio and video streaming, otherwise the call will fail

    ```swift
      public func onCallStateChanged(with state: CallStateType,
                                     stateReason: CallReason,
                                     eventReason: String,
                                     elapsed: Int,
                                     eventInfo: [String : Any]) {
          let publisher = UInt(eventInfo[kPublisher] as? String ?? "") ?? currentUid
          
          // The user who triggered the status only handles it themselves
          guard publisher == currentUid else {
              return
          }
          
          if state == .calling {
              //If it is a call in progress
          }
      }
    ```
- If it is a show to 1v1 mode, the default is not to process the call response. If it needs to be processed, you can set the autoAccept in CallConfig to false to indicate that the call will not be automatically accepted. If the call is not automatically accepted, the called party needs to agree or refuse on their own, and the caller can cancel the call.
  ```swift
    //Agree, we need to obtain the corresponding token based on FromRoomId
    api.accept(roomId: fromRoomId, remoteUserId: fromUserId, rtcToken: rtcToken) { err in
    }

    // Reject
    api.reject(roomId: fromRoomId, remoteUserId: fromUserId, reason: "reject by user") { err in
    }

    // Cancel Call
    api.cancelCall { err in
    }
  ```
- If agreed, onCallStateChanged will first become connected(state=.connecting), and then after rendering the remote screen, it will become state=.connected, indicating that the call was successful.
- If rejected, onCallStateChanged will return state=.prepared, event=.localRejected/.remoteRejected.
- If not agreed/rejected, onCallStateChanged will return state=.prepared, event=.callingTimeout.
- If the call needs to end, you can call hang up. At this time, the local onCallStateChanged will return state=. prepared, event=. localHangup, and the remote will receive state=. prepared, event=. remoteHangup.
  ```swift
    api.hangup(userId: showUserId) { error in
    }
  ```

## Call timing diagram
### Pure 1v1
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/sequence_pure1v1.en.png)

### Live to 1v1
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/sequence_pure1v1.en.png)
## License

Call API uses MIT License, see LICENSE file for details.