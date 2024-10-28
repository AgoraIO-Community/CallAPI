//
//  CallRtmManager.swift
//  CallAPI
//
//  Created by wushengtao on 2024/2/27.
//

import Foundation
import AgoraRtmKit

private func createRtmClient(appId: String, userId: String) -> AgoraRtmClientKit {
    let rtmConfig = AgoraRtmClientConfig(appId: appId, userId: userId)
    var rtmClient: AgoraRtmClientKit? = nil
    do {
        rtmClient = try AgoraRtmClientKit(rtmConfig, delegate: nil)
    } catch {
        callMessagePrint("create rtm client fail: \(error.localizedDescription)")
    }
    return rtmClient!
}

func callMessagePrint(_ message: String) {
    NSLog(message)
}

/// CallRtmManager callback protocol
public protocol ICallRtmManagerListener: NSObjectProtocol {
    
    /// RTM connection succeeded
    func onConnected()
    
    /// RTM connection disconnected
    func onDisconnected()
    
    /// Token is about to expire, need to renew token
    /// - Parameter channelName: The name of the channel that is about to expire
    func onTokenPrivilegeWillExpire(channelName: String)
}

@objcMembers public class CallRtmManager: NSObject {
    public var isConnected: Bool = false
    public weak var delegate: ICallRtmManagerListener?
    
    private var rtmClient: AgoraRtmClientKit

    /// Whether RTM is logged in
    public var isLoginedRtm: Bool = false
    
    /// Whether the RTM is externally provided; if so, manual logout is not required
    private var isExternalRtmClient: Bool = false
    
    deinit {
        self.rtmClient.removeDelegate(self)
    }
    
    
    /// Initialization
    /// - Parameters:
    ///   - appId: Agora App ID
    ///   - userId: User ID
    ///   - rtmClient: [Optional] Agora Real-Time Messaging (RTM) instance; pass nil to have CallRtmManager create it internally
    public required init(appId: String, userId: String, rtmClient: AgoraRtmClientKit? = nil) {
        if let rtmClient = rtmClient {
            // If an external rtmClient is provided, default to logged in
            self.isLoginedRtm = true
            self.isExternalRtmClient = true
            self.rtmClient = rtmClient
            self.isConnected = true
        } else {
            self.rtmClient = createRtmClient(appId: appId, userId: userId)
        }
        super.init()
        
        self.rtmClient.addDelegate(self)
        
        // Disable retry message
        let _ = self.rtmClient.setParameters("{\"rtm.msg.tx_timeout\": 3000}")
        callMessagePrint("init-- CallMessageManager ")
    }
    
    
    /// Retrieves the RTM instance; use this method to pass it to CallRtmSignalClient
    /// - Returns: RTM instance object
    public func getRtmClient() -> AgoraRtmClientKit {
        return rtmClient
    }
    
    
    /// RTM login
    /// - Parameters:
    ///   - rtmToken: RTM token
    ///   - completion: Completion callback
    public func login(rtmToken: String, completion: @escaping ((NSError?) -> ())) {
        callMessagePrint("initialize")
        if rtmToken.isEmpty, isExternalRtmClient == false {
            let reason = "RTM Token is Empty"
            completion(NSError(domain: reason, code: -1))
            return
        }
        
        if !isLoginedRtm {
            loginRtm(rtmClient: rtmClient, token: rtmToken) {/*[weak self]*/ err in
                if let err = err, err.errorCode != .ok, err.errorCode != .duplicateOperation {
                    completion(NSError(domain: err.reason, code: err.errorCode.rawValue))
                    return
                }
                completion(nil)
            }
        } else {
            completion(nil)
        }
    }
    
    
    /// Logout
    public func logout() {
        if isExternalRtmClient == false {
            rtmClient.logout()
            rtmClient.destroy()
            self.isConnected = false
        }
    }
    
    /// Update RTM token
    /// - Parameter tokenConfig: CallTokenConfig
    public func renewToken(rtmToken: String) {
        guard isLoginedRtm else {
            // Not logged in successfully, but needs to auto-login; may be an initial token issue, reinitialize here
            callMessagePrint("renewToken need to reinit")
            self.rtmClient.logout()
            login(rtmToken: rtmToken) { err in
            }
            return
        }
        rtmClient.renewToken(rtmToken, completion: { resp, err in
            callMessagePrint("rtm renewToken: \(err?.errorCode.rawValue ?? 0)")
        })
    }
    
    private func loginRtm(rtmClient: AgoraRtmClientKit,
                          token: String,
                          completion: @escaping (AgoraRtmErrorInfo?)->()) {
        if isLoginedRtm {
            completion(nil)
            return
        }
        rtmClient.logout()
        callMessagePrint("will login")
        rtmClient.login(token) {[weak self] resp, error in
            guard let self = self else {return}
            callMessagePrint("login completion: \(error?.errorCode.rawValue ?? 0)")
            self.isLoginedRtm = error == nil ? true : false
            completion(error)
        }
    }
}

//MARK: AgoraRtmClientDelegate
//TODO: error handler
extension CallRtmManager: AgoraRtmClientDelegate {
    public func rtmKit(_ kit: AgoraRtmClientKit,
                channel channelName: String,
                connectionChangedToState state: AgoraRtmClientConnectionState,
                reason: AgoraRtmClientConnectionChangeReason) {
        callMessagePrint("rtm connectionChangedToState: \(state.rawValue) reason: \(reason.rawValue)")
        if reason == .changedTokenExpired {
            self.delegate?.onTokenPrivilegeWillExpire(channelName: channelName)
        } else if state == .connected {
            if self.isConnected == true { return }
            self.isConnected = true
            self.delegate?.onConnected()
        } else {
            if self.isConnected == false { return }
            self.isConnected = false
            self.delegate?.onDisconnected()
        }
    }

    public func rtmKit(_ rtmKit: AgoraRtmClientKit, tokenPrivilegeWillExpire channel: String?) {
        callMessagePrint("rtm onTokenPrivilegeWillExpire[\(channel ?? "nil")]")
        self.delegate?.onTokenPrivilegeWillExpire(channelName: channel ?? "")
    }
}
