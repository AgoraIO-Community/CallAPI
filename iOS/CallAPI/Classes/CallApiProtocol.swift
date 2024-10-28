//
//  CallApiProtocol.swift
//  CallAPI
//
//  Created by Agora on 2023/5/18.
//

import Foundation
import AgoraRtcKit

/// Initializes configuration information
@objcMembers public class CallConfig: NSObject {
    public var appId: String = ""                          // Agora App Id
    public var userId: UInt = 0                            // User ID
    public var rtcEngine: AgoraRtcEngineKit!               // RTC engine instance
    public var signalClient: ISignalClient!                // Signaling channel object instance
}

@objcMembers public class PrepareConfig: NSObject {
    public var roomId: String = ""                      // Own RTC channel name, used to let the remote user join this RTC channel when calling
    public var rtcToken: String = ""                    // RTC token, needs to use a universal token, the channel name is empty when the token is created
    public var localView: UIView!                       // Canvas for displaying local stream
    public var remoteView: UIView!                      // Canvas for displaying remote stream
    public var callTimeoutMillisecond: UInt64 = 15000   // Call timeout duration in milliseconds, 0 means no internal timeout handling
    public var userExtension: [String: Any]?            // [Optional] User extension fields, can be used to retrieve the kFromUserExtension field when the state changes due to receiving a message from the remote user (e.g., calling/connecting)
    public var firstFrameWaittingDisabled: Bool = false  // Whether to disable waiting for the first frame in connected state, true: yes, the caller considers the call successful upon receiving the acceptance message, the callee clicking accept also considers the call successful. Note that using this method may allow the call to connect without audio/video permissions, and during connection, due to weak network conditions, the screen may not be visible. false: no, will wait for the first audio frame (audio call) or the first video frame (video call)
}

/// Call types
@objc public enum CallType: UInt {
    case video = 0    // Video call
    case audio        // Audio call
}

/// Call states
@objc public enum CallStateType: UInt {
    case idle = 0            // Unknown
    case prepared = 1        // Idle
    case calling = 2         // Calling
    case connecting = 3      // Connecting
    case connected = 4       // In call
    case failed = 10         // Error occurred
}

/// Reasons for call state transitions
@objc public enum CallStateReason: UInt {
    case none = 0
    case joinRTCFailed = 1         // Failed to join RTC
    case messageFailed = 4         // Message sending failed
    case localRejected = 5         // Local user rejected
    case remoteRejected = 6        // Remote user rejected
    case remoteAccepted = 7        // Remote user accepted
    case localAccepted = 8         // Local user accepted
    case localHangup = 9           // Local user hung up
    case remoteHangup = 10         // Remote user hung up
    case localCancelled = 11       // Local user canceled the call
    case remoteCancelled = 12      // Remote user canceled the call
    case recvRemoteFirstFrame = 13 // Received remote first frame
    case callingTimeout = 14       // Local call timed out
    case cancelByCallerRecall = 15 // The same caller calling different channels leads to cancellation
    case remoteCallBusy = 17       // Remote user is busy
    case remoteCallingTimeout = 18 // Remote call timed out
    case localVideoCall = 30       // Local initiated video call
    case localAudioCall = 31       // Local initiated audio call
    case remoteVideoCall = 32      // Remote initiated video call
    case remoteAudioCall = 33      // Remote initiated audio call
}

/// Call events
@objc public enum CallEvent: UInt {
    case none = 0
    case deinitialize = 1                         // Called deinitialize
    case callingTimeout = 3                       // Local call timed out
    case remoteCallingTimeout = 4                 // Remote call timed out
    case joinRTCSuccessed = 5                     // Successfully joined RTC
    case stateMismatch = 9                        // State transition anomaly
    case joinRTCStart = 10                        // Local has joined RTC channel but not yet successfully (called JoinChannelEx)
    case remoteUserRecvCall = 99                  // Caller call succeeded
    case localRejected = 100                      // Local user rejected
    case remoteRejected = 101                     // Remote user rejected
    case remoteAccepted = 103                     // Remote user accepted
    case localAccepted = 104                      // Local user accepted
    case localHangup = 105                        // Local user hung up
    case remoteHangup = 106                       // Remote user hung up
    case remoteJoined = 107                       // Remote user joined RTC channel
    case remoteLeft = 108                         // Remote user left RTC channel (eventReason refers to AgoraUserOfflineReason)
    case localCancelled = 109                      // Local user canceled the call
    case remoteCancelled = 110                     // Remote user canceled the call
    case localJoined = 111                        // Local user joined RTC channel
    case localLeft = 112                          // Local user left RTC channel
    case recvRemoteFirstFrame = 113               // Received remote first frame (video call for video frame first frame, audio call for audio frame first frame)
    case remoteCallBusy = 117                     // Remote user is busy
    case captureFirstLocalVideoFrame = 119        // Captured first video frame
    case publishFirstLocalVideoFrame = 120        // Successfully published first video frame
    case publishFirstLocalAudioFrame = 130        // Successfully published first audio frame [supported since 2.1.0]
    case localVideoCall = 140                     // Local initiated video call
    case localAudioCall = 141                     // Local initiated audio call
    case remoteVideoCall = 142                    // Remote initiated video call
    case remoteAudioCall = 143                    // Remote initiated audio call
}

/// Call error events
@objc public enum CallErrorEvent: UInt {
    case normalError = 0              // General error
    case rtcOccurError = 100          // RTC error occurred
    case startCaptureFail = 110       // Failed to start capture
    case sendMessageFail = 210        // Message sending failed
}

/// Error code types for call error events
@objc public enum CallErrorCodeType: UInt {
    case normal = 0   // Business type error, none at present
    case rtc          // RTC error, use AgoraErrorCode
    case message      // Message error, if using CallRtmSignalClient then it is AgoraRtmErrorCode, if using custom channel then it is the corresponding channel error code
}

/// Log levels
@objc public enum CallLogLevel: Int {
    case normal = 0
    case warning = 1
    case error = 2
}

@objc public protocol CallApiListenerProtocol: NSObjectProtocol {
    /// State response callback
    /// - Parameters:
    ///   - state: State type
    ///   - stateReason: Reason for state change
    ///   - eventReason: Description of event type
    ///   - eventInfo: Extended information, different event types have different parameters
    func onCallStateChanged(with state: CallStateType,
                            stateReason: CallStateReason,
                            eventReason: String,
                            eventInfo: [String: Any])

    /// Internal detailed event change callback
    /// - Parameters:
    ///   - event: Event
    ///   - eventReason: Event reason, default nil, different events represent different meanings
    @objc optional func onCallEventChanged(with event: CallEvent, eventReason: String?)
    
    /// Callback for errors that occur
    /// - Parameters:
    ///   - errorEvent: Error event
    ///   - errorType: Error code type
    ///   - errorCode: Error code
    ///   - message: Error message
    @objc optional func onCallError(with errorEvent: CallErrorEvent,
                                    errorType: CallErrorCodeType,
                                    errorCode: Int,
                                    message: String?)
    
    /// Callback for when the call starts
    /// - Parameters:
    ///   - roomId: Channel ID of the call
    ///   - callerUserId: User ID of the caller
    ///   - currentUserId: Your own ID
    ///   - timestamp: Timestamp when the call started, difference from 19700101, in ms
    @objc optional func onCallConnected(roomId: String,
                                        callUserId: UInt,
                                        currentUserId: UInt,
                                        timestamp: UInt64)
    
    /// Callback for when the call ends
    /// - Parameters:
    ///   - roomId: Channel ID of the call
    ///   - hangupUserId: User ID of the user who hung up
    ///   - currentUserId: Your own user ID
    ///   - timestamp: Timestamp when the call ended, difference from 19700101, in ms
    ///   - duration: Duration of the call, in ms
    @objc optional func onCallDisconnected(roomId: String,
                                           hangupUserId: UInt,
                                           currentUserId: UInt,
                                           timestamp: UInt64,
                                           duration: UInt64)
    
    /// When receiving a call, determine if you can join RTC
    /// - Parameter eventInfo: Extended information when receiving the call
    /// - Returns: true: can join false: cannot join
    @objc optional func canJoinRtcOnCalling(eventInfo: [String: Any]) -> Bool
    
    /// Token is about to expire (requires external retrieval of new token and calling renewToken to update)
    @objc optional func tokenPrivilegeWillExpire()
    
    /// Print logs
    /// - Parameters:
    ///   - message: Log information
    ///   - logLevel: Log priority: 0: normal log, 1: warning log, 2: error log
    @objc optional func callDebugInfo(message: String, logLevel: CallLogLevel)
}

@objc public protocol CallApiProtocol: NSObjectProtocol {
    /// Initialize configuration
    /// - Parameters:
    ///   - config: <#config description#>
    func initialize(config: CallConfig)
    
    /// Release cache
    func deinitialize(completion: @escaping (()->()))
    
    /// Update RTC token
    /// - Parameter config: <#config description#>
    func renewToken(with rtcToken: String)
    
    /// Prepare the call environment, must be called successfully to make a call. If you need to change the RTC channel number for the call, you can call it repeatedly. Ensure that it must be non-call state (not calling, connecting, connected) for it to succeed.
    /// - Parameters:
    ///   - config: <#config description#>
    ///   - completion: completion description
    func prepareForCall(prepareConfig: PrepareConfig, completion: ((NSError?)->())?)
    
    /// Add a listener for callbacks
    /// - Parameter listener: <#listener description#>
    func addListener(listener: CallApiListenerProtocol)
    
    /// Remove a listener for callbacks
    /// - Parameter listener: <#listener description#>
    func removeListener(listener: CallApiListenerProtocol)
    
    /// Initiate a call invitation (for video calls), the caller calls, establishing an RTC call connection with the remote user using the RTC channel number set by prepareForCall
    /// - Parameters:
    ///   - remoteUserId: User ID being called
    ///   - completion: <#completion description#>
    func call(remoteUserId: UInt, completion: ((NSError?)->())?)
    
    /// Initiate a call invitation, the caller calls, establishing an RTC call connection with the remote user using the RTC channel number set by prepareForCall
    /// - Parameters:
    ///   - remoteUserId: User ID being called
    ///   - callType: Call type: 0: video call, 1: audio call
    ///   - callExtension: Fields that need to be extended for the call. The kFromUserExtension field can be used to retrieve the state change when receiving a message from the remote user (e.g., calling/connecting)
    ///   - completion: <#completion description#>
    func call(remoteUserId: UInt,
              callType: CallType,
              callExtension: [String: Any],
              completion: ((NSError?)->())?)
    
    /// Cancel an ongoing call invitation, the caller calls
    /// - Parameter completion: <#completion description#>
    func cancelCall(completion: ((NSError?)->())?)
    
    /// Accept a call invitation, the callee calls
    /// - Parameters:
    ///   - remoteUserId: User ID being accepted
    ///   - completion: <#completion description#>
    func accept(remoteUserId: UInt, completion: ((NSError?)->())?)
    
    /// Reject a call invitation, the callee calls
    /// - Parameters:
    ///   - remoteUserId: User ID being rejected
    ///   - reason: Reason for rejection
    ///   - completion: <#completion description#>
    func reject(remoteUserId: UInt, reason: String?, completion: ((NSError?)->())?)
    
    /// Hang up the call, both caller and callee can call
    /// - Parameters:
    ///   - userId: User ID of the user hanging up
    ///   - reason: Reason for hanging up
    ///   - completion: <#completion description#>
    func hangup(remoteUserId: UInt, reason: String?, completion: ((NSError?)->())?)
    
    /// Get the current call's callId, callId is a unique identifier during the call process, through which Agora backend services can query the key node duration and the time points of state changes for the current call
    /// - Returns: callId, empty for messages not related to the call
    func getCallId() -> String
}
