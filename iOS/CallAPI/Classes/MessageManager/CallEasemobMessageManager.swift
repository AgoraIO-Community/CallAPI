//
//  CallEasemobMessageManager.swift
//  CallAPI
//
//  Created by wushengtao on 2024/2/8.
//

import Foundation
import HyphenateChat

public class CallEasemobMessageManager: NSObject {
    private let delegates:NSHashTable<ICallMessageListener> = NSHashTable<ICallMessageListener>.weakObjects()
    private var appKey: String
    private var userId: String
    // 消息id
    private var messageId: Int = 0
    public init(appKey: String, userId: String) {
        self.appKey = appKey
        self.userId = userId
        super.init()
        
        let option = EMOptions(appkey: appKey)
        EMClient.shared().initializeSDK(with: option)
    }
    
    public func login(completion: @escaping ((NSError?)->())) {
        let uid = userId
        let pwd = uid
        let date = Date()
        callMessagePrint("register start")
        EMClient.shared().register(withUsername: uid, password: pwd) {[weak self] username, err in
            if let err = err, err.code != EMErrorCode.userAlreadyExist {
                self?.callMessagePrint("register fail: \(String(describing: err.errorDescription))")
                completion(NSError(domain: err.errorDescription, code: err.code.rawValue))
                return
            }
            self?.callMessagePrint("login start")
            EMClient.shared().login(withUsername: uid, password: pwd) { username, err in
                if let err = err, err.code != EMErrorCode.userAlreadyLoginSame {
                    self?.callMessagePrint("login fail: \(String(describing: err.errorDescription))")
                    completion(NSError(domain: err.errorDescription, code: err.code.rawValue))
                    return
                }
                self?.callMessagePrint("login success \(-date.timeIntervalSinceNow * 1000) ms")
                EMClient.shared().chatManager?.add(self, delegateQueue: nil)
                completion(nil)
            }
        }
    }
    
    public func logout() {
        let date = Date()
        EMClient.shared().chatManager?.remove(self)
        EMClient.shared().logout(false)
        print("logout success: \(-date.timeIntervalSinceNow * 1000) ms")
    }
    
    public func renew(token: String) {
        EMClient.shared().renewToken(token)
    }
}


extension CallEasemobMessageManager {
    private func callMessagePrint(_ message: String, _ logLevel: Int = 0) {
        let tag = "[CallEasemobMessageManager]"
        for element in delegates.allObjects {
            element.debugInfo(message: "\(tag)\(message)", logLevel: logLevel)
        }
    }
    
    private func _sendMessage(userId: String, message: [String: Any], completion: ((NSError?)-> Void)?) {
        if userId.count == 0 {
            completion?(NSError(domain: "send message fail! roomId is empty", code: -1))
            return
        }
        let msgId = message[kMessageId] as? Int ?? 0
        guard let data = try? JSONSerialization.data(withJSONObject: message) else {
            completion?(NSError(domain: "message data is empty", code: -1))
            return
        }
        let text = String(data: data, encoding: .utf8)
        
        let body = EMTextMessageBody(text: text)
        callMessagePrint("_sendMessage[\(msgId)] to '\(userId)', message: \(String(describing: text))")
        let emMessage = EMChatMessage(conversationID: userId, from: self.userId, to: userId, body: body, ext: nil)
        let date = Date()
        EMClient.shared().chatManager?.send(emMessage, progress: nil, completion: {[weak self] msg, err in
            guard let self = self else {return}
            if let err = err {
                let error = NSError(domain: err.errorDescription, code: err.code.rawValue)
                self.callMessagePrint("_sendMessage[\(msgId)] fail: \(error) cost: \(date.getCostMilliseconds()) ms", 1)

                completion?(error)
                return
            }
            self.callMessagePrint("_sendMessage[\(msgId)] publish cost \(date.getCostMilliseconds()) ms")
            completion?(nil)
        })
    }
}


extension CallEasemobMessageManager: ICallMessageManager {
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
        logout()
    }
}


extension CallEasemobMessageManager: EMChatManagerDelegate {
    public func messagesDidReceive(_ aMessages: [EMChatMessage]) {
        for message in aMessages {
            let from = message.from
            let to = message.to
            let body = message.body as? EMTextMessageBody
            let text = body?.text
            callMessagePrint("messagesDidReceive from: \(from), to: \(to), text: \(String(describing: text))")
            if let text = text, let data = text.data(using: .utf8), 
                let json = try? JSONSerialization.jsonObject(with: data, options: .mutableContainers) as? [String: Any] {
                for element in delegates.allObjects {
                    element.onMessageReceive(message: json)
                }
            }
        }
    }
}
