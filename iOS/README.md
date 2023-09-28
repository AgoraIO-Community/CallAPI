# CallAPI Example

*English | [中文](README_zh.md)*

This document mainly introduces how to quickly get through the CallAPI example project.

## Requirements
- Xcode 13.0 and later
- Minimum OS version: iOS 10.0
- Please ensure that your project has a valid developer signature set up

## Getting Started

- Clone or download source code
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
    config.role = .caller  // Pure 1v1 can only be set as the caller
    config.mode = .pure1v1
    config.appId = KeyCenter.AppId
    config.userId = currentUid
    config.autoAccept = false
    config.rtcEngine = _createRtcEngine()
    config.localView = rightView
    config.remoteView = leftView
        
    self.api.initialize(config: config, token: tokenConfig!) { error in
        // Requires active call to prepareForCall
        let prepareConfig = PrepareConfig.callerConfig()
        prepareConfig.autoLoginRTM = true
        prepareConfig.autoSubscribeRTM = true
        self.api.prepareForCall(prepareConfig: prepareConfig) { err in
            completion(err == nil)
        }
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
    if role == .caller {
        config.localView = rightView
        config.remoteView = leftView
    } else {
        config.localView = leftView
        config.remoteView = rightView
    }
    // If it is called, it will implicitly call prepare
    self.api.initialize(config: config, token: tokenConfig!) { error in
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
  - If it is the callee, Change to call state, onCallStateChanged will return state=. calling.
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
- If it is a show to 1v1 mode, it does not need to be processed by default. If it needs to be processed, you can set the autoAccept in CallConfig to false to indicate that the call cannot be automatically accepted. If the call is not automatically accepted, the callee needs to agree or reject it on their own, and the caller can cancel the call.
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
## License

Call API uses MIT License, see LICENSE file for details.