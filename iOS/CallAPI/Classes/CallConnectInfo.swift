//
//  CallConnectInfo.swift
//  CallAPI
//
//  Created by wushengtao on 2023/12/14.
//

import Foundation


/// Cost statistics type
/// 成本统计类型
public enum CallConnectCostType: String {
    // Caller successfully initiated a call; receiving call success indicates it has been delivered to the callee
    // 主叫成功发起通话,收到通话成功表示已送达给被叫
    case remoteUserRecvCall = "remoteUserRecvCall"
    
    // Caller received the callee's acceptance of the call (onAccept) / callee clicked to accept (accept)
    // 主叫收到被叫接受通话(onAccept)/被叫点击接受(accept)
    case acceptCall = "acceptCall"
    
    // Local user joined the channel
    // 本地用户加入频道
    case localUserJoinChannel = "localUserJoinChannel"
    
    // The first frame of local video has been captured (only for video calls)
    // 本地视频第一帧已捕获(仅视频通话)
    case localFirstFrameDidCapture = "localFirstFrameDidCapture"
    
    // Local user successfully published the first frame (audio or video)
    // 本地用户成功发布第一帧(音频或视频)
    case localFirstFrameDidPublish = "localFirstFrameDidPublish"
    
    // Remote user joined the channel
    // 远端用户加入频道
    case remoteUserJoinChannel = "remoteUserJoinChannel"
    
    // Received the first frame from the opposite side
    // 收到对方的第一帧
    case recvFirstFrame = "recvFirstFrame"
}

class CallConnectInfo {
    /// Time to start retrieving the video stream
    /// 开始获取视频流的时间
    private(set) var startRetrieveFirstFrame: Date?
    
    /// Whether the first frame of the opposite side's video has been retrieved
    /// 是否已获取对方视频的第一帧
    var isRetrieveFirstFrame: Bool = false
    
    /// Call type
    /// 通话类型
    var callType: CallType = .video
    
    /// Call session ID
    /// 通话会话ID
    var callId: String = ""
    
    // Channel name during the call
    // 通话过程中的频道名
    var callingRoomId: String?
    
    // Remote user during the call
    // 通话过程中的远端用户
    var callingUserId: UInt?
    
    // Call start time
    // 通话开始时间
    var callConnectedTs: UInt64 = 0
    
    /// Whether the local user has agreed
    /// 本地用户是否已同意
    var isLocalAccepted: Bool = false
    
    // Call start time
    // 通话开始时间
    private(set) var callTs: Int? {
        didSet {
            callCostMap.removeAll()
        }
    }
    
    var callCostMap: [String: Int] = [:]
    
    // Timer for initiating the call, used to handle timeouts
    // 发起通话的定时器,用于处理超时
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
