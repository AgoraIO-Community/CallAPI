//
//  CallRtmMessageManager.swift
//  CallAPI
//
//  Created by wushengtao on 2024/1/30.
//

import Foundation
import AgoraRtmKit

let kMessageId: String = "messageId"     //发送的消息id

@objcMembers
public class CallRtmMessageManager: NSObject {
    private let delegates:NSHashTable<ICallMessageListener> = NSHashTable<ICallMessageListener>.weakObjects()
    private var appId: String
    private var userId: String
    private var rtmToken: String?
    private var rtmClient: AgoraRtmClientKit!

    /// RTM是否已经登录
    private var isLoginedRtm: Bool = false
    
    /// 是否外部传入的rtm，如果是则不需要手动logout
    private var isExternalRtmClient: Bool = false
    
    private var prepareConfig: PrepareConfig?
    
    // 消息id
    private var messageId: Int = 0
    
    
    public required init(appId: String, userId: String, rtmToken: String, rtmClient: AgoraRtmClientKit? = nil) {
        self.appId = appId
        self.userId = userId
        self.rtmToken = rtmToken
        super.init()
        if let rtmClient = rtmClient {
            //如果外部传入rtmclient，默认登陆成功
            self.isLoginedRtm = true
            self.isExternalRtmClient = true
            self.rtmClient = rtmClient
        } else {
            self.rtmClient = _createRtmClient(delegate: nil)
        }
        // disable retry message
        let _ = self.rtmClient.setParameters("{\"rtm.msg.tx_timeout\": 3000}")
        callMessagePrint("init-- CallMessageManager ")
    }
    
    func _deinitialize() {
        rtmClient.removeDelegate(self)
        if isExternalRtmClient == false {
            rtmClient.logout()
            rtmClient.destroy()
        }
    }
    
    /// 根据配置初始化RTM
    /// - Parameters:
    ///   - prepareConfig: <#prepareConfig description#>
    ///   - tokenConfig: <#tokenConfig description#>
    ///   - completion: <#completion description#>
    func _initialize(rtmToken: String, completion: ((NSError?) -> ())?) {
        callMessagePrint("initialize")
        rtmClient.addDelegate(self)
        self.rtmToken = rtmToken
        if rtmToken.isEmpty, isExternalRtmClient == false {
            let reason = "RTM Token is Empty"
            completion?(NSError(domain: reason, code: -1))
            return
        }
        
        guard let rtmClient = self.rtmClient else {
            let reason = "rtmClient is nil, please invoke 'initialize' to setup config"
            completion?(NSError(domain: reason, code: -1))
            return
        }
        
        if !isLoginedRtm {
            loginRtm(rtmClient: rtmClient, token: rtmToken) {/*[weak self]*/ err in
                if let err = err, err.errorCode != .ok {
                    completion?(NSError(domain: err.reason, code: err.errorCode.rawValue))
                    return
                }

                completion?(nil)
            }
        } else {
            completion?(nil)
        }
    }
    
    /// 更新RTM token
    /// - Parameter tokenConfig: CallTokenConfig
    public func renewToken(rtmToken: String) {
        guard isLoginedRtm else {
            if let prepareConfig = prepareConfig {
                //没有登陆成功，但是需要自动登陆，可能是初始token问题，这里重新initialize
                callMessagePrint("renewToken need to reinit")
                self.rtmClient.logout()
                _initialize(rtmToken: rtmToken) { err in
                }
            }
            return
        }
        self.rtmToken = rtmToken
        rtmClient?.renewToken(rtmToken, completion: {[weak self] resp, err in
            self?.callMessagePrint("rtm renewToken: \(err?.errorCode.rawValue ?? 0)")
        })
    }
}

extension CallRtmMessageManager {
    private func _sendMessage(userId: String, message: [String: Any], completion: ((NSError?)-> Void)?) {
        if userId.count == 0 {
            completion?(NSError(domain: "send message fail! roomId is empty", code: -1))
            return
        }
        let msgId = message[kMessageId] as? Int ?? 0
        let data = try? JSONSerialization.data(withJSONObject: message)
        let options = AgoraRtmPublishOptions()
        options.channelType = .user
        let date = Date()
        callMessagePrint("_sendMessage[\(msgId)] to '\(userId)', message: \(message)")
        rtmClient.publish(channelName: userId, data: data!, option: options) { [weak self] resp, err in
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
    
    private func _createRtmClient(delegate: AgoraRtmClientDelegate?) -> AgoraRtmClientKit {
        let rtmConfig = AgoraRtmClientConfig(appId: appId, userId: userId)
        if rtmConfig.userId.count == 0 {
            callMessagePrint("userId is empty", 2)
        }
        if rtmConfig.appId.count == 0 {
            callMessagePrint("appId is empty", 2)
        }

        var rtmClient: AgoraRtmClientKit? = nil
        do {
            rtmClient = try AgoraRtmClientKit(rtmConfig, delegate: nil)
        } catch {
            callMessagePrint("create rtm client fail: \(error.localizedDescription)", 2)
        }
        return rtmClient!
    }
    
    private func loginRtm(rtmClient: AgoraRtmClientKit, token: String, completion: @escaping (AgoraRtmErrorInfo?)->()) {
        if isLoginedRtm {
            completion(nil)
            return
        }
        rtmClient.logout()
        callMessagePrint("will login")
        rtmClient.login(token) {[weak self] resp, error in
            guard let self = self else {return}
            self.callMessagePrint("login completion: \(error?.errorCode.rawValue ?? 0)")
            self.isLoginedRtm = error == nil ? true : false
            completion(error)
        }
    }
    private func callMessagePrint(_ message: String, _ logLevel: Int = 0) {
        let tag = "[CallRtmMessageManager][\(String.init(format: "%p", self))][\(String.init(format: "%p", rtmClient))]"
        for element in delegates.allObjects {
            element.debugInfo(message: "\(tag)\(message)", logLevel: logLevel)
        }
    }
}


//MARK: AgoraRtmClientDelegate
extension CallRtmMessageManager: AgoraRtmClientDelegate {
//    func rtmKit(_ kit: AgoraRtmClientKit,
//                channel channelName: String,
//                connectionChangedToState state: AgoraRtmClientConnectionState,
//                reason: AgoraRtmClientConnectionChangeReason) {
//        callMessagePrint("rtm connectionChangedToState: \(state.rawValue) reason: \(reason.rawValue)")
//        if reason == .changedTokenExpired {
//            self.delegate?.rtmKit?(kit, tokenPrivilegeWillExpire: nil)
//        } else if reason == .changedChangedLost {
//            self.delegate?.onConnectionFail()
//        }
//    }
    
//    func rtmKit(_ rtmKit: AgoraRtmClientKit, tokenPrivilegeWillExpire channel: String?) {
//        callMessagePrint("rtm onTokenPrivilegeWillExpire[\(channel ?? "nil")]")
//        self.delegate?.rtmKit?(rtmKit, tokenPrivilegeWillExpire: channel)
//    }
    
    //收到RTM消息
    public func rtmKit(_ rtmKit: AgoraRtmClientKit, didReceiveMessageEvent event: AgoraRtmMessageEvent) {
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

extension CallRtmMessageManager: ICallMessageManager {
    public func initialize(completion: @escaping ((NSError?) -> ())) {
        _initialize(rtmToken: rtmToken ?? "", completion: completion)
    }
    
    public func deinitialize() {
        _deinitialize()
    }
    
    public func sendMessage(userId: String, message: [String : Any], completion: ((NSError?) -> Void)?) {
        guard userId.count > 0, userId != "0" else {
        let errorStr = "sendMessage fail, invalid userId[\(userId)]"
        callMessagePrint(errorStr)
        completion?(NSError(domain: errorStr, code: -1))
        return
        }

        messageId += 1
        messageId %= Int.max
        var message = message
        message[kMessageId] = messageId
//        message[kReceiptsRoomIdKey] = fromUserId
//        assert(fromUserId.count > 0, "kReceiptsRoomIdKey is empty")
        _sendMessage(userId: userId, message: message, completion: completion)
    }
    
    public func addListener(listener: ICallMessageListener) {
        if delegates.contains(listener) { return }
        delegates.add(listener)
    }
    
    public func removeListener(listener: ICallMessageListener) {
        delegates.remove(listener)
    }
    
    public func clean() {
        delegates.removeAllObjects()
        deinitialize()
    }
}
