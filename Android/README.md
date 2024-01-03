# CallAPI Example

*English | [中文](README_zh.md)*

This document mainly introduces how to quickly get through the CallAPI example project.

## Requirements
- Minimum compatibility with Android 7.0 (SDK API Level 24). 
- Android Studio 3.5 and above versions. 
- Android devices running on Android 7.0 and above.

## Getting Started

- Clone or download source code.
- Open Android Studio and use it to open the [Android](../Android) directory of the project. This way, the IDE will automatically start building the project.
- Wait for the build to complete.
- Follow [The Account Document](https://docs.agora.io/en/video-calling/reference/manage-agora-account) to get the **App ID** and **App Certificate(if enable token)**.
- **How to enable RTM**
  > To try out this service, please contact sales@agora.io

- <a id="custom-report">Enable Agora Custom Data Reporting and Analytics Service</a>
  > This service is currently in free beta testing. To try out this service, please contact sales@agora.io

- In the project root directory, locate the [gradle.properties](gradle.properties) file and fill in the Agora App ID and Certificate:
```
AG_APP_ID=
AG_APP_CERTIFICATE=
```
- In the top toolbar of Android Studio, click "File" -> select "Sync Project with Gradle Files", wait for Gradle sync to complete, then you can run and debug the project

## 3. Project Overview
### 3.1 Summary
> CallAPI is Agora's scenario-based API solution designed for one-to-one instant connection. It enables developers to achieve ultra-low latency and smooth switching in live streaming scenarios.

### 3.2 Core Features

- **Call**: The caller initiates a call.
- **Cancel Call**: The caller can cancel the call before the call is successfully established.
- **Accept Call**: The callee can accept the call request from the caller.
- **Reject Call**: The callee can reject the call request from the caller.
- **Hang Up**: Both the caller and callee can initiate a request to hang up the current call.

### 3.3 Gameplay Explanation
- 1v1 Scenario:
  > In a social scenario with strangers, users can filter and find other users of interest based on photos and personal profiles. They can also randomly match with other users based on geographical location or tags. This allows two users to have a private one-on-one video call. By default, both users will have their cameras and microphones enabled, and they can send and receive audio and video streams bidirectionally.
- Showroom to 1v1 Scenario:
  > During a live broadcast, viewers can pay to initiate a one-on-one video call. Once the call is connected, the original live broadcast of the host will not be closed, but it will stop streaming. The host will switch to a one-on-one video call with the paid user. After the one-on-one call ends, the host will switch back to the original live broadcast and continue streaming.

### 3.4 Role Introduction
- Caller:
  > The caller is the one who initiates the call and invites the other party to join the call. The caller initiates the call request, establishes the video call connection, and sends an invitation to the callee.
- Callee:
  > The callee is the one who receives the call request and is invited to join the call. The callee can accept or reject the call request from the caller. If the callee accepts the call, a video call connection is established with the caller.

### 3.5 Optimizing Call Performance and Reliability
#### 3.5.1 Accelerating Rendering Speed
- 1.Use [Wildcard Tokens](https://doc.shengwang.cn/doc/rtc/ios/best-practice/wildcard-token)
  - To improve call quality and stability, we recommend using wildcard tokens. This eliminates the need to generate different tokens for joining different channels, saving time and effort. By using a single fixed token, you can focus more on the content of the call instead of token management.
- 2.Accelerating Caller's Rendering Speed
  - 2.1 **`[Optional]`** When initializing, you can join your own RTC channel in advance.**`Keep in mind that this action may incur additional costs. If you are concerned about expenses, you can skip this step`**.
  - 2.2 When initiating a call, you need to join your own RTC channel, send audio/video streams, and subscribe to the remote video stream. To avoid missing the first I-frame and potential slow rendering of the first frame, create a temporary canvas and use the `setupRemoteVideoEx` method to render the callee's video stream onto the canvas.
  - 2.3 After receiving acceptance from the callee, start subscribing to the remote audio stream.
  - 2.4 Once you receive the callee's first frame and their consent, you can consider the connection successful. At this point, add the previously created temporary canvas to the view to complete the video rendering.
- 3.Accelerating Callee's Rendering Speed
  - 3.1 **`[Optional][Default]`** When receiving a call, immediately join the caller's RTC channel, push audio/video streams, and subscribe to the video stream while creating a temporary canvas. Use the`setupRemoteVideoEx` method to render the caller's video stream onto the canvas. This ensures that the first I-frame is not missed and avoids potential slow rendering of the first frame.**`Note that this behavior is the default in CallAPI and incurs additional costs. If you are sensitive to the expenses, we recommend modifying internal parameters to delay triggering this behavior. This allows better cost control and adjustment based on your specific needs`**.
  - 3.2 When accepting the call:
    - 3.2.1 If you haven't executed **`[Step 3.1]`** when receiving the call, perform it at this point.
    - 3.2.2 Start subscribing to the remote audio stream.
  - 3.3 Once you receive the first frame from the caller, you can confirm the successful connection. Add the previously created temporary canvas to the visual view to complete the video rendering process.

#### 3.5.2 Improving Message Delivery Rate
- Implement message acknowledgments (if not already available in the signaling channel).
- Add timeout and retry mechanism for message delivery (if not already available in the signaling channel).
- Choose a signaling channel with high message delivery rate, such as Agora RTM.
#### 3.5.3 Enhancing Security
- To ensure the privacy and security of calls, you can pre-allocate multiple channel IDs and use different channels for each call to maintain the confidentiality of call content.
- Adopt a multi-platform billing strategy to ensure accurate and secure billing for calls.

#### 3.5.4 Sequence Diagram
- 
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_solution_1v1.en.png){width=600px}

### 3.6 Indicators for Measuring Call Speed
- Caller:
  - Call-to-receiver time
  - Call-to-receiver-acceptance time
  - Call-to-receiver-joining-channel time
  - Call-to-caller-joining-channel time
  - Call-to-receiver-first-frame-arrival time
- Receiver:
  - Receiver-receiving-call-to-acceptance time
  - Receiver-receiving-call-to-self-joining-channel time
  - Receiver-receiving-call-to-caller-joining-channel time
  - Receiver-receiving-call-to-receiving-first-frame time

## 4. Quick Integration
- Copy[lib_callapi/src/main/java/io/agora/onetoone](lib_callapi/src/main/java/io/agora/onetoone)to your own project.

- Make sure you use the correct Agora SDK dependencies in your project, ensuring they do not conflict with CallApi:
  - 'io.agora:agora-rtm:2.1.8'
  - 'io.agora.rtc:agora-special-full:4.1.1.17'

- In Android Studio, click "File" in the top toolbar, then select "Sync Project With Gradle File" to integrate the CallAPI code into your project.
- Initialize the settings.
  - Create an instance of CallAPI.
    ```kotlin
      val api = CallApiImpl(this)
    ```
  - Initialize CallApi.
    ```kotlin
       // Initialize config
       val config = CallConfig(
           appId = BuildConfig.AG_APP_ID,
           userId = enterModel.currentUid.toInt(),
           rtcEngine = rtcEngine,
           rtmClient = rtmClient, // If available, pass it, otherwise set to null
       )
       api.initialize(config)
    ```
  - Prepare the call environment.
    ```kotlin   
      this.api.initialize(config) 
      val prepareConfig = PrepareConfig()
      prepareConfig.rtcToken = ...   // Set rtc token (universal token)
      prepareConfig.rtmToken = ...   // Set rtm token
      prepareConfig.roomId = enterModel.currentUid
      prepareConfig.localView = mViewBinding.vRight
      prepareConfig.remoteView = mViewBinding.vLeft
      prepareConfig.autoAccept = false  // If you want the call to be automatically accepted, set it to true
      prepareConfig.autoJoinRTC = false  // If you want to immediately join your own RTC call channel, set it to true
      api.prepareForCall(prepareConfig: prepareConfig) { err ->
          // Once successful, you can start making calls
      }
    ```
- Set up callbacks.
  - Set up listeners.
    ```kotlin
      api.addListener(this)
    ```
  - Implement the protocol corresponding to ICallApiListener.
    ```kotlin
      override fun onCallStateChanged(
            state: CallStateType,
            stateReason: CallReason,
            eventReason: String,
            eventInfo: Map<String, Any>
      ) {}

      override fun onCallEventChanged(event: CallEvent) {
        
      }
    ```
- Make a call
  - If you are the caller, call the remote user using the `call` method.
    ```kotlin
      callApi.call(remoteUserId) { err ->
      }
    ```
  - At this point, both the caller and the callee will receive the onCallStateChanged callback with `state == CallStateType.Calling`, indicating that the call is in progress.
    **`Note: When receiving the Calling state, make sure to close any ongoing audio/video streaming externally, otherwise the call will fail.`**
      ```kotlin
        override fun onCallStateChanged(
            state: CallStateType,
            stateReason: CallReason,
            eventReason: String,
            eventInfo: Map<String, Any>
        ) {
            val publisher = eventInfo.getOrDefault(CallApiImpl.kPublisher, enterModel.currentUid)
            if (publisher != enterModel.currentUid) {
                return
            }
            if (state == CallStateType.Calling) {
                // If the call is in progress
                // UID of the calling user
                val fromUserId = eventInfo[CallApiImpl.kFromUserId] as? Int ?: 0
                // UID of the target user, which is the current user
                val toUserId = eventInfo[CallApiImpl.kRemoteUserId] as? Int ?: 0
            }
        }
      ```
- If `autoAccept` is set to `true` in `PrepareConfig`, then there is no need to explicitly call the `accept` method. CallApi will automatically accept the call. If `autoAccept` is set to `false`, then the callee needs to manually accept or reject the call, and the caller can choose to cancel the call.
  ```kotlin
    // Accept the call
    api.accept(remoteUserId) { err ->
    }

    // Reject the call
    api.reject(remoteUserId, "reject by user") { err ->
    }

    // Cancel the call
    api.cancelCall { err ->
    }
  ```
- If the callee accepts the call, the `onCallStateChanged` event will first switch to the `connecting` state, and after the remote video rendering is complete, the state will change to `connected`, indicating that the call was successful. This state change process reflects the establishment of the call and the rendering of the video.
- If the callee rejects the call, `onCallStateChanged` will return `(state: .prepared), (stateReason: .localRejected)` (if the callee rejects the call) or `(stateReason: .remoteRejected)` (if the caller cancels the call).
- If the callee does not respond (accept or reject), `onCallStateChanged` will return `(state: .prepared), (stateReason: .callingTimeout)`. This indicates that the call timed out and the connection was not established successfully.
- To end the call, you can call the `hangup` function. At this point, `onCallStateChanged` will return `(state: .prepared), (stateReason: .localHangup)` (if the local user hangs up) or `(stateReason: .remoteHangup)` (if the remote user hangs up). This indicates that the call has been hung up and the connection has been disconnected.
  ```kotlin
    api.hangup(remoteUserId) { error ->
    }
  ```
- To release the call buffer, call `deinitialize`. After releasing the buffer, you need to reinitialize it.
  ```kotlin
    api.deinitialize()
  ```
## 5. Advanced Integration
- Using externally initialized RTM.
  ```kotlin
    //If the RTM has already been created externally, you can pass in the RTM instance
    val rtmClient: RtmClient? = _createRtmClient()
    //If the rtmClient has been created externally, make sure it is in a logged-in state before proceeding with any subsequent settings
    rtmClient?.login(token, object: ResultCallback<Void?> {
        override fun onSuccess(p0: Void?) {
            //Once logged in, CallAPI can be initialized
            val config = CallConfig()
            config.rtmClient = rtmClient 
            //...
            api.initialize(config) 
        }
        override fun onFailure(p0: ErrorInfo?) {
            Log.e(TAG, "login error = ${p0.toString()}") 
        }
    })
  ```
  **`Note: If rtmClient is passed in externally, the external party needs to maintain the login status`**

- Modify callee streaming strategy to save costs.
  - Modify the corresponding code (OnCall -> Accept) in[CallApiImpl.kt](lib_callapi/src/main/java/io/agora/onetoone/CallApiImpl.kt)to start streaming and receiving streams after accepting the call, instead of streaming and receiving streams as soon as the call is received, to ensure that streaming starts after accepting the call.
    ```kotlin
      //val calleeJoinRTCType = CalleeJoinRTCType.OnCall
      val calleeJoinRTCType = CalleeJoinRTCType.Accept
    ```
- Troubleshoot call exceptions.
  - During the two-way connection process (when state is calling/connecting/connected), you can use the getCallId method to obtain the call ID for both ends of the current call.
  - Through the CallAPI internal log reporting, you can query the time consumed by each node during the current call through Agora's backend. Please make sure that the[Agora custom data reporting and analysis service ](#custom-report)has been enabled.

## 6. Scenario Invocation Sequence Diagram
### 6.1 1v1 Scenario
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_pure1v1.en.png){width=600px}

### 6.2 Live Broadcasting to 1v1 Scenario
![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_showto1v1.en.png){width=600px}