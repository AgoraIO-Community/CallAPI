# CallAPI Example

*English | [中文](README_zh.md)*

This document mainly introduces how to quickly get through the CallAPI example project.

## 1. Requirements
- Xcode 14.0 and later
- Minimum OS version: iOS 13.0
- Please ensure that your project has a valid developer signature set up

## 2. Getting Started

- Clone or download source code
- Follow [The Account Document](https://docs.agora.io/en/video-calling/reference/manage-agora-account) to get the **App ID** and **App Certificate(if enable token)**.
- How to enable RTM
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config1.jpg)
  > 
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config2.jpg)
  > 
  > ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/config/rtm_config3.jpg)
  > 
- <a id="custom-report">Activate Agora custom data reporting and analysis services</a>
  > This service is currently in the free beta period. If you need to try this service, please contact sales@agora.io
- Fill in the AppId/Certificate in the [KeyCenter.swift](Example/CallAPI/KeyCenter.swift) of the project
  ```swif4
  static var AppId: String = <#Your AppId#>
  static var Certificate: String = <#Your Certificate#>
  ```
- Open the terminal and enter the [Podfile](Example/Podfile) directory, run `pod install`
- Finally, open [CallAPI.xcworkspace](Example/CallAPI.xcworkspace) and run it to start your experience


## 3. Introduction
### 3.1 Overview
  > CallAPI is a scenario based API solution designed by Agora for one-on-one quickly rendering, which allows developers to experience high-speed and smooth switching in live streaming scenarios.

### 3.2 Role
  - Caller
    > The party who initiates a call and invites the other party to make the call. The caller initiates a call request, establishes a video call connection, and sends an invitation to the callee.
  - Callee
    > The party who receives a call request and is invited to make a call. After receiving the call invitation from the caller, the callee can accept or reject the call. If they accept the call, they will establish a video call connection with the caller.

### 3.3 Core functions：
  - **Call**：The caller initiates a call.
  - **Cancel Call**：After the caller initiates the call, they can initiate a cancellation call to interrupt the current call before the call is successful.
  - **Accept**：The callee can accept the current call after receiving the call request from the caller.
  - **Reject**：The callee party can reject the current call after receiving the call request from the caller.
  - **Hangup**：The caller/callee can initiate a hang up request during a call to interrupt the call。
  
### 3.4 How to play
  - 1v1 scenario
    > Usually in social scenes with strangers, users can filter out other users of interest based on photos and personal profiles, or engage in 1v1 private video calls between two users through random matching of geographic location and tags. During the call, by default, both 1v1 users have their cameras and microphones turned on, and can send and receive audio and video streams in both directions.
  - Live to 1v1 scene
    > During the live broadcast, users can pay to initiate 1v1 video calls. After the call is connected, the original live broadcast room of the anchor is not closed but does not push the stream. The anchor transitions to 1v1 to have a video call with paying users; The gameplay of the scene where the anchor switches back to the original live broadcast room to continue the live broadcast after the 1v1 video call ends.

    
### 3.5 Optimize call performance and reliability
#### 3.5.1 Accelerate the speed of drawing
  - 1.Using [Wildcard Token](https://doc.shengwang.cn/doc/rtc/ios/best-practice/wildcard-token)
    - In order to enhance call quality and stability, we employ a wildcard token that eliminates the need for obtaining a token for each channel. This means that when using our service, you do not need to retrieve tokens frequently, but instead use a single fixed token. This approach not only improves efficiency but allows you to focus more on the content of your calls.
  - 2.Accelerate the caller speed
    - 2.1 **`[Optional]`** When initializing, you can join your own RTC channel in advance.**`Please note that this behavior may result in additional costs. If you are concerned about the cost, you can choose to ignore this step`**.
    - 2.2 When making a call, you need to join your own RTC channel, publish audio and video streams, and then subscribe to remote video streams. At the same time, to avoid missing the decoding of the first I frame and potentially causing slow rendering of the first frame, you need to create a temporary canvas and use the `setupRemoteVideoEx` method to render the video stream of the callee user to that canvas.
    - 2.3 After receiving the acceptance from the callee party, start subscribing to the remote audio stream.
    - 2.4 When the first frame from the callee is received and the callee's consent has been received, the connection is considered successful. At this point, you can add the previously created temporary canvas to the view to complete the rendering of the video.
  - 3.Accelerate the callee speed
    - 3.1 **`[Optional][Default]`** After receiving the call, you should immediately join the caller's RTC channel and push audio and video streams. At the same time, it is necessary to subscribe to the video stream and create a temporary canvas. Use the `setupRemoteVideoEx`  method to render the video stream of the callee user to this temporary canvas, which can avoid missing the first I frame decoding and potentially slow rendering of the first frame，**`Please note that this behavior is default in CallApi and may result in additional fees. If you are sensitive to costs, it is recommended that you modify internal parameters to delay triggering this behavior. This can better control costs and operate according to actual needs`**.
    - 3.2 After clicking accept
      - 3.2.1 If the call is not executed upon receipt **`[Step 3.1]`**,So **`[Step 3.1]`** needs to be executed here.
      - 3.2.2 Start subscribing to remote audio streams.
    - 3.3 After receiving the first frame from the caller, a successful connection can be confirmed. At this point, you can add the previously created temporary canvas to the visualization view to complete the video rendering process.
  
#### 3.5.2 Improve message delivery rate
  - Add message receipt (ignore if there is a signaling channel)
  - Increase timeout retry (ignored if signaling channel exists)
  - Choose a signaling channel with a high delivery rate, such as Agora RTM
#### 3.5.3 Improve security
  - To ensure the privacy and security of calls, we can pre assign multiple channel numbers, and each call is made through different channels to ensure the privacy of the call content.
  - Adopting a multi end statistical billing strategy to ensure accurate and secure call fees.

#### 3.5.4 Sequence Diagram
- 
 ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_solution_1v1.en.png)

### 3.6 Metrics that affect call speed
  - Caller
    - Time taken for Caller to receive a Call
    - Time taken for Caller to receive acceptance of Call
    - Time taken for Caller to join a channel
    - Time taken for Callee to join a channel
    - Time taken for Caller to receive the first frame from Callee
  - Callee
    - Time taken to receive and accept a call
    - Time taken for Callee to join the channel after receiving a call
    - Time taken for Caller to join the channel after making a call
    - Time taken to receive the first frame from the Caller after receiving a call
  

## 4. Quick Integration

- Copy the [iOS](iOS) directory of the sample code into your project, for example, at the same level as the Podfile.
- Add the following line to your Podfile:
  ```
  pod 'CallAPI', :path => './iOS'
  ```
- Ensure that you are using the correct Agora SDK dependencies in your project, to avoid conflicts with CallAPI dependencies:
  - AgoraRtm_iOS: 2.1.8
  - AgoraRtcEngine_Special_iOS: 4.1.1.17

- Open the terminal and execute the `pod install` command to integrate the CallAPI code into your project.
- Initialization setup.
  - Create a CallAPI instance.
    ```swift
      let api = CallApiImpl()
    ```
  - Initialize CallApi.
    ```swift
      //Initialize config
      let config = CallConfig()
      config.appId = KeyCenter.AppId
      config.userId = currentUid
      config.rtcEngine = _createRtcEngine()
      config.rtmClient = rtmClient   //If it exists, pass it; otherwise, it is nil.
    ```
  - Prepare the call environment.
    ```swift   
      self.api.initialize(config: config) 
      let prepareConfig = PrepareConfig()
      prepareConfig.rtcToken = ...   //set rtc token (wildcard token)
      prepareConfig.rtmToken = ...   //set rtm token
      prepareConfig.roomId = "\(currentUid)"
      prepareConfig.localView = rightView
      prepareConfig.remoteView = leftView
      prepareConfig.autoAccept = false  //If you expect to automatically accept incoming calls, you need to set it to true.
      prepareConfig.autoJoinRTC = false  //If you expect to join your own RTC call channel immediately, you need to set it to true.
      api.prepareForCall(prepareConfig: prepareConfig) { err in
          //Once successful, you can start making the call.
      }
    ```
- Set callback.
  - add Listener.
    ```swift
      api.addListener(listener: self)
    ```
  - Implement the protocol corresponding to CallApiListenerProtocol.
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
- Call
  - If you are the caller, call the remote user by invoking the call method.
    ```swift
      callApi.call(remoteUserId: remoteUserId) { err in
      }
    ```
  - At this point, both the caller and the callee will receive the onCallStateChanged callback with a state value of .calling, indicating a transition to the calling state.
    **`Note: When receiving the "calling" state, make sure to close any external audio or video streaming that is currently active, otherwise the call may fail.`**
      ```swift
        public func onCallStateChanged(with state: CallStateType,
                                       stateReason: CallReason,
                                       eventReason: String,
                                       elapsed: Int,
                                       eventInfo: [String : Any]) {
            let publisher = UInt(eventInfo[kPublisher] as? String ?? "") ?? currentUid
            
            // Only handle the triggered state if it belongs to oneself.
            guard publisher == currentUid else {
                return
            }
            
            if state == .calling {
                //If it is in the "calling" state.

                //The UID of the calling user.
                let fromUserId = eventInfo[kFromUserId] as? UInt ?? 0
                //The UID of the target user, which is the current user.
                let toUserId = eventInfo[kRemoteUserId] as? UInt ?? 0
            }
        }
      ```
- If the autoAccept is set to true in PrepareConfig, there is no need to explicitly call the accept method as the CallAPI will automatically accept the call. If autoAccept is set to false, the callee needs to manually accept or decline the call, while the caller can choose to cancel the call.
  ```swift
    // accept
    api.accept(remoteUserId: fromUserId) { err in
    }

    // reject
    api.reject(remoteUserId: fromUserId, reason: "reject by user") { err in
    }

    // cancel call
    api.cancelCall { err in
    }
  ```
- If the callee accepts the call, the onCallStateChanged event will first transition to the "connecting" state (state: .connecting), and once the remote video rendering completes, the state will change to "connected" (state: .connected), indicating a successful call establishment. This state transition process reflects the establishment of the call and the rendering of the video.
>
- If the callee rejects the call, onCallStateChanged will return `(state: .prepared), (stateReason: .localRejected)` (for the callee) or `(state: .prepared), (stateReason: .remoteRejected)` (for the caller).
>
- If the callee does not respond (accept or reject), onCallStateChanged will return `(state: .prepared), (stateReason: .callingTimeout)`, indicating a call timeout and failure to establish a connection.
>
- To end the call, you can invoke the hang-up function. At this point, onCallStateChanged will return `(state: .prepared), (stateReason: .localHangup)` (for the local user) or `(state: .prepared), (stateReason: .remoteHangup)` (for the remote user). This indicates that the call has been hung up and the connection is disconnected.
  ```swift
    api.hangup(remoteUserId: showUserId) { error in
    }
  ```
- Release the call cache. After releasing, you need to reinitialize.
  ```swift
    api.deinitialize()
  ```

## 5. Advanced Integration
- Use externally initialized RTM.
  ```swift
    // If an external RTM instance is already being used, you can pass in the RTM instance.
    let rtmClient:AgoraRtmClientKit? = _createRtmClient() 
    // If the external RTM client is created, ensure that it is already logged in before proceeding with further setup.
    rtmClient?.login(token) {[weak self] resp, error in
      if let error = error {return}
      //Once logged in successfully, you can initialize CallAPI
      let config = CallConfig()
      config.rtmClient = rtmClient 
      ...

      api.initialize(config: config) 
    }
  ```
  **`Note: If the RTM client is passed in from an external source, the external source needs to maintain the login state.`**

- Modify the streaming strategy for the callee to save costs.
  - Modify the corresponding code in [CallApiImpl.swift](/iOS/CallAPI/Classes/CallApiImpl.swift)(onCall->accept), change the streaming and receiving flow from `publish streaming and receiving upon call reception` to `publish streaming and receiving only after accepting the call`, in order to ensure streaming is initiated after accepting the call.
    ```swift
      //let calleeJoinRTCType: CalleeJoinRTCType = .onCall
      let calleeJoinRTCType: CalleeJoinRTCType = .accept
    ```
- Exception handling during the call.
  - During the dual-end connection process (when the state is calling/connecting/connected), you can use the `getCallId` method to obtain the call ID of the current call for both ends.
  - Through the internal log reporting of CallAPI, you can query the time consumed by each node during the call in the Agora backend. Make sure you have enabled the [Activate Agora custom data reporting and analysis services](#custom-report)。.

## 6. Sequence Diagram for Scenario Calling
### 6.1 1v1 scenario
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_pure1v1.en.png)

### 6.2 Live to 1v1 scenario
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_showto1v1.en.png)