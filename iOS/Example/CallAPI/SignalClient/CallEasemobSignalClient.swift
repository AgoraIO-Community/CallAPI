//
//  CallEasemobSignalClient.swift
//  CallAPI
//
//  Created by wushengtao on 2024/2/8.
//

import Foundation
import HyphenateChat
import CallAPI

public class CallEasemobSignalClient: NSObject {
    public var isConnected: Bool = false
    private let delegates:NSHashTable<ISignalClientListener> = NSHashTable<ISignalClientListener>.weakObjects()
    private var appKey: String
    private var userId: String

    public init(appKey: String, userId: String) {
        self.appKey = appKey
        self.userId = userId
        super.init()
        
        let option = EMOptions(appkey: appKey)
        EMClient.shared().initializeSDK(with: option)
    }
}

//MARK: public
extension CallEasemobSignalClient {
    public func login(completion: @escaping ((NSError?)->())) {
        let uid = userId
        let pwd = uid
        let date = Date()
        callMessagePrint("register start")
        EMClient.shared().chatManager?.add(self, delegateQueue: nil)
        EMClient.shared().add(self, delegateQueue: DispatchQueue.main)
        EMClient.shared().register(withUsername: uid, password: pwd) {[weak self] username, err in
            if let err = err, err.code != EMErrorCode.userAlreadyExist {
                self?.callMessagePrint("register fail: \(String(describing: err.errorDescription))")
                completion(NSError(domain: err.errorDescription, code: err.code.rawValue))
                return
            }
            self?.callMessagePrint("login start")
            EMClient.shared().login(withUsername: uid, password: pwd) { username, err in
                guard let self = self else { return }
                if let err = err, err.code != EMErrorCode.userAlreadyLoginSame {
                    self.callMessagePrint("login fail: \(String(describing: err.errorDescription))")
                    completion(NSError(domain: err.errorDescription, code: err.code.rawValue))
                    return
                }
                self.callMessagePrint("login success \(-date.timeIntervalSinceNow * 1000) ms")
                completion(nil)
            }
        }
    }
    
    public func logout() {
        let date = Date()
        EMClient.shared().removeDelegate(self)
        EMClient.shared().chatManager?.remove(self)
        EMClient.shared().logout(false)
        print("logout success: \(-date.timeIntervalSinceNow * 1000) ms")
    }
    
    public func renew(token: String) {
        EMClient.shared().renewToken(token)
    }
}

//MARK: private
extension CallEasemobSignalClient {
    private func callMessagePrint(_ message: String, _ logLevel: Int = 0) {
        let tag = "[CallEasemobMessageManager]"
        for element in delegates.allObjects {
            element.debugInfo?(message: "\(tag)\(message)", logLevel: logLevel)
        }
    }
    
    private func _sendMessage(userId: String,
                              message: String,
                              completion: ((NSError?)-> Void)?) {
        if userId.count == 0 {
            completion?(NSError(domain: "send message fail! roomId is empty", code: -1))
            return
        }
        
        let body = EMTextMessageBody(text: message)
        callMessagePrint("_sendMessage to '\(userId)', message: \(message)")
        let emMessage = EMChatMessage(conversationID: userId, from: self.userId, to: userId, body: body, ext: nil)
        let date = Date()
        EMClient.shared().chatManager?.send(emMessage, progress: nil, completion: {[weak self] msg, err in
            guard let self = self else {return}
            if let err = err {
                let error = NSError(domain: err.errorDescription, code: err.code.rawValue)
                self.callMessagePrint("_sendMessage fail: \(error) cost: \(date.getCostMilliseconds()) ms", 1)

                completion?(error)
                return
            }
            self.callMessagePrint("_sendMessage publish cost \(date.getCostMilliseconds()) ms")
            completion?(nil)
        })
    }
}

//MARK: ICallMessageManager
extension CallEasemobSignalClient: ISignalClient {
    public func sendMessage(userId: String,
                            message: String, 
                            completion: ((NSError?) -> Void)?) {
        guard userId.count > 0, userId != "0" else {
            let errorStr = "sendMessage fail, invalid userId[\(userId)]"
            callMessagePrint(errorStr)
            completion?(NSError(domain: errorStr, code: -1))
            return
        }

        _sendMessage(userId: userId,
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
        logout()
    }
}

//MARK: EMChatManagerDelegate
extension CallEasemobSignalClient: EMChatManagerDelegate {
    public func messagesDidReceive(_ aMessages: [EMChatMessage]) {
        for message in aMessages {
            let from = message.from
            let to = message.to
            let body = message.body as? EMTextMessageBody
            let text = body?.text
            callMessagePrint("messagesDidReceive from: \(from), to: \(to), text: \(String(describing: text))")
            if let text = text {
                for element in delegates.allObjects {
                    element.onMessageReceive(message: text)
                }
            }
        }
    }
}

extension CallEasemobSignalClient: EMClientDelegate {
    public func connectionStateDidChange(_ aConnectionState: EMConnectionState) {
        print("connectionStateDidChange: \(aConnectionState.rawValue)")
        switch aConnectionState {
        case .connected:
            isConnected = true
        default:
            isConnected = false
        }
    }
}
