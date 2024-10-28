//
//  CallConnectInfo.swift
//  CallAPI
//
//  Created by wushengtao on 2023/12/14.
//

import Foundation


/// Cost statistics type
public enum CallConnectCostType: String {
    case remoteUserRecvCall = "remoteUserRecvCall"                              // Caller successfully initiated a call; receiving call success indicates it has been delivered to the callee
    case acceptCall = "acceptCall"                                              // Caller received the callee's acceptance of the call (onAccept) / callee clicked to accept (accept)
    case localUserJoinChannel = "localUserJoinChannel"                          // Local user joined the channel
    case localFirstFrameDidCapture = "localFirstFrameDidCapture"                // The first frame of local video has been captured (only for video calls)
    case localFirstFrameDidPublish = "localFirstFrameDidPublish"                // Local user successfully published the first frame (audio or video)
    case remoteUserJoinChannel = "remoteUserJoinChannel"                        // Remote user joined the channel
    case recvFirstFrame = "recvFirstFrame"                                      // Received the first frame from the opposite side
}

class CallConnectInfo {
    /// Time to start retrieving the video stream
    private(set) var startRetrieveFirstFrame: Date?
    
    /// Whether the first frame of the opposite side's video has been retrieved
    var isRetrieveFirstFrame: Bool = false
    
    
    /// Call type
    var callType: CallType = .video
    
    /// Call session ID
    var callId: String = ""
    
    // Channel name during the call
    var callingRoomId: String?
    
    // Remote user during the call
    var callingUserId: UInt?
    
    // Call start time
    var callConnectedTs: UInt64 = 0
    
    /// Whether the local user has agreed
    var isLocalAccepted: Bool = false
    
    // Call start time
    private(set) var callTs: Int? {
        didSet {
            callCostMap.removeAll()
        }
    }
    
    var callCostMap: [String: Int] = [:]
    
    // Timer for initiating the call, used to handle timeouts
    var timer: Timer? {
        didSet {
            oldValue?.invalidate()
        }
    }
    
    func clean() {
        timer = nil
        callingRoomId = nil
        callingUserId = nil
        callTs = nil
        callId = ""
        isRetrieveFirstFrame = false
        startRetrieveFirstFrame = nil
        isLocalAccepted = false
        callConnectedTs = 0
    }
    
    func set(callType: CallType? = nil,
             userId: UInt,
             roomId: String,
             callId: String? = nil,
             isLocalAccepted: Bool = false) {
        if let callType = callType {
            self.callType = callType
        }
        self.callingUserId = userId
        self.callingRoomId = roomId
        self.isLocalAccepted = isLocalAccepted
        if let callId = callId {
            self.callId = callId
        }
        if callTs == nil {
            self.callTs = Date().millisecondsSince1970()
        }
        if startRetrieveFirstFrame == nil {
            self.startRetrieveFirstFrame = Date()
        }
    }
}
