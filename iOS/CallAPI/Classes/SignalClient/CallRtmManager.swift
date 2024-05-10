//
//  CallRtmManager.swift
//  CallAPI
//
//  Created by wushengtao on 2024/2/27.
//

import Foundation
import AgoraRtmKit

let kUserCallStateKey = "callState"

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

/// CallRtmManager回调协议
@objc public protocol ICallRtmManagerListener: NSObjectProtocol {
    
    /// rtm连接成功
    func onConnected()
    
    /// rtm连接断开
    func onDisconnected()
    
    /// token即将过期，需要renew token
    /// - Parameter channelName: 即将过期的频道名
    func onTokenPrivilegeWillExpire(channelName: String)
}

@objcMembers
public class CallRtmManager: NSObject {
    public var isConnected: Bool = false
    private var userId: String
    public weak var delegate: ICallRtmManagerListener?
    
    private var rtmClient: AgoraRtmClientKit
    
    /// RTM是否已经登录
    private var isLoginedRtm: Bool = false
    
    /// 是否外部传入的rtm，如果是则不需要手动logout
    private var isExternalRtmClient: Bool = false
    
    deinit {
        self.rtmClient.removeDelegate(self)
    }
    
    
    /// 初始化
    /// - Parameters:
    ///   - appId: 声网AppId
    ///   - userId: 用户id
    ///   - rtmClient: [可选]声网实时消息(Rtm)实例，传空则CallRtmManager内部自行创建
     @objc public required init(appId: String, userId: String, rtmClient: AgoraRtmClientKit? = nil) {
        self.userId = userId
        if let rtmClient = rtmClient {
            //如果外部传入rtmclient，默认登陆成功
            self.isLoginedRtm = true
            self.isExternalRtmClient = true
            self.rtmClient = rtmClient
            self.isConnected = true
        } else {
            self.rtmClient = createRtmClient(appId: appId, userId: userId)
        }
        super.init()
        
        self.rtmClient.addDelegate(self)
        
        // disable retry message
        let _ = self.rtmClient.setParameters("{\"rtm.msg.tx_timeout\": 3000}")
        callMessagePrint("init-- CallMessageManager ")
    }
    
    
    /// 获取到rtm实例，使用该方法获取到后传递给CallRtmSignalClient
    /// - Returns: rtm实例对象
    @objc public func getRtmClient() -> AgoraRtmClientKit {
        return rtmClient
    }
    
    
    /// rtm登录
    /// - Parameters:
    ///   - rtmToken: rtm token
    ///   - completion: 完成回调
    @objc public func login(rtmToken: String, completion: @escaping ((NSError?) -> ())) {
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
    
    
    /// 登出
    @objc public func logout() {
        if isExternalRtmClient == false {
            rtmClient.logout()
            rtmClient.destroy()
            self.isConnected = false
        }
    }
    
    /// 更新RTM token
    /// - Parameter tokenConfig: CallTokenConfig
    @objc public func renewToken(rtmToken: String) {
        guard isLoginedRtm else {
            //没有登陆成功，但是需要自动登陆，可能是初始token问题，这里重新initialize
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
    
    @objc public func joinChannel(channelName: String?, completion: @escaping(NSError?)-> ()) {
        let currentUserChannelName = channelName ?? userId
        let options = AgoraRtmSubscribeOptions()
        options.features = [.presence]
        callMessagePrint("will joinChannel[\(currentUserChannelName)]")
        rtmClient.subscribe(channelName: currentUserChannelName, option: options) { resp, err in
            callMessagePrint("joinChannel[\(currentUserChannelName)] completion: \(err?.localizedDescription ?? "success")")
            completion(err)
        }
    }
    
    @objc public func leaveChannel(channelName: String?) {
        let currentUserChannelName = channelName ?? userId
        rtmClient.unsubscribe(currentUserChannelName)
    }
    
    @objc public func setCallState(channelName: String?, state: CallStateType, completion: @escaping (NSError?)->()) {
        let currentUserChannelName = channelName ?? userId
        callMessagePrint("will setCallState[\(currentUserChannelName)] '\(state.rawValue)'")
        rtmClient.getPresence()?.setState(channelName: currentUserChannelName,
                                          channelType: .message,
                                          items: [kUserCallStateKey: "\(state.rawValue)"]) { resp, err in
            callMessagePrint("setCallState[\(currentUserChannelName)] '\(state.rawValue)' completion \(err?.localizedDescription ?? "success")")
            completion(err)
        }
    }
    
    @objc public func getCallState(userChannelName: String?,
                                   userId: String,
                                   completion: @escaping (NSError?, CallStateType)->()) {
        let channelName = userChannelName ?? userId
        callMessagePrint("will getCallState[\(channelName)]")
        rtmClient.getPresence()?.getState(channelName: channelName,
                                          channelType: .message,
                                          userId: userId) { resp, err in
            callMessagePrint("getCallState[\(channelName)] completion \(err?.localizedDescription ?? "success")")
            if let err = err {
                completion(err, .idle)
                return
            }
            guard let states = resp?.state.states else {
                completion(nil, .idle)
                return
            }
            let stateRaw = UInt(states[kUserCallStateKey] as? String ?? "") ?? UInt.max
            let state = CallStateType(rawValue: stateRaw) ?? .idle
            callMessagePrint("getCallState[\(channelName)] \(stateRaw)")
            completion(err, state)
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
