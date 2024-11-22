# CallAPI Example

*English | [中文](README.zh.md)* 

This document mainly describes how to quickly run the CallAPI sample project.

- [CallAPI Example](#callapi-example)
  - [1. Enable Service](#1-enable-service)
  - [2. Run the Example](#2-run-the-example)
  - [3. Project Introduction](#3-project-introduction)
    - [3.1 Overview](#31-overview)
    - [3.2 Role Introduction](#32-role-introduction)
    - [3.3 Core Capabilities](#33-core-capabilities)
    - [3.4 Gameplay Introduction](#34-gameplay-introduction)
  - [4. Quick Integration](#4-quick-integration)
    - [Add dependencies](#add-dependencies)
    - [Implement a 1v1 call](#implement-a-1v1-call)
      - [Initialize CallRtmManager](#initialize-callrtmmanager)
      - [Add and listen for CallRtmManager state callbacks.](#add-and-listen-for-callrtmmanager-state-callbacks)
      - [Initialize CallRtmSignalClient](#initialize-callrtmsignalclient)
      - [Initialize CallAPI](#initialize-callapi)
      - [Add and listen for CallAPI callbacks.](#add-and-listen-for-callapi-callbacks)
      - [Handle CallRtmManager login](#handle-callrtmmanager-login)
      - [prepare call environment](#prepare-call-environment)
      - [Caller makes a call](#caller-makes-a-call)
      - [listens for call events and processes](#listens-for-call-events-and-processes)
      - [Receives call success](#receives-call-success)
      - [Ends call](#ends-call)
      - [Receives hang-up message](#receives-hang-up-message)
      - [Updates channel id](#updates-channel-id)
      - [Leaves and releases resources](#leaves-and-releases-resources)
    - [Sequence diagram for calling CallAPI scenarios:](#sequence-diagram-for-calling-callapi-scenarios)
  - [5. Advanced Integration](#5-advanced-integration)
    - [5.1 Using an Initialized rtmClient](#51-using-an-initialized-rtmclient)
    - [5.2 Switching the Timing of Publishing and Subscribing for the Callee to Save Costs](#52-switching-the-timing-of-publishing-and-subscribing-for-the-callee-to-save-costs)
    - [5.3 Manually Enable and Disable Audio and Video Stream Publishing](#53-manually-enable-and-disable-audio-and-video-stream-publishing)
    - [5.4 Carry Custom Data Structure in the Message](#54-carry-custom-data-structure-in-the-message)
    - [5.5 Call Exception Diagnosis](#55-call-exception-diagnosis)
    - [5.6 Listen to RTC Call Channel Callbacks](#56-listen-to-rtc-call-channel-callbacks)
    - [5.6 Updating Expired Tokens](#56-updating-expired-tokens)
  - [6. API Reference](#6-api-reference)
  - [7. Implementation Principles](#7-implementation-principles)
    - [7.1 Optimizing Call Performance and Reliability](#71-optimizing-call-performance-and-reliability)
      - [7.1.1 Accelerating Rendering Speed](#711-accelerating-rendering-speed)
      - [7.1.2 Improve Message Delivery Rate](#712-improve-message-delivery-rate)
    - [7.2 Metrics Affecting Call Speed](#72-metrics-affecting-call-speed)

## 1. Enable Service
- Follow [the Account Document](https://docs.agora.io/en/video-calling/reference/manage-agora-account) to Access the **App ID** and **App Certificate**.
- Follow Signaling(Agora Rtm) Beginner's guide to enable signaling in Agora Console. 
- [Options]Enable and Configure [Agora Chat](https://docs.agora.io/en/agora-chat/get-started/enable?platform=ios) to Access Your **AppKey**.

## 2. Run the Example

- Environment Preparation
  - Minimum compatibility: Android 7.0 (SDK API Level 24)
  - Android Studio version 3.5 and above.
  - Mobile devices running Android 7.0 and above.
- Clone or directly download the project source code.
- Open Android Studio and use it to open the project's [Android](../Android) directory. The IDE will automatically start building the project.
- In the project root directory, find [gradle.properties](gradle.properties) and fill in the Agora App ID and Certificate, as well as the Easemob App Key (if you do not need to experience the Easemob custom signaling process, `IM_APP_KEY` can be set to `""`).
  ```
  AG_APP_ID=
  AG_APP_CERTIFICATE=
  IM_APP_KEY=
  ```
- In the top toolbar of Android Studio, click on "File" -> select "Sync Project With Gradle File". Wait for the Gradle sync to complete, and then you can run the project and debug it.

## 3. Project Introduction
### 3.1 Overview
1v1 Private Room is Agora's 1v1 video social scenario solution. It integrates Agora's RTC and RTM SDK products and capabilities to ensure optimal performance, such as smoothness, high definition, and instant connection for 1v1 interactions. This integration significantly lowers the development threshold and supports rapid deployment.
>  CallApi is an open-source call invitation module that focuses purely on business logic, allowing you to customize and modify it freely without restricting your business processes.
>  
>  CallApi does not involve any UI, enabling you to flexibly customize the UI according to your needs.
>

### 3.2 Role Introduction
- Caller: The party that initiates the call and invites the other party to join the conversation. The caller actively sends a call request to establish a video call connection and sends an invitation to the callee.
- Callee: The party that receives the call request and is invited to join the conversation. Upon receiving the caller's invitation, the callee can either accept or decline the call. If the call is accepted, a video call connection is established with the caller.

### 3.3 Core Capabilities
- **Call**: The caller initiates a call.
- **Cancel Call**: The caller can cancel the call to interrupt the current call before it is successfully connected.
- **Accept Call**: The callee can accept the call after receiving the caller's request.
- **Reject Call**: The callee can decline the call after receiving the caller's request.
- **Hang Up**: Either the caller or callee can initiate a hang-up request to end the current call during the conversation.

### 3.4 Gameplay Introduction
- 1v1 Scenario: Typically in stranger social scenarios, users can filter potential matches based on photos and personal profiles, or randomly match with other users through location and tags, allowing two users to engage in a private 1v1 video call. During the call, both users are defaulted to have their cameras and microphones on, sending and receiving audio and video streams bidirectionally.
- Showroom to 1v1 Scenario: During a live broadcast, users can pay to initiate a 1v1 video call with the host. Once the call is connected, the host's original live stream does not close but stops broadcasting, allowing the host to transition to the 1v1 video call with the paying user. After the 1v1 video call ends, the host transitions back to the original live stream to continue broadcasting.


## 4. Quick Integration
### Add dependencies
  - Copy [lib_callapi/src/main/java/io/agora/onetoone](lib_callapi/src/main/java/io/agora/onetoone) to your project.

  - Please ensure that the project uses Agora SDK dependencies that are not lower than the following versions, and that there are no conflicts with CallApi dependencies:
    - `'io.agora:agora-rtm:2.2.0'`
    - `'io.agora.rtc:agora-special-full:4.1.1.26'`

- In the top toolbar of Android Studio, click “File” -> select “Sync Project With Gradle Files” to integrate the CallAPI code into your project.

### Implement a 1v1 call
#### Initialize CallRtmManager
  ```kotlin
  val rtmManager = CallRtmManager(appId = "Your AppId",
                              userId = "Local user UserId",
                              client = null)  
  ```
#### Add and listen for CallRtmManager state callbacks.
  ```kotlin
rtmManager?.addListener(object : ICallRtmManagerListener {
    override fun onConnected() {
        // Network connected, signaling can be sent and received normally.
    }

    override fun onDisconnected() {
        // Network not connected, signaling cannot be sent or received at this time; the business layer can handle exceptions based on the current status.
    }

    override fun onTokenPrivilegeWillExpire(channelName: String) {
        // Token expired, need to refresh the RTM Token.
    }
})
  ```

#### Initialize CallRtmSignalClient
  ```kotlin
  val signalClient = createRtmSignalClient(rtmManager.getRtmClient())
  ```

#### Initialize CallAPI
  ```kotlin
val config = CallConfig(
    appId = "Your AppId",
    userId = "Local user UserId",
    rtcEngine = rtcEngine,
    signalClient = signalClient
)
api.initialize(config)
  ```

#### Add and listen for CallAPI callbacks.

  ```kotlin
api.addListener(this)

override fun onCallStateChanged(
    state: CallStateType,
    stateReason: CallStateReason,
    eventReason: String,
    eventInfo: Map<String, Any>
) {
}
  ```

#### Handle CallRtmManager login
  ```kotlin
rtmManager?.login(enterModel.rtmToken) {
    if (it == null) {
        // Initialize call api after successful login
    }
}
  ```

#### prepare call environment

  ```kotlin
// Prepare the call environment
var prepareConfig = PrepareConfig()
prepareConfig.rtcToken = "${Universal RTC Token}"
prepareConfig.roomId = "${Channel ID to call}"
prepareConfig.localView = callVC.localCanvasView.canvasView
prepareConfig.remoteView = callVC.remoteCanvasView.canvasView
prepareConfig.userExtension = null

api.prepareForCall(prepareConfig) { error ->
    completion.invoke(error == null)
}
  ```
    
#### Caller makes a call
  - Video call
    ```kotlin
    api.call(targetUserId) { error ->
        // Call fails, hang up immediately
        if (error != null && mCallState == CallStateType.Calling) {
            api.cancelCall {  }
        }
    }
      ```
  - Audio call
    ```kotlin
    api.call(targetUserId, CallType.Audio) { error ->
        // Call fails, hang up immediately
        if (error != null && mCallState == CallStateType.Calling) {
            api.cancelCall {  }
        }
    }
    ```
#### listens for call events and processes
  ```kotlin
override fun onCallStateChanged(
    state: CallStateType,
    stateReason: CallStateReason,
    eventReason: String,
    eventInfo: Map<String, Any>
) {
    runOnUiThread {
        val publisher = eventInfo.getOrDefault(CallApiImpl.kPublisher, enterModel.currentUid)
        if (publisher != enterModel.currentUid) {
            return@runOnUiThread
        }
        updateCallState(state, stateReason)

        when (state) {
            CallStateType.Calling -> {
                val fromUserId = eventInfo[CallApiImpl.kFromUserId] as? Int ?: 0
                val fromRoomId = eventInfo[CallApiImpl.kFromRoomId] as? String ?: ""
                val toUserId = eventInfo[CallApiImpl.kRemoteUserId] as? Int ?: 0
                if (connectedUserId != null && connectedUserId != fromUserId) {
                    api.reject(fromUserId, "already calling") {
                    }
                    return@runOnUiThread
                }
                // Only handle if target user is self
                if (enterModel.currentUid.toIntOrNull() == toUserId) {
                    connectedUserId = fromUserId
                    connectedChannel = fromRoomId
                    callDialog = AlertDialog.Builder(this)
                        .setTitle(getString(R.string.alert_title))
                        .setMessage(getString(R.string.alert_incoming_call, fromUserId))
                        .setPositiveButton(getString(R.string.alert_accept)) { p0, p1 ->
                            // Check signal channel connection status
                            if (!checkConnectionAndNotify()) return@setPositiveButton
                            api.accept(fromUserId) { err ->
                                if (err != null) {
                                    // If accept message fails, reject and return to initial state
                                    api.reject(fromUserId, err.msg) {}
                                }
                            }
                        }.setNegativeButton(getString(R.string.alert_reject)) { p0, p1 ->
                            // Check signal channel connection status
                            if (!checkConnectionAndNotify()) return@setNegativeButton
                            api.reject(fromUserId, "reject by user") { err ->
                            }
                        }.create()
                    callDialog?.setCancelable(false)
                    callDialog?.show()
                } else if (enterModel.currentUid.toIntOrNull() == fromUserId) {
                    connectedUserId = toUserId
                    connectedChannel = fromRoomId
                    callDialog = AlertDialog.Builder(this)
                        .setTitle(getString(R.string.alert_title))
                        .setMessage(getString(R.string.alert_calling_user, toUserId))
                        .setNegativeButton(getString(R.string.alert_cancel)) { p0, p1 ->
                            // Check signal channel connection status
                            if (!checkConnectionAndNotify()) return@setNegativeButton
                            api.cancelCall { err ->
                            }
                        }.create()
                    callDialog?.setCancelable(false)
                    callDialog?.show()
                }
            }
            else -> {}
        }
    }
  }
  ```
#### Receives call success
  ```kotlin
override fun onCallStateChanged(
    state: CallStateType,
    stateReason: CallStateReason,
    eventReason: String,
    eventInfo: Map<String, Any>
) {
    when (state) {
        CallStateType.Connected -> {
            // Display the call page.
        } else -> {}
    }
  }
  ```
#### Ends call

  ```kotlin
private fun hangupAction() { 
    // Check signal channel connection status
    api.hangup(connectedUserId ?: 0, "hangup by user") {
    }
}
  ```

#### Receives hang-up message
  ```kotlin
  override fun onCallStateChanged(
    state: CallStateType,
    stateReason: CallStateReason,
    eventReason: String,
    eventInfo: Map<String, Any>
) {
    when (state) {
        CallStateType.Prepared -> {
            when (stateReason) {
                CallStateReason.LocalHangup, CallStateReason.RemoteHangup -> {
                    // Remove the call page
                    // Display rejection information
                }
                else -> {}
            }
        } else -> {}
    }
  }
  ```

#### Updates channel id
  ```kotlin
  // Prepare the call environment
  val prepareConfig = PrepareConfig()
  // Set the new channel ID
  prepareConfig.roomId = "${Channel ID to call}"
  // Other property settings are omitted here. Please ensure to synchronize the settings for other properties.
  callApi.prepareForCall(prepareConfig) { err ->
      // Success means you can start making the call
  }
  ```

#### Leaves and releases resources
  ```kotlin
  // Clear CallAPI cache
  callApi.deinitialize {
      // Destroy RTC instance
      RtcEngine.destroy()
      // Logout from RTM service
      rtmManager?.logout()
      // Other business logic
  }
  ```

### Sequence diagram for calling CallAPI scenarios:
  - 1v1 scenario
   <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_pure1v1.en.png" width="500px"><br><br>
  - Show to 1v1
    <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/100/sequence_showto1v1.en.png" width="500px"><br><br>


## 5. Advanced Integration
### 5.1 Using an Initialized rtmClient
Agora's CallAPI for 1v1 private rooms has already implemented the necessary encapsulation for RTM services. If your project already has an RTM instance (rtmClient) before integrating the 1v1 private room, you can directly use the initialized RTM instance and then call the relevant functionalities.

> If you use your own created `rtmClient` instance, you can manage the RTM login state yourself; you can also manage login and logout using the `login` and `logout` methods provided in our `CallRtmManager`.

```kotlin
val rtmConfig = RtmConfig.Builder(appId, userId.toString()).build()
val rtmClient = RtmClient.create(rtmConfig)
rtmClient.login(token, object : ResultCallback<Void?> {
    override fun onSuccess(p0: Void?) {
        // Login successful, you can initialize CallRtmManager, CallRtmSignalClient, and CallApi
    }

    override fun onFailure(p0: ErrorInfo?) {
        // Login failed
    }
})
```

### 5.2 Switching the Timing of Publishing and Subscribing for the Callee to Save Costs
- There are two timing options for the callee's publishing and subscribing after receiving a call:
  - Automatically publish audio and video streams and subscribe to the video stream upon receiving the call, which is the default behavior.
  - Publish audio and video streams and subscribe to the video stream only after the callee accepts the call.
- You can set the timing for publishing and subscribing through the optional callback canJoinRtcOnCalling in the CallApiListenerProtocol:
  - If it returns true or the callback method is not implemented, the default streaming strategy will be used, meaning audio and video streams will be published and subscribed to upon receiving the call.
  - If it returns false, the strategy will be to publish audio and video streams and subscribe to the video stream only after accepting the call.
  
```kotlin
/**
  * Determine if it is possible to join Rtc during a call.
  * @param eventInfo Extended information received during the call.
  * @return true: can join, false: cannot join.
  */
fun canJoinRtcOnCalling(eventInfo: Map<String, Any>): Boolean?
```
### 5.3 Manually Enable and Disable Audio and Video Stream Publishing
Since the `CallAPI` internally starts audio and video capture during a call and stops it when the call ends, if external manual activation of audio and video capture is needed after ending the call (for example, when `onCallStateChanged` returns `(state: prepared))`, you can enable the capture.

```kotlin
rtcEngine.enableLocalAudio(true)
rtcEngine.enableLocalVideo(true)
```

### 5.4 Carry Custom Data Structure in the Message
By setting parameters in the `userExtension` attribute of the `PrepareConfig`, you can attach additional user extension information when sending messages to the other party (such as for call, cancel call, agree, reject, etc.). The other party can receive this `userExtension` through callback messages to obtain relevant additional information when processing the message.

```kotlin
override fun onCallStateChanged(
    state: CallStateType,
    stateReason: CallStateReason,
    eventReason: String,
    eventInfo: Map<String, Any>
) {
    val userExtension = eventInfo[CallApiImpl.kFromUserExtension] as? Map<String, Any>
    ...
}
```

### 5.5 Call Exception Diagnosis
During the connection process on both ends (when the state is calling/connecting/connected), you can obtain the call ID for the current call on both ends using the getCallId method.

You can also query the duration of various nodes for the current call through the internal log reporting of the CallAPI in the Agora backend. If you need to use this, you can contact sales@agora.io to apply for the Agora custom data reporting and analysis service.

### 5.6 Listen to RTC Call Channel Callbacks
To better understand the current state and events of the call channel, you can also listen to the RTC channel callbacks. Since the `joinChannelEx` method is used to join the RTC channel in the `CallAPI`, you cannot use the `rtcEngine.addHandler` method. Instead, you need to use `rtcEngine.addHandlerEx` and specify the corresponding channel to add the `delegate`:

```kotlin
// The channel ID of the call can be saved when the call state is received.
override fun onCallStateChanged(
        state: CallStateType,
        stateReason: CallStateReason,
        eventReason: String,
        eventInfo: Map<String, Any>
    ) {
    when (state) {
        CallStateType.Calling -> {
          roomId = eventInfo[kFromRoomId] as? String ?: ""
        }
        else -> {}
    }
}

// After receiving the joinRTCStart event, set the listener using the channel ID.
override fun onCallEventChanged(event: CallEvent, eventReason: String?) {
    when(event) {
        CallEvent.JoinRTCStart -> {
            rtcEngine.addHandlerEx(
                object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(
                        channel: String?,
                        uid: Int,
                        elapsed: Int
                    ) {
                        super.onJoinChannelSuccess(channel, uid, elapsed)
                        // ...
                    }
                },
                RtcConnection(roomId, currentUid.toInt()) // For convenience in this demo, the local UID's string is used as the channel name.
            )
        }
        else -> {}
    }
}

```
The ID of the current RTC call channel (the `roomId` parameter) can be parsed from `eventInfo` when the state is `calling` in the `onCallStateChanged` method.
> **Note:** 
> You need to ensure that the joinRTCStart event has been triggered before joining; calling rtcEngine.addHandlerEx before this event will be ineffective.


### 5.6 Updating Expired Tokens
You can update the expired tokens for signaling and RTC in the following way.

**Signaling Token**
1. Monitor whether the RTM token has expired. You can do this by adding and listening to the CallRtmManager state callback, specifically the `onTokenPrivilegeWillExpire` method of ICallRtmManagerListener``.
   ```kotlin
   val listener = object: ICallRtmManagerListener {
      override fun onTokenPrivilegeWillExpire(channelName: String) {
        // The token is about to expire; a new token needs to be obtained.
      }
   }
   ```
2. Update Token。
   ```kotlin
   // Update RTC Token
   this.api.renewToken(rtcToken)

   // Update RTM Token
   this.rtmManager?.renewToken(rtmToken)

   // To ensure that both RTM and RTC Tokens are valid simultaneously, it is recommended to update both tokens at the same time.
   ```

**RTC Token**
1. Monitor whether the RTC Token has expired.
   - Listen for expired tokens during a call.
   ```kotlin
   val listener = object: ICallApiListener {
       override fun tokenPrivilegeWillExpire() {
       // The token is about to expire; need to retrieve the token again
       }
   }
   ```
   - Monitor for expired tokens before starting a call.
   ```kotlin
   val listener = ICallApiListener {
       override fun onCallError(errorEvent: CallErrorEvent,
                  errorType: CallErrorCodeType,
                  errorCode: Int,
                  message: String?) {
          if (errorEvent == CallErrorEvent.RtcOccurError && errorType == CallErrorCodeType.Rtc && errorCode == ErrorCode.TokenExpired.value {
              // Failed to join the RTC channel; need to cancel the call and retrieve the Token again
              this.api.cancelCall { err -> }
          }
       }
   }
   ```

2. Update Token.
   ```kotlin
   // Update RTC Token
   this.api.renewToken(rtcToken)

   // Update RTM Token
   this.rtmManager?.renewToken(rtmToken)

   // To ensure that both RTM and RTC Tokens are valid simultaneously, it is recommended to update both tokens at the same time.
   ```

## 6. API Reference
- Refer to the [link](./lib_callapi/src/main/java/io/agora/onetoone/CallApiProtocol.kt) to review the API for CallAPI.
- Refer to the [link](./lib_callapi/src/main/java/io/agora/onetoone/signalClient/ISignalClient.kt) to review the API for SignalClient.

## 7. Implementation Principles
### 7.1 Optimizing Call Performance and Reliability
#### 7.1.1 Accelerating Rendering Speed
  - 1. Use [Wildcard Token](https://doc.shengwang.cn/doc/rtc/ios/best-practice/wildcard-token)
    - To improve call quality and stability, we use a wildcard token, which saves time spent obtaining tokens for joining different channels. This means that when using our service, you do not need to frequently obtain tokens; you only need to use a fixed token. This not only improves your efficiency but also allows you to focus more on the content of the call itself.
    - > Note: To ensure the privacy and security of calls, it is recommended to use **different RTC channel numbers** for each call.
  - 2. Accelerate the caller's rendering speed
    - 2.1 **`[Optional]`** During initialization, you can join your RTC channel in advance. **`Please note that this behavior may incur additional costs. If you are concerned about costs, you may choose to skip this step.`**
    - 2.2 When initiating a call to the callee
      - 2.2.1 Join your RTC channel.
      - 2.2.2 Send audio and video streams to your RTC channel.
      - 2.2.3 Subscribe to the remote video stream, and do not subscribe to the audio stream.
      - 2.2.4 At the same time, to avoid missing the first I-frame decoding, which may cause slow rendering of the first frame, you need to create a temporary canvas and use the `setupRemoteVideoEx` method to render the callee's video stream on that canvas.
    - 2.3 After receiving the callee's acceptance message, start subscribing to the remote audio stream.
    - 2.4 When the first frame from the callee is received and the callee's agreement is confirmed, the connection can be considered successful. At this point, you can add the previously created temporary canvas to the view to complete the video rendering.
  - 3. Accelerate the callee's rendering speed
    - 3.1 **`[Optional][Recommended]`** After receiving the caller's call
      - 3.1.1 Immediately join the caller's RTC channel.
      - 3.1.2 Push audio and video streams to the caller's RTC channel.
      - 3.1.3 Then subscribe to the remote video stream, and do not subscribe to the audio stream.
      - 3.1.4 At the same time, to avoid missing the first I-frame decoding, which may cause slow rendering of the first frame, you need to create a temporary canvas and use the `setupRemoteVideoEx` method to render the caller's video stream on that canvas.
    **`Please note that [Step 3.1] may incur additional costs. If you are sensitive to costs, you may choose to skip this step.`**
    - 3.2 After clicking accept
      - 3.2.1 If [Step 3.1] was not executed when receiving the call, then you need to execute [Step 3.1] here.
      - 3.2.2 Start subscribing to the remote audio stream.
    - 3.3 After receiving the first frame from the caller, the connection can be confirmed as successful. At this point, you can add the previously created temporary canvas to the visual view, thus completing the video rendering process.
  - 4. Sequence Diagram
      <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/200/sequence_solution_1v1.en.png" width="500px"><br><br>
  
#### 7.1.2 Improve Message Delivery Rate
  - Add message acknowledgment (ignore if signaling channel is available)
  - Add timeout retries (ignore if signaling channel is available)
  - Select signaling channels with higher delivery rates, such as Agora RTM

### 7.2 Metrics Affecting Call Speed
  - Caller
    - Time taken from call initiation to the callee receiving the call
    - Time taken from call initiation to receiving the callee's acceptance
    - Time taken from call initiation to the callee joining the channel
    - Time taken from call initiation to the caller joining the channel
    - Time taken from call initiation to receiving the first frame from the callee
  - Callee
    - Time taken from receiving the call to accepting the call
    - Time taken from receiving the call to the callee joining the channel
    - Time taken from receiving the call to the caller joining the channel
    - Time taken from receiving the call to receiving the first frame from the caller
