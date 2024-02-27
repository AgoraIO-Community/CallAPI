//
//  CallRtmSignalClient.swift
//  CallAPI
//
//  Created by wushengtao on 2024/1/30.
//

import Foundation
import AgoraRtmKit

@objcMembers
public class CallRtmSignalClient: NSObject {
    private let delegates:NSHashTable<ISignalClientListener> = NSHashTable<ISignalClientListener>.weakObjects()
    private var rtmClient: AgoraRtmClientKit
    
    deinit {
        clean()
    }
    
    public required init(rtmClient: AgoraRtmClientKit) {
        self.rtmClient = rtmClient
        super.init()
        self.rtmClient.addDelegate(self)
        
        // disable retry message
        let _ = self.rtmClient.setParameters("{\"rtm.msg.tx_timeout\": 3000}")
        callMessagePrint("init-- CallMessageManager ")
    }
}

extension CallRtmSignalClient {
    private func _sendMessage(userId: String,
                              messageId: Int,
                              message: String,
                              completion: ((NSError?)-> Void)?) {
        if userId.count == 0 {
            completion?(NSError(domain: "send message fail! roomId is empty", code: -1))
            return
        }
        let msgId = messageId
        let data = message.data(using: .utf8)!
        let options = AgoraRtmPublishOptions()
        options.channelType = .user
        let date = Date()
        callMessagePrint("_sendMessage[\(msgId)] to '\(userId)', message: \(message)")
        rtmClient.publish(channelName: userId, data: data, option: options) { [weak self] resp, err in
            guard let self = self else {return}
            if let err = err {
                let error = NSError(domain: err.reason, code: err.errorCode.rawValue)
                self.callMessagePrint("_sendMessage[\(msgId)] fail: \(error) cost: \(date.getCostMilliseconds()) ms", 1)

                completion?(error)
                return
            }
            self.callMessagePrint("_sendMessage[\(msgId)] publish cost \(date.getCostMilliseconds()) ms")
            completion?(nil)
        }
    }
    
    private func callMessagePrint(_ message: String, _ logLevel: Int = 0) {
        let tag = "[CallRtmMessageManager][\(String.init(format: "%p", self))][\(String.init(format: "%p", rtmClient))]"
        for element in delegates.allObjects {
            element.debugInfo?(message: "\(tag)\(message)", logLevel: logLevel)
        }
    }
}


//MARK: AgoraRtmClientDelegate
//TODO: error handler
extension CallRtmSignalClient: AgoraRtmClientDelegate {
//    public func rtmKit(_ kit: AgoraRtmClientKit,
//                channel channelName: String,
//                connectionChangedToState state: AgoraRtmClientConnectionState,
//                reason: AgoraRtmClientConnectionChangeReason) {
//        callMessagePrint("rtm connectionChangedToState: \(state.rawValue) reason: \(reason.rawValue)")
//        if reason == .changedTokenExpired {
//            for element in delegates.allObjects {
//                element.onTokenWillExpire?(channelName: channelName)
//            }
//        } else if reason == .changedChangedLost {
//            //TODO: 内部重试，rtm 2.2.0支持
//            for element in delegates.allObjects {
//                element.onDisconnected?(channelName: channelName)
//            }
//        }
//    }
//    
//    public func rtmKit(_ rtmKit: AgoraRtmClientKit, tokenPrivilegeWillExpire channel: String?) {
//        callMessagePrint("rtm onTokenPrivilegeWillExpire[\(channel ?? "nil")]")
//        for element in delegates.allObjects {
//            element.onTokenWillExpire?(channelName: channel)
//        }
//    }
    
    //收到RTM消息
    public func rtmKit(_ rtmKit: AgoraRtmClientKit, 
                       didReceiveMessageEvent event: AgoraRtmMessageEvent) {
        guard let data = event.message.rawData,
              let message = String(data: data, encoding: .utf8) else {
               callMessagePrint("on event message parse fail", 1)
               return
        }
        for element in delegates.allObjects {
            element.onMessageReceive(message: message)
        }
    }
}

extension CallRtmSignalClient: ISignalClient {
    public func sendMessage(userId: String,
                            messageId: Int,
                            message: String,
                            completion: ((NSError?) -> Void)?) {
        guard userId.count > 0, userId != "0" else {
            let errorStr = "sendMessage fail, invalid userId[\(userId)]"
            callMessagePrint(errorStr)
            completion?(NSError(domain: errorStr, code: -1))
            return
        }

        _sendMessage(userId: userId, 
                     messageId: messageId,
                     message: message, 
                     completion: completion)
    }
    
    public func addListener(listener: ISignalClientListener) {
        if delegates.contains(listener) { return }
        delegates.add(listener)
    }
    
    public func removeListener(listener: ISignalClientListener) {
        delegates.remove(listener)
    }
    
    public func clean() {
        delegates.removeAllObjects()
        rtmClient.removeDelegate(self)
    }
}
