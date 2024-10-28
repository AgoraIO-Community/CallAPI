//
//  CallApiImpl.swift
//  CallAPI
//
//  Created by Agora on 2023/5/29.
//

import Foundation
import AgoraRtcKit

let kApiVersion = "2.1.2"

let kMessageId: String = "messageId"     // ID of the sent message

private let kCurrentMessageVersion = "1.0"
private let kMessageAction = "message_action"
private let kMessageVersion = "message_version"
private let kMessageTs = "message_timestamp"

private let kCallId = "callId"

public let kRemoteUserId = "remoteUserId"
public let kFromUserId = "fromUserId"
public let kFromUserExtension = "fromUserExtension"
public let kFromRoomId = "fromRoomId"
public let kCalleeState = "state"      // Current call state
public let kPublisher = "publisher"    // UID of the user that triggered the state; can currently indicate the state of the current user and the caller. If there is no publisher, it defaults to the current user.

/// ⚠️ Do not modify the following two values; clients may use rejectReason/call busy for business logic (e.g., user is busy).
public let kRejectReason = "rejectReason"
public let kRejectReasonCallBusy = "The user is currently busy"

public let kHangupReason = "hangupReason"

// Indicates whether the rejection is internal; internal rejection is currently marked as the other party being call busy.
public let kRejectByInternal = "rejectByInternal"

// Indicates whether the call was canceled internally; internal cancellation is currently marked as the other party remote calling timeout.
public let kCancelCallByInternal = "cancelCallByInternal"

public let kCostTimeMap = "costTimeMap"    // Duration information during the call, which will be reported step-by-step when connected.

struct CallCustomEvent {
    static let stateChange = "stateChange"
    static let eventChange = "eventChange"
}

enum CallAction: UInt {
    case videoCall = 0
    case cancelCall = 1
    case accept = 2
    case reject = 3
    case hangup = 4
    case audioCall = 10
}

// Default timing for joining RTC
var defaultCalleeJoinRTCTiming: CalleeJoinRTCTiming = .calling

/// Timing for joining RTC during an incoming call
@objc public enum CalleeJoinRTCTiming: Int {
    case calling = 0    // Join the channel and start pushing video stream immediately upon receiving the call. This incurs higher costs but provides faster display.
    case accepted       // Join the channel and start pushing video stream only after actively accepting the call. This incurs lower costs but results in slower display.
}

public class CallApiImpl: NSObject {
    private let delegates: NSHashTable<CallApiListenerProtocol> = NSHashTable<CallApiListenerProtocol>.weakObjects()
    private var config: CallConfig? {
        didSet {
            oldValue?.signalClient.removeListener(listener: self)
            config?.signalClient.addListener(listener: self)
        }
    }
    private var prepareConfig: PrepareConfig? = nil

    // Message ID
    private var messageId: Int = 0
    
    // Call information
    private var connectInfo = CallConnectInfo()
    
    private var tempRemoteCanvasView: UIView = UIView()
    
    private var reporter: APIReporter?
    
    /// Current state
    private var state: CallStateType = .idle {
        didSet {
            let prevState = oldValue
            if prevState == state { return }
            tempRemoteCanvasView.removeFromSuperview()
            switch state {
            case .calling:
                // Start a timer; if there is no response after the timeout, call no response
                let timeoutSecond = prepareConfig?.callTimeoutMillisecond ?? 0
                if timeoutSecond == 0 { return }
                let timeoutInterval = Double(timeoutSecond) / 1000
                connectInfo.timer = Timer.scheduledTimer(withTimeInterval: timeoutInterval,
                                                         repeats: false,
                                                         block: { [weak self] timer in
                    self?._cancelCall(cancelCallByInternal: true) { _ in
                    }
                    self?._updateAndNotifyState(state: .prepared, stateReason: .callingTimeout)
                    self?._notifyEvent(event: .callingTimeout)
                })
            case .prepared:
                connectInfo.timer = nil
                
                if prevState != .idle {
                    _prepareForCall(prepareConfig: prepareConfig!) { _ in
                    }
                }
            case .connecting:
                self.reporter?.startDurationEvent(name: APICostEvent.firstFramePerceived)
                break
            case .connected:
                _muteRemoteAudio(false)
                connectInfo.timer = nil
                if let remoteView = prepareConfig?.remoteView {
                    tempRemoteCanvasView.frame = remoteView.bounds
                    remoteView.addSubview(tempRemoteCanvasView)
                    tempRemoteCanvasView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                } else {
                    callWarningPrint("Remote view not found in connected state!")
                }
                let ext: [String: Any] = ["channelName": connectInfo.callingRoomId ?? "", "userId": config?.userId ?? 0]
                reporter?.endDurationEvent(name: APICostEvent.firstFramePerceived, ext: ext)
                reporter?.endDurationEvent(name: APICostEvent.firstFrameActual, ext: ext)
            case .idle, .failed:
                _leaveRTC()
                connectInfo.clean()
//                config = nil
                isPreparing = false
//                self.messageManager = nil
            default:
                break
            }
        }
    }
    
    /// Connection for joining the channel ex, used to leave the channel ex and check if already joined the ex channel
    private var rtcConnection: AgoraRtcConnection?
    // Callback for completing RTC join
    private var joinRtcCompletion: ((NSError?) -> Void)?
    // Callback for the first frame display/sound
    private var firstFrameCompletion: (() -> Void)?
    // Indicates whether currently preparing; currently a straightforward return of an error, to see if we need to store each closure for dispatch after completion
    private var isPreparing: Bool = false
    
    deinit {
        callPrint("deinit-- CallApiImpl")
    }
    
    public override init() {
        super.init()
        callPrint("init-- CallApiImpl")
    }
    
    // Get NTP time
    private func _getTimeInMs() -> Int {
        return Date().millisecondsSince1970()
    }
    
    private func _getCost(ts: Int? = nil) -> Int {
        guard let callTs = connectInfo.callTs else { return 0 }
        var cost = 0
        if let ts = ts {
            cost = ts - callTs
        } else {
            cost = _getTimeInMs() - callTs
        }
        return cost
    }
}

//MARK: message Dictionary
extension CallApiImpl {
    private func _messageDic(action: CallAction) -> [String: Any] {
        var dic: [String: Any] = [:]
        dic[kMessageAction] = action.rawValue
        dic[kMessageVersion] = kCurrentMessageVersion
        dic[kMessageTs] = _getTimeInMs()
        dic[kFromUserId] = config?.userId ?? 0
        dic[kCallId] = connectInfo.callId
        if let userExtension = prepareConfig?.userExtension {
            dic[kFromUserExtension] = userExtension
        }
        return dic
    }
    
    private func _callMessageDic(remoteUserId: UInt, 
                                 callType: CallType,
                                 fromRoomId: String,
                                 callExtension: [String: Any]) -> [String: Any] {
        var message: [String: Any] = _messageDic(action: callType == .video ? .videoCall : .audioCall)
        message[kRemoteUserId] = remoteUserId
        message[kFromRoomId] = fromRoomId
        var userExtension = (message[kFromUserExtension] as? [String: Any]) ?? [:]
        userExtension.merge(callExtension) { (_, new) in new }
        message[kFromUserExtension] = userExtension

        return message
    }
    
    private func _cancelCallMessageDic(cancelCallByInternal: Bool) -> [String: Any] {
        var message: [String: Any] = _messageDic(action: .cancelCall)
        message[kCancelCallByInternal] = cancelCallByInternal ? 1 : 0
        return message
    }
    
    private func _rejectMessageDic(reason: String?, rejectByInternal: Bool) -> [String: Any] {
        var message: [String: Any] = _messageDic(action: .reject)
        message[kRejectReason] = reason
        message[kRejectByInternal] = rejectByInternal ? 1 : 0
        return message
    }
    
    private func _hangupMessageDic(reason: String?) -> [String: Any] {
        var message: [String: Any] = _messageDic(action: .hangup)
        message[kHangupReason] = reason
        return message
    }
}

//MARK: private method
extension CallApiImpl {
    private func getNtpTimeInMs() -> UInt64 {
        var localNtpTime = config?.rtcEngine.getNtpWallTimeInMs() ?? 0

        if localNtpTime == 0 {
            localNtpTime = UInt64(_getTimeInMs())
        }

        return localNtpTime
    }
    
    private func _canJoinRtcOnCalling(eventInfo: [String: Any]) -> Bool {
        var emptyCount: Int = 0
        for element in delegates.allObjects {
            if let isEnable = element.canJoinRtcOnCalling?(eventInfo: eventInfo) {
                if isEnable {
                    return true
                }
            } else {
                emptyCount += 1
            }
        }
        
        // If no protocol is implemented, use the default value
        if emptyCount == delegates.allObjects.count {
            callPrint("join rtc strategy callback not found, use default")
            return true
        }
        
        return false
    }

    private func _notifyCallConnected() {
        guard let config = config else { return }
        let ntpTime = getNtpTimeInMs()
        connectInfo.callConnectedTs = ntpTime
        let callUserId = connectInfo.callingRoomId == prepareConfig?.roomId ? config.userId : connectInfo.callingUserId ?? 0
        for element in delegates.allObjects {
            element.onCallConnected?(roomId: connectInfo.callingRoomId ?? "",
                                     callUserId: callUserId,
                                     currentUserId: config.userId,
                                     timestamp: ntpTime)
        }
    }
    
    private func _notifyCallDisconnected(hangupUserId: UInt) {
        guard let config = config else { return }
        let ntpTime = getNtpTimeInMs()
        for element in delegates.allObjects {
            element.onCallDisconnected?(roomId: connectInfo.callingRoomId ?? "",
                                        hangupUserId: hangupUserId,
                                        currentUserId: config.userId,
                                        timestamp: ntpTime,
                                        duration: ntpTime - connectInfo.callConnectedTs)
        }
    }
    
    private func _notifyTokenPrivilegeWillExpire() {
        for element in delegates.allObjects {
            element.tokenPrivilegeWillExpire?()
        }
    }
    
    private func checkConnectedSuccess(reason: CallStateReason) {
        guard let rtcConnection = rtcConnection else {
            callWarningPrint("checkConnectedSuccess fail, connection not found")
            return
        }
        callPrint("checkConnectedSuccess: firstFrameWaittingDisabled: \(prepareConfig?.firstFrameWaittingDisabled ?? false), isRetrieveFirstFrame: \(connectInfo.isRetrieveFirstFrame) state: \(state.rawValue)")
        if prepareConfig?.firstFrameWaittingDisabled == true {
            guard state == .connecting else { return }
        } else {
            guard connectInfo.isRetrieveFirstFrame, state == .connecting else { return }
        }
        /*
         1. Because the callee joins the channel and subscribes to streams and pushes streams early, the dual end may receive the first video frame earlier than the callee clicks accept (changing to connecting).
         2. Since both ends receive onCall when matching 1v1, if A initiates accept, B receives onAccept + A's first frame, it will cause B to enter the connected state without accepting.
         Therefore:
         Changing to connecting: need to check if it has changed to "remote accepted" + "local accepted (or called)".
         Changing to connected: need to check if it is in "connecting state" + "received first frame".
         */
        _changeToConnectedState(reason: reason)
    }
    
    private func _changeToConnectedState(reason: CallStateReason) {
        let eventInfo: [String: Any] = [
            kFromRoomId: self.connectInfo.callingRoomId ?? "",
            kFromUserId: self.connectInfo.callingUserId ?? 0,
            kRemoteUserId: config?.userId ?? 0,
            kCostTimeMap: self.connectInfo.callCostMap
        ]
//        let elapsed = connectInfo.startRetrieveFirstFrame?.getCostMilliseconds() ?? 0
//        self._updateAndNotifyState(state: .connecting,
//                                   stateReason: .recvRemoteFirstFrame,
//                                   elapsed: elapsed)
        self._updateAndNotifyState(state: .connected,
                                   stateReason: reason,
//                                   elapsed: elapsed,
                                   eventInfo: eventInfo)
//        self._notifyEvent(event: .recvRemoteFirstFrame, elapsed: elapsed)
    }
    
    // External state notification
    private func _updateAndNotifyState(state: CallStateType,
                                       stateReason: CallStateReason = .none,
                                       eventReason: String = "",
//                                       elapsed: Int = 0,
                                       eventInfo: [String: Any] = [:]) {
        callPrint("call change[\(connectInfo.callId)] state: \(state.rawValue), stateReason: \(stateReason.rawValue), eventReason: '\(eventReason)'")
        let oldState = self.state
        // Check connected/disconnected
        if state == .connected, oldState == .connecting {
            _notifyCallConnected()
        } else if state == .prepared, oldState == .connected {
            switch stateReason {
                // Normally only .remoteCancel, .remoteHangup will trigger, the rest are fallback
            case .remoteCancelled, .remoteHangup, .remoteRejected, .remoteCallBusy:
                _notifyCallDisconnected(hangupUserId: connectInfo.callingUserId ?? 0)
            default:
                //.localHangup or bad case
                _notifyCallDisconnected(hangupUserId: config?.userId ?? 0)
                break
            }
        }
        var ext: [String: Any] = ["state": state.rawValue,
                                  "stateReason": stateReason.rawValue,
                                  "eventReason": eventReason,
                                  "userId": config?.userId ?? 0,
                                  "callId": connectInfo.callId]
        if let roomId = connectInfo.callingRoomId {
            ext["roomId"] = roomId
        }
        _reportCustomEvent(event: CallCustomEvent.stateChange, ext: ext)
        
        self.state = state
        for element in delegates.allObjects {
            element.onCallStateChanged(with: state,
                                       stateReason: stateReason,
                                       eventReason: eventReason,
                                       eventInfo: eventInfo)
        }
    }
    
    private func _notifySendMessageErrorEvent(error: NSError, reason: String?) {
        _notifyErrorEvent(with: .sendMessageFail,
                          errorType: .message,
                          errorCode: error.code,
                          message: "\(reason ?? "")\(error.localizedDescription)")
    }
    
    private func _notifyRtcOccurErrorEvent(errorCode: Int, message: String? = nil) {
        _notifyErrorEvent(with: .rtcOccurError,
                          errorType: .rtc,
                          errorCode: errorCode,
                          message: message)
    }
    
    private func _notifyErrorEvent(with errorEvent: CallErrorEvent,
                                   errorType: CallErrorCodeType,
                                   errorCode: Int,
                                   message: String?) {
        callPrint("call change[\(connectInfo.callId)] errorEvent: '\(errorEvent.rawValue)', errorType: '\(errorType.rawValue)', errorCode: '\(errorCode)', message: '\(message ?? "")'")
        for element in delegates.allObjects {
            element.onCallError?(with: errorEvent,
                                 errorType: errorType,
                                 errorCode: errorCode,
                                 message: message)
        }
    }
    
    private func _notifyEvent(event: CallEvent,
                              reasonCode: String? = nil,
                              reasonString: String? = nil) {
        callPrint("call change[\(connectInfo.callId)] event: \(event.rawValue) reasonCode: '\(reasonCode ?? "")' reasonString: '\(reasonString ?? "")'")
        if let config = config {
            var ext: [String: Any] = ["event": event.rawValue,
                                      "userId": config.userId,
                                      "state": state.rawValue,
                                      "callId": connectInfo.callId]
            if let roomId = connectInfo.callingRoomId {
                ext["roomId"] = roomId
            }
            if let reasonCode = reasonCode, !reasonCode.isEmpty {
                ext["reasonCode"] = reasonCode
            }
            if let reasonString = reasonString, !reasonString.isEmpty {
                ext["reasonString"] = reasonString
            }
            _reportCustomEvent(event: CallCustomEvent.eventChange, ext: ext)
        } else {
            callWarningPrint("_notifyEvent config == nil")
        }
        
        _notifyOptionalFunc { listener in
            listener.onCallEventChanged?(with: event, eventReason: reasonCode)
        }
        
        switch event {
        case .remoteUserRecvCall:
            _reportCostEvent(type: .remoteUserRecvCall)
        case .remoteJoined:
            _reportCostEvent(type: .remoteUserJoinChannel)
        case .localJoined:
            _reportCostEvent(type: .localUserJoinChannel)
        case .captureFirstLocalVideoFrame:
            _reportCostEvent(type: .localFirstFrameDidCapture)
        case .publishFirstLocalAudioFrame:
            if connectInfo.callType == .audio {
                _reportCostEvent(type: .localFirstFrameDidPublish)
            }
        case .publishFirstLocalVideoFrame:
            _reportCostEvent(type: .localFirstFrameDidPublish)
        case .remoteAccepted:
            _reportCostEvent(type: .acceptCall)
            checkConnectedSuccess(reason: .remoteAccepted)
        case .localAccepted:
            _reportCostEvent(type: .acceptCall)
            checkConnectedSuccess(reason: .localAccepted)
        case .recvRemoteFirstFrame:
            _reportCostEvent(type: .recvFirstFrame)
            checkConnectedSuccess(reason: .recvRemoteFirstFrame)
        default:
            break
        }
    }
    
    private func _notifyOptionalFunc(closure: ((CallApiListenerProtocol) -> ())) {
        for element in delegates.allObjects {
            closure(element)
        }
    }
    
    private func _prepareForCall(prepareConfig: PrepareConfig, completion: ((NSError?) -> ())?) {
        guard let _ = self.config else {
            let reason = "config is Empty"
            callWarningPrint(reason)
            completion?(NSError(domain: reason, code: -1))
            return
        }
        
        if isPreparing {
            let reason = "is already in preparing"
            callWarningPrint(reason)
            completion?(NSError(domain: reason, code: -1))
            return
        }
        
        switch state {
        case .calling, .connecting, .connected:
            let reason = "currently busy"
            callWarningPrint(reason)
            completion?(NSError(domain: reason, code: -1))
            return
        case .prepared:
            break
        case .failed, .idle:
            break
        }
        
        let tag = UUID().uuidString
        callPrint("prepareForCall[\(tag)]")
        self.prepareConfig = prepareConfig.cloneConfig()
        
        _leaveRTC()
        connectInfo.clean()
        
        completion?(nil)
    }
    
    private func _deinitialize() {
        _updateAndNotifyState(state: .idle)
        _notifyEvent(event: .deinitialize)
        reporter = nil
    }
    
    // Set remote video view
    private func _setupRemoteVideo(uid: UInt) {
        if connectInfo.callType == .audio { return }
        
        guard let connection = rtcConnection, let engine = config?.rtcEngine else {
            callWarningPrint("_setupRemoteVideo fail: connection or engine is empty")
            return
        }
        
        let videoCanvas = AgoraRtcVideoCanvas()
        videoCanvas.uid = uid
        videoCanvas.view = tempRemoteCanvasView
        videoCanvas.renderMode = .hidden
        let ret = engine.setupRemoteVideoEx(videoCanvas, connection: connection)
        callPrint("_setupRemoteVideo ret = \(ret) channelId: \(connection.channelId) uid: \(uid)")
    }
    
    private func _removeRemoteVideo(uid: UInt) {
        guard let connection = rtcConnection, let engine = config?.rtcEngine else {
            callWarningPrint("_removeRemoteVideo fail: connection or engine is empty")
            return
        }
        
        let videoCanvas = AgoraRtcVideoCanvas()
        videoCanvas.uid = uid
        videoCanvas.view = nil
        let ret = engine.setupRemoteVideoEx(videoCanvas, connection: connection)
        callPrint("_removeRemoteVideo ret = \(ret) channelId: \(connection.channelId) uid: \(uid)")
        tempRemoteCanvasView.removeFromSuperview()
        tempRemoteCanvasView = UIView()
    }
    
    // Set local video view
    private func _setupLocalVideo() {
        if connectInfo.callType == .audio { return }
        
        guard let engine = config?.rtcEngine, let localView = prepareConfig?.localView else {
            callWarningPrint("_setupLocalVideo fail: engine or localView empty")
            return
        }
        config?.rtcEngine.addDelegate(self)
        
        let canvas = AgoraRtcVideoCanvas()
        canvas.mirrorMode = .disabled
        canvas.setupMode = .add
        canvas.view = localView
        engine.setDefaultAudioRouteToSpeakerphone(true)
        engine.setupLocalVideo(canvas)
        let ret = engine.startPreview()
        
        if ret != 0 {
            _notifyErrorEvent(with: .startCaptureFail,
                              errorType: .rtc,
                              errorCode: Int(ret),
                              message: nil)
        }
    }
    
    private func _removeLocalVideo() {
        if connectInfo.callType == .audio { return }
        
        guard let engine = config?.rtcEngine, let localView = prepareConfig?.localView else {
            callWarningPrint("_removeLocalVideo fail: engine or localView is empty")
            return
        }
        let canvas = AgoraRtcVideoCanvas()
        canvas.setupMode = .remove
        canvas.view = localView
        engine.setupLocalVideo(canvas)
    }
    
    /// Check if the current RTC channel matches the provided room ID
    /// - Parameter roomId: <#roomId description#>
    /// - Returns: <#description#>
    private func _isCurrentRTCChannel(roomId: String) -> Bool {
        return rtcConnection?.channelId == roomId ? true : false
    }
    
    /// Check if the current RTC channel has successfully joined or is in the process of joining
    /// - Returns: <#description#>
    private func _isChannelJoinedOrJoining() -> Bool {
        return rtcConnection == nil ? false : true
    }
    
    /// Check if initialization is complete
    /// - Returns: <#description#>
    private func _isInitialized() -> Bool {
        switch state {
        case .idle, .failed:
            return false
        default:
            return true
        }
    }
    
    private func _isCallingUser(message: [String: Any]) -> Bool {
        guard let fromUserId = message[kFromUserId] as? UInt,
              fromUserId == connectInfo.callingUserId else { return false }
        return true
    }
    
    private func _joinRTCWithMediaOptions(roomId: String, completion:@escaping ((NSError?) -> ())) {
        if !_isCurrentRTCChannel(roomId: roomId) {
            _leaveRTC()
        }
        let isChannelJoinedOrJoining = _isChannelJoinedOrJoining()
        if isChannelJoinedOrJoining {
            completion(nil)
        } else {
            _joinRTC(roomId: roomId) { error in
                completion(error)
            }
        }
        
        let publishVideo = connectInfo.callType == .audio ? false : true
        _updatePublishStatus(audioStatus: true, videoStatus: publishVideo)
        
        let subscribeVideo = connectInfo.callType == .audio ? false : true
        _updateSubscribeStatus(audioStatus: true, videoStatus: subscribeVideo)
        
        // Mute after joining the channel, unmute only after connecting
        _muteRemoteAudio(true)
    }
    
    private func _joinRTCAsBroadcaster(roomId: String) {
        _joinRTCWithMediaOptions(roomId: roomId) { [weak self] error in
            guard let self = self else { return }
            if let err = error {
                self._notifyRtcOccurErrorEvent(errorCode: err.code, message: err.localizedDescription)
            } else {
                self._notifyEvent(event: .joinRTCSuccessed)
            }
        }
        setupCanvas()
    }
    
    /// Join RTC as a viewer
    /// - Parameters:
    ///   - roomId: <#roomId description#>
    ///   - completion: <#completion description#>
    private func _joinRTC(roomId: String, completion:@escaping ((NSError?) -> ())) {
        guard let config = self.config, let rtcToken = prepareConfig?.rtcToken else {
            completion(NSError(domain: "config is empty", code: -1))
            return
        }
        let connection = AgoraRtcConnection(channelId: roomId, localUid: Int(config.userId))
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.clientRoleType = .broadcaster
        mediaOptions.publishCameraTrack = false
        mediaOptions.publishMicrophoneTrack = false
        mediaOptions.autoSubscribeAudio = false
        mediaOptions.autoSubscribeVideo = false
        let ret = config.rtcEngine.joinChannelEx(byToken: rtcToken,
                                                 connection: connection,
                                                 delegate: self,
                                                 mediaOptions: mediaOptions)
        callPrint("joinRTC channel roomId: \(roomId) uid: \(config.userId) ret = \(ret)")
        rtcConnection = connection
        joinRtcCompletion = { _ in
            completion(nil)
        }
//        let date = Date()
        firstFrameCompletion = { [weak self] in
            guard let self = self else { return }
            connectInfo.isRetrieveFirstFrame = true
            self._notifyEvent(event: .recvRemoteFirstFrame)
        }
        
        if ret != 0 {
            _notifyRtcOccurErrorEvent(errorCode: Int(ret))
        }
        _notifyEvent(event: .joinRTCStart)
        
        reporter?.startDurationEvent(name: APICostEvent.firstFrameActual)
    }
    
    /// Update the status of pushing audio and video streams
    /// - Parameters:
    ///   - audioStatus: Whether to push audio stream
    ///   - videoStatus: Whether to push video stream
    private func _updatePublishStatus(audioStatus: Bool, videoStatus: Bool) {
        guard let config = self.config, let connection = rtcConnection else { return }
        callPrint("_updatePublishStatus audioStatus: \(audioStatus) videoStatus: \(videoStatus)")
        
        config.rtcEngine.enableLocalAudio(audioStatus)
        config.rtcEngine.enableLocalVideo(videoStatus)
        
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.publishCameraTrack = videoStatus
        mediaOptions.publishMicrophoneTrack = audioStatus
        config.rtcEngine.updateChannelEx(with: mediaOptions, connection: connection)
    }
    
    /// Update the subscription status of audio and video streams
    /// - Parameters:
    ///   - audioStatus: <#audioStatus description#>
    ///   - videoStatus: <#videoStatus description#>
    private func _updateSubscribeStatus(audioStatus: Bool, videoStatus: Bool) {
        guard let rtcEngine = self.config?.rtcEngine, let connection = rtcConnection else { return }
        callPrint("_updateSubscribeStatus audioStatus: \(audioStatus) videoStatus: \(videoStatus)")
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.autoSubscribeAudio = audioStatus
        mediaOptions.autoSubscribeVideo = videoStatus
        
        rtcEngine.updateChannelEx(with: mediaOptions, connection: connection)
    }
    
    private func _muteRemoteAudio(_ isMute: Bool) {
        guard let rtcEngine = self.config?.rtcEngine, let connection = rtcConnection else { return }
        if let uid = connectInfo.callingUserId {
            callPrint("_muteRemoteAudio: \(isMute)  uid: \(uid) channelId: \(connection.channelId)")
            rtcEngine.adjustUserPlaybackSignalVolumeEx(uid, volume: isMute ? 0 : 100, connection: connection)
        }
    }
    
    // Leave RTC channel
    private func _leaveRTC() {
        joinRtcCompletion = nil
        guard let rtcConnection = self.rtcConnection else {
//            callWarningPrint("Leave RTC channel failed, not joined the channel")
            return
        }
        cleanCanvas()
        _updatePublishStatus(audioStatus: false, videoStatus: false)
        config?.rtcEngine.stopPreview()
        config?.rtcEngine.removeDelegate(self)
        let ret = config?.rtcEngine.leaveChannelEx(rtcConnection)
        callPrint("leave RTC channel[\(rtcConnection.channelId)]: \(ret ?? -1)")
        self.rtcConnection = nil
    }
    
    /// Set up the canvas
    private func setupCanvas() {
        _setupLocalVideo()
        guard let callingUserId = connectInfo.callingUserId else {
            callWarningPrint("setupCanvas fail: callingUserId == nil")
            return
        }
        _setupRemoteVideo(uid: callingUserId)
    }
    
    private func cleanCanvas() {
        _removeLocalVideo()
        guard let callingUserId = connectInfo.callingUserId else {
            callWarningPrint("cleanCanvas fail: callingUserId == nil")
            return
        }
        _removeRemoteVideo(uid: callingUserId)
    }
    
    private func _reportCostEvent(type: CallConnectCostType) {
        let cost = _getCost()
        connectInfo.callCostMap[type.rawValue] = cost
        let ext: [String: Any] = ["channelName": connectInfo.callingRoomId ?? "",
                                  "callId": connectInfo.callId,
                                  "userId": config?.userId ?? 0]
        reporter?.reportCostEvent(name: type.rawValue, cost: cost, ext: ext)
    }
    
    private func _reportMethod(event: String, value: [String: Any]? = nil) {
        let value = value ?? [:]
        callPrint("_reportMethod event: \(event) ext: \(value)")
        var subEvent = event
        if let range = event.range(of: "(") {
            subEvent = String(event[..<range.lowerBound])
        }
        let ext: [String: Any] = ["callId": connectInfo.callId,
                                  "userId": config?.userId ?? 0]
        reporter?.reportFuncEvent(name: subEvent,
                                  value: value,
                                  ext: ext)
    }
    
    private func _reportCustomEvent(event: String, ext: [String: Any]) {
        callPrint("_reportCustomEvent event: \(event) ext: \(ext)")
        reporter?.reportCustomEvent(name: event, ext: ext)
    }
    
    private func _sendMessage(userId: String,
                              message: [String: Any],
                              completion: ((NSError?) -> ())?) {
        messageId += 1
        messageId %= Int.max
        var message = message
        message[kMessageId] = messageId
        let data = try? JSONSerialization.data(withJSONObject: message)
        let messageStr = String(data: data!, encoding: .utf8)!
        config?.signalClient.sendMessage(userId: "\(userId)",
                                         message: messageStr,
                                         completion: completion)
    }
}

//MARK: on Message
extension CallApiImpl {
    private func _processRespEvent(reason: CallAction, message: [String: Any]) {
        switch reason {
        case .videoCall:
            _onCall(message: message, callType: .video)
        case .audioCall:
            _onCall(message: message, callType: .audio)
        case .cancelCall:
            _onCancel(message: message)
        case .reject:
            _onReject(message: message)
        case .accept:
            _onAccept(message: message)
        case .hangup:
            _onHangup(message: message)
        }
    }
    
    private func _call(remoteUserId: UInt,
                       callType: CallType,
                       callExtension: [String: Any],
                       completion: ((NSError?)->())?) {
        guard let fromRoomId = prepareConfig?.roomId else {
            let reason = "call fail! config or roomId is empty"
            completion?(NSError(domain: reason, code: -1))
            callWarningPrint(reason)
            return
        }
        
        guard state == .prepared else {
            let reason = "call fail! state busy or not initialized"
            completion?(NSError(domain: reason, code: -1))
            callWarningPrint(reason)
            return
        }
        
        // Send call message
        connectInfo.set(callType: callType,
                        userId: remoteUserId,
                        roomId: fromRoomId,
                        callId: UUID().uuidString,
                        isLocalAccepted: true)
        
        let message: [String: Any] = _callMessageDic(remoteUserId: remoteUserId,
                                                     callType: callType,
                                                     fromRoomId: fromRoomId,
                                                     callExtension: callExtension)
        _sendMessage(userId: "\(remoteUserId)", message: message) {[weak self] err in
            guard let self = self else { return }
            completion?(err)
            if let error = err {
//                self._updateAndNotifyState(state: .prepared, stateReason: .messageFailed, eventReason: error.localizedDescription)
                self._notifySendMessageErrorEvent(error: error, reason: "call fail: ")
            } else {
                self._notifyEvent(event: .remoteUserRecvCall)
            }
        }
        
        let reason: CallStateReason = callType == .video ? .localVideoCall : .localAudioCall
        let event: CallEvent = callType == .video ? .localVideoCall : .localAudioCall
        _updateAndNotifyState(state: .calling, stateReason: reason, eventInfo: message)
        _notifyEvent(event: event)
        
        _joinRTCAsBroadcaster(roomId: fromRoomId)
    }
    
    // Cancel the call
    private func _cancelCall(message: [String: Any]? = nil,
                             cancelCallByInternal: Bool = false,
                             completion: ((NSError?) -> ())?) {
        guard let userId = connectInfo.callingUserId else {
            completion?(NSError(domain: "cancelCall fail! callingUserId is empty", code: -1))
            callWarningPrint("cancelCall fail! callingUserId is empty")
            return
        }
        let message: [String: Any] = message ?? _cancelCallMessageDic(cancelCallByInternal: cancelCallByInternal)
        _sendMessage(userId: "\(userId)", message: message) { err in
            completion?(err)
            guard let error = err else { return }
            self._notifySendMessageErrorEvent(error: error, reason: "cancelCall fail: ")
        }
    }
    
    private func _reject(remoteUserId: UInt,
                         reason: String?,
                         rejectByInternal: Bool = false,
                         completion: ((NSError?) -> ())? = nil) {
        let message = _rejectMessageDic(reason: reason, rejectByInternal: rejectByInternal)
        _reject(remoteUserId: remoteUserId, message: message, completion: completion)
    }
    
    private func _reject(remoteUserId: UInt,
                         message: [String: Any],
                         completion: ((NSError?) -> ())? = nil) {
        _sendMessage(userId: "\(remoteUserId)",
                     message: message,
                     completion: completion)
    }
    
    private func _hangup(remoteUserId: UInt,
                         message: [String: Any]? = nil,
                         completion: ((NSError?) -> ())? = nil) {
        _sendMessage(userId: "\(remoteUserId)",
                     message: message ?? _messageDic(action: .hangup),
                     completion: completion)
    }
}

//MARK: on resp
extension CallApiImpl {
    // Received call message
    private func _onCall(message: [String: Any], callType: CallType) {
        let fromRoomId = message[kFromRoomId] as? String ?? ""
        let fromUserId = message[kFromUserId] as? UInt ?? 0
        let callId = message[kCallId] as? String ?? ""

        var enableNotify: Bool = true
        switch state {
        case .prepared:
            break
        case .idle, .failed:
            // not reachable
//            _reject(remoteUserId: fromUserId, reason: kRejectReasonCallBusy, rejectByInternal: true)
            return
        case .calling, .connecting, .connected:
            if connectInfo.callingUserId ?? 0 != fromUserId {
                _reject(remoteUserId: fromUserId, reason: kRejectReasonCallBusy, rejectByInternal: true)
                return
            }
            if state == .calling {
                enableNotify = false
            }
        }

        connectInfo.set(callType: callType, userId: fromUserId, roomId: fromRoomId, callId: callId)
        
        defaultCalleeJoinRTCTiming = _canJoinRtcOnCalling(eventInfo: message) ? .calling : .accepted
        
        if enableNotify {
            let reason: CallStateReason = callType == .video ? .remoteVideoCall : .remoteAudioCall
            let event: CallEvent = callType == .video ? .remoteVideoCall : .remoteAudioCall
            _updateAndNotifyState(state: .calling, stateReason: reason, eventInfo: message)
            _notifyEvent(event: event)
        }
        
        callPrint("[calling]defaultCalleeJoinRTCTiming: \(defaultCalleeJoinRTCTiming.rawValue)")
        if defaultCalleeJoinRTCTiming == .calling {
            // The join operation needs to be executed after the calling event is thrown,
            // because in scenarios like switching to a 1v1 show,
            // it is necessary to notify the external system to close external capture first;
            // otherwise, internal streaming will fail, causing the other party to see no video.
            _joinRTCAsBroadcaster(roomId: fromRoomId)
        }
        
        if connectInfo.isLocalAccepted, prepareConfig?.firstFrameWaittingDisabled == true {
            // If the first frame is not associated, in the scenario of switching to a 1v1 show,
            // it might automatically accept, leading to a state of connected before joining the channel,
            // making unmuting ineffective.
            checkConnectedSuccess(reason: .localAccepted)
        }
    }

    // Received cancel call message
    fileprivate func _onCancel(message: [String: Any]) {
        // Do not process if the operation is not from the user who is currently calling
        guard _isCallingUser(message: message) else { return }
        var stateReason: CallStateReason = .remoteCancelled
        var callEvent: CallEvent = .remoteCancelled
        if let cancelCallByInternal = message[kCancelCallByInternal] as? Int, cancelCallByInternal == 1 {
            stateReason = .remoteCallingTimeout
            callEvent = .remoteCallingTimeout
        }
        _updateAndNotifyState(state: .prepared, stateReason: stateReason, eventInfo: message)
        _notifyEvent(event: callEvent)
    }

    // Received reject message
    fileprivate func _onReject(message: [String: Any]) {
        guard _isCallingUser(message: message) else { return }
        var stateReason: CallStateReason = .remoteRejected
        var callEvent: CallEvent = .remoteRejected
        if let rejectByInternal = message[kRejectByInternal] as? Int, rejectByInternal == 1 {
            stateReason = .remoteCallBusy
            callEvent = .remoteCallBusy
        }
        _updateAndNotifyState(state: .prepared, stateReason: stateReason, eventInfo: message)
        _notifyEvent(event: callEvent)
    }

    // Received accept message
    fileprivate func _onAccept(message: [String: Any]) {
        // Must be in calling state and from the user who initiated the call
        guard state == .calling, _isCallingUser(message: message) else { return }
//        let elapsed = _getTimeInMs() - (connectInfo.callTs ?? 0)
// TODO: If already connected
// and isLocalAccepted (initiated the call or has already accepted),
// otherwise consider that local has not agreed
        if connectInfo.isLocalAccepted {
            _updateAndNotifyState(state: .connecting, stateReason: .remoteAccepted, eventInfo: message)
        }
        _notifyEvent(event: .remoteAccepted)
    }

    // Received hangup message
    fileprivate func _onHangup(message: [String: Any]) {
        guard _isCallingUser(message: message) else { return }

        _updateAndNotifyState(state: .prepared, stateReason: .remoteHangup, eventInfo: message)
        _notifyEvent(event: .remoteHangup)
    }
}

//MARK: CallApiProtocol
extension CallApiImpl: CallApiProtocol {
    public func getCallId() -> String {
        _reportMethod(event: "\(#function)")
        return connectInfo.callId
    }
    
    public func initialize(config: CallConfig) {
        defer {
            _reportMethod(event: "\(#function)", value: ["appId": config.appId, "userId": config.userId])
        }
        if state != .idle {
            callWarningPrint("must invoke 'deinitialize' to clean state")
            return
        }
        
        self.config = config.cloneConfig()
        
        // API enables accelerated rendering of the first frame of audio and video
        if let rtcEngine = config.rtcEngine {
            reporter = APIReporter(type: .call, version: kApiVersion, engine: rtcEngine)
            optimize1v1Video(engine: rtcEngine)
        }
    }
    
    public func deinitialize(completion: @escaping (()->())) {
        _reportMethod(event: "\(#function)")
        switch state {
        case .calling:
            cancelCall { err in
                completion()
            }
            _deinitialize()
        case .connecting, .connected:
            let callingUserId = connectInfo.callingUserId ?? 0
            _hangup(remoteUserId: callingUserId) { err in
                completion()
            }
            _deinitialize()
        default:
            self._deinitialize()
            completion()
        }
    }
    
    public func renewToken(with rtcToken: String) {
        _reportMethod(event: "\(#function)")
        guard let roomId = prepareConfig?.roomId else {
            callWarningPrint("renewToken failed, roomid mismatch")
            return
        }
        self.prepareConfig?.rtcToken = rtcToken
//        callPrint("renewToken with roomId[\(roomId)]")
        guard let connection = rtcConnection else {
            return
        }
        let options = AgoraRtcChannelMediaOptions()
        options.token = rtcToken
        let ret = self.config?.rtcEngine.updateChannelEx(with: options, connection: connection)
        callPrint("rtc[\(roomId)] renewToken ret = \(ret ?? -1)")
    }
    
    public func prepareForCall(prepareConfig: PrepareConfig, completion: ((NSError?) -> ())?) {
        _reportMethod(event: "\(#function)", value: ["roomId": prepareConfig.roomId, "callTimeoutMillisecond": prepareConfig.callTimeoutMillisecond])
        _prepareForCall(prepareConfig: prepareConfig) { err in
            if let err = err {
                self._updateAndNotifyState(state: .failed,
                                           stateReason: .none,
                                           eventReason: err.localizedDescription)
                completion?(err)
                return
            }
            self._updateAndNotifyState(state: .prepared)
            completion?(nil)
        }
    }
    
    public func addListener(listener: CallApiListenerProtocol) {
        _reportMethod(event: "\(#function)")
        if delegates.contains(listener) { return }
        delegates.add(listener)
    }
    
    public func removeListener(listener: CallApiListenerProtocol) {
        _reportMethod(event: "\(#function)")
        delegates.remove(listener)
    }
    
    // Call
    public func call(remoteUserId: UInt, completion: ((NSError?) -> ())?) {
        _call(remoteUserId: remoteUserId,
              callType: .video,
              callExtension: [:],
              completion: completion)
        _reportMethod(event: "\(#function)", value: ["remoteUserId": remoteUserId])
    }
    
    public func call(remoteUserId: UInt,
                     callType: CallType,
                     callExtension: [String: Any],
                     completion: ((NSError?)->())?) {
        _call(remoteUserId: remoteUserId,
              callType: callType,
              callExtension: callExtension,
              completion: completion)
        _reportMethod(event: "\(#function)", value: ["remoteUserId": remoteUserId, "callType": callType.rawValue, "callExtension": callExtension])
    }
    
    // Cancel call
    public func cancelCall(completion: ((NSError?) -> ())?) {
        _reportMethod(event: "\(#function)")
        let message: [String: Any] = _cancelCallMessageDic(cancelCallByInternal: false)
        _cancelCall(message: message, completion: completion)
        _updateAndNotifyState(state: .prepared, stateReason: .localCancelled, eventInfo: message)
        _notifyEvent(event: .localCancelled)
    }
    
    // Accept
    public func accept(remoteUserId: UInt, completion: ((NSError?) -> ())?) {
        _reportMethod(event: "\(#function)", value: ["remoteUserId": remoteUserId])
        guard let roomId = connectInfo.callingRoomId else {
            let errReason = "accept fail! current userId or roomId is empty"
            completion?(NSError(domain: errReason, code: -1))
            return
        }
        // Check if in calling state; if in prepared, it indicates the caller may have canceled
        guard state == .calling else {
            let errReason = "accept fail! current state[\(state.rawValue)] is not calling"
            completion?(NSError(domain: errReason, code: -1))
            _notifyEvent(event: .stateMismatch, reasonString: errReason)
            return
        }
        
        connectInfo.set(userId: remoteUserId, roomId: roomId, isLocalAccepted: true)
        
        let message: [String: Any] = _messageDic(action: .accept)
        _sendMessage(userId: "\(remoteUserId)", message: message) { err in
            completion?(err)
            guard let error = err else { return }
            self._notifySendMessageErrorEvent(error: error, reason: "accept fail: ")
        }
        
        callPrint("[accepted]defaultCalleeJoinRTCTiming: \(defaultCalleeJoinRTCTiming.rawValue)")
        if defaultCalleeJoinRTCTiming == .accepted {
            /*
             Because connecting will set autosubscribeAudio=true,
             here join will be set to false,
             thus if this method needs to be called, it must be called before the state machine changes to connecting.
             */
            _joinRTCAsBroadcaster(roomId: roomId)
        }
        
        _updateAndNotifyState(state: .connecting, stateReason: .localAccepted, eventInfo: message)
        _notifyEvent(event: .localAccepted)
    }
    
    // Reject
    public func reject(remoteUserId: UInt, reason: String?, completion: ((NSError?) -> ())?) {
        _reportMethod(event: "\(#function)", value: ["remoteUserId": remoteUserId, "reason": reason ?? ""])
        let message = _rejectMessageDic(reason: reason, rejectByInternal: false)
        _reject(remoteUserId: remoteUserId, message: message) {[weak self] err in
            completion?(err)
            guard let self = self, let error = err else { return }
            self._notifySendMessageErrorEvent(error: error, reason: "reject fail: ")
        }
        _updateAndNotifyState(state: .prepared, stateReason: .localRejected, eventInfo: message)
        _notifyEvent(event: .localRejected)
    }
    
    // Hang up
    public func hangup(remoteUserId: UInt, reason: String?, completion: ((NSError?) -> ())?) {
        _reportMethod(event: "\(#function)", value: ["remoteUserId": remoteUserId])
        let message = _hangupMessageDic(reason: reason)
        _hangup(remoteUserId: remoteUserId, message: message) {[weak self] err in
            completion?(err)
            guard let self = self, let error = err else { return }
            self._notifySendMessageErrorEvent(error: error, reason: "hangup fail: ")
        }
        _updateAndNotifyState(state: .prepared, stateReason: .localHangup, eventInfo: message)
        _notifyEvent(event: .localHangup)
    }
}

//MARK: CallMessageDelegate
extension CallApiImpl: ISignalClientListener {
    public func debugInfo(message: String, logLevel: Int) {
        callPrint(message, CallLogLevel(rawValue: logLevel) ?? .normal)
    }
    
    public func onMessageReceive(message: String) {
        callPrint("on event message: \(message)")
        guard let data = message.data(using: .utf8),
              let msg = try? JSONSerialization.jsonObject(with: data, options: .mutableContainers) as? [String: Any] else {
            return
        }
        guard let messageAction = CallAction(rawValue: msg[kMessageAction] as? UInt ?? 0),
              let messageVersion = msg[kMessageVersion] as? String else {
            callWarningPrint("fail to parse message")
            return
        }
        
        //TODO: compatible other message version
        guard kCurrentMessageVersion == messageVersion else { return }
        _processRespEvent(reason: messageAction, message: msg)
    }
}

//MARK: AgoraRtcEngineDelegate
extension CallApiImpl: AgoraRtcEngineDelegate {
    public func rtcEngine(_ engine: AgoraRtcEngineKit, tokenPrivilegeWillExpire token: String) {
        _notifyTokenPrivilegeWillExpire()
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit, connectionChangedTo state: AgoraConnectionState, reason: AgoraConnectionChangedReason) {
        callPrint("connectionChangedTo state: \(state.rawValue) reason: \(reason.rawValue)")
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        callPrint("didJoinedOfUid: \(uid) elapsed: \(elapsed)")
        guard connectInfo.callingUserId == uid/*, let config = config*/ else { return }
        _notifyEvent(event: .remoteJoined)
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didOfflineOfUid uid: UInt, reason: AgoraUserOfflineReason) {
        callPrint("didOfflineOfUid: \(uid) reason: \(reason.rawValue)")
        guard connectInfo.callingUserId == uid else { return }
        _notifyEvent(event: .remoteLeft, reasonCode: "\(reason.rawValue)")
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didLeaveChannelWith stats: AgoraChannelStats) {
        callPrint("didLeaveChannelWith")
        /*
         Since the transition from leave rtc to didLeaveChannelWith is asynchronous,
         setting rtcConnection = nil here will cause an immediate join after leave,
         which will incorrectly nullify the rtc connection after joining in didLeaveChannelWith.
         */
//        rtcConnection = nil
        _notifyEvent(event: .localLeft)
    }

    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        callPrint("join RTC channel, didJoinChannel: \(uid), channel: \(channel) elapsed: \(elapsed)")
        guard uid == config?.userId ?? 0 else { return }
        joinRtcCompletion?(nil)
        joinRtcCompletion = nil
        _notifyEvent(event: .localJoined)
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didOccurError errorCode: AgoraErrorCode) {
        _notifyRtcOccurErrorEvent(errorCode: errorCode.rawValue)
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit,
                          remoteVideoStateChangedOfUid uid: UInt,
                          state: AgoraVideoRemoteState,
                          reason: AgoraVideoRemoteReason,
                          elapsed: Int) {
        let channelId = prepareConfig?.roomId ?? ""
        guard uid == connectInfo.callingUserId else { return }
        callPrint("didLiveRtcRemoteVideoStateChanged channelId: \(channelId)/\(connectInfo.callingRoomId ?? "") uid: \(uid)/\(connectInfo.callingUserId ?? 0) state: \(state.rawValue) reason: \(reason.rawValue)")
        if state == .decoding /*2*/,
           (reason == .remoteUnmuted /*6*/ || reason == .localUnmuted /*4*/ || reason == .localMuted /*3*/) {
            DispatchQueue.main.async {
                self.firstFrameCompletion?()
            }
        }
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit,
                          firstLocalVideoFramePublishedWithElapsed elapsed: Int,
                          sourceType: AgoraVideoSourceType) {
        _notifyEvent(event: .publishFirstLocalVideoFrame, reasonString: "elapsed: \(elapsed)ms")
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit, firstLocalVideoFrameWith size: CGSize, elapsed: Int, sourceType: AgoraVideoSourceType) {
        _notifyEvent(event: .captureFirstLocalVideoFrame, reasonString: "elapsed: \(elapsed)ms")
        config?.rtcEngine.removeDelegate(self)
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit,
                          firstLocalAudioFramePublished elapsed: Int) {
        _notifyEvent(event: .publishFirstLocalAudioFrame, reasonString: "elapsed: \(elapsed)ms")
    }
    
    public func rtcEngine(_ engine: AgoraRtcEngineKit, firstRemoteAudioFrameOfUid uid: UInt, elapsed: Int) {
        guard connectInfo.callType == .audio else { return }
        
        let channelId = prepareConfig?.roomId ?? ""
        guard uid == connectInfo.callingUserId else { return }
        callPrint("firstRemoteAudioFrameOfUid: \(channelId) uid: \(uid)")
        DispatchQueue.main.async {
            self.firstFrameCompletion?()
        }
    }
}

extension CallApiImpl {
    func callPrint(_ message: String, _ logLevel: CallLogLevel = .normal) {
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        let timeString = formatter.string(from: Date())
        for element in delegates.allObjects {
            element.callDebugInfo?(message: "\(timeString) \(message)", logLevel: logLevel)
        }
        var level: AgoraLogLevel = .info
        if logLevel == .error {
            level = .error
        } else if logLevel == .warning {
            level = .warn
        }
        reporter?.writeLog(content: "[CallApi]\(message)", level: level)
    }
    
    func callWarningPrint(_ message: String) {
        callPrint(message, .warning)
    }
    
    func callErrorPrint(_ message: String) {
        callPrint(message, .error)
    }

    func callProfilePrint(_ message: String) {
        callPrint("[Profile]\(message)")
    }
}
