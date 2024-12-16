//
//  EMPure1v1RoomViewController.swift
//  CallAPI_Example
//
//  Created by wushengtao on 2024/2/8.
//  Copyright © 2024 Agora. All rights reserved.
//


import UIKit
import CallAPI
import AgoraRtcKit

class EMPure1v1RoomViewController: UIViewController {
    // Current user UID
    // 当前用户UID
    private var currentUid: UInt
    private var prepareConfig: PrepareConfig
    var videoEncoderConfig: AgoraVideoEncoderConfiguration?
    
    private let api = CallApiImpl()
    private lazy var leftView: UIView = {
        let view = UIView()
        view.backgroundColor = .white
        return view
    }()
    private let rightView: UIView = {
        let view = UIView()
        view.backgroundColor = .white
        return view
    }()
    private lazy var rtcEngine = _createRtcEngine()
    private lazy var signalClient = _createSignalClient()
    private var emToken: String = ""
    
    private var connectedUserId: UInt?
    private var connectedRoomId: String?
    
    private var callState: CallStateType = .idle {
        didSet {
            switch callState {
            case .calling:
                self.rightView.isHidden = false
                hangupButton.isHidden = true
                callButton.isHidden = true
            case .connected:
                muteAudioButton.isSelected = false
                muteAudioButton.isHidden = false
                muteVideoButton.isSelected = false
                muteVideoButton.isHidden = false
                self.leftView.isHidden = false
                hangupButton.isHidden = false
            case .prepared, .idle, .failed:
                muteAudioButton.isHidden = true
                muteVideoButton.isHidden = true
                self.leftView.isHidden = true
                self.rightView.isHidden = true
                self.callButton.isHidden = false
                self.hangupButton.isHidden = true
            default:
                break
            }
        }
    }
    
    private var targetUserId: UInt {
        get {
            var uid = UInt(UserDefaults.standard.integer(forKey: "targetUserId"))
            if uid > 0 {
                return uid
            }
            uid = UInt(arc4random_uniform(99999))
            UserDefaults.standard.set(uid, forKey: "targetUserId")
            return uid
        } set {
            UserDefaults.standard.set(newValue, forKey: "targetUserId")
        }
    }
    
    private func _createRtcEngine() ->AgoraRtcEngineKit {
        let config = AgoraRtcEngineConfig()
        config.appId = KeyCenter.AppId
        config.channelProfile = .liveBroadcasting
        config.audioScenario = .gameStreaming
        config.areaCode = .global
        let engine = AgoraRtcEngineKit.sharedEngine(with: config,
                                                    delegate: self)
        
        engine.setClientRole(.broadcaster)
        return engine
    }
    
    private func _createSignalClient() -> CallEasemobSignalClient {
        let signalClient = CallEasemobSignalClient(appKey: KeyCenter.IMAppKey, userId: "\(currentUid)")
        signalClient.delegate = self
        return signalClient
    }
    
    private lazy var currentUserLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.text = "\(NSLocalizedString("current_user_id", comment: "")): \(currentUid)"
        return label
    }()
    
    private lazy var targetUserLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.text = NSLocalizedString("target_user_id", comment: "")
        return label
    }()
    
    private lazy var targetUserTextField: UITextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = NSLocalizedString("call_user_id", comment: "")
        tf.textColor = .black
        tf.keyboardType = .numberPad
        tf.addTarget(self, action: #selector(targetUserChanged), for: .editingChanged)
        return tf
    }()
    
    private lazy var closeButton: UIButton = {
        let button = UIButton(type: .custom)
        button.setImage(UIImage(named: "show_live_close"), for: .normal)
        button.addTarget(self, action: #selector(closeAction), for: .touchUpInside)
        return button
    }()
    
    private lazy var callButton: UIButton = {
        let btn = UIButton()
        btn.addTarget(self, action: #selector(callAction), for: .touchUpInside)
        btn.setImage(UIImage(named: "show_live_link"), for: .normal)
        return btn
    }()
    
    private lazy var hangupButton: UIButton = {
        let btn = UIButton()
        btn.addTarget(self, action: #selector(hangupAction), for: .touchUpInside)
        btn.setImage(UIImage(named: "show_live_link_disable"), for: .normal)
        return btn
    }()
    
    private lazy var muteAudioButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.addTarget(self, action: #selector(muteAudioAction), for: .touchUpInside)
        btn.setTitle(NSLocalizedString("call_audio_off", comment: ""), for: .normal)
        btn.setTitle(NSLocalizedString("call_audio_on", comment: ""), for: .selected)
        return btn
    }()
    
    private lazy var muteVideoButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.addTarget(self, action: #selector(muteVideoAction), for: .touchUpInside)
        btn.setTitle(NSLocalizedString("call_video_off", comment: ""), for: .normal)
        btn.setTitle(NSLocalizedString("call_video_on", comment: ""), for: .selected)
        return btn
    }()
    
    private lazy var canvas: AgoraRtcVideoCanvas = {
        let canvas = AgoraRtcVideoCanvas()
        canvas.mirrorMode = .disabled
        return canvas
    }()
    
    deinit {
        NSLog("deinit-- Pure1v1RoomViewController")
    }
    
    required init(currentUid: UInt, prepareConfig: PrepareConfig) {
        self.currentUid = currentUid
        self.prepareConfig = prepareConfig
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        view.backgroundColor = .black
        targetUserTextField.text = "\(targetUserId)"
        view.addSubview(currentUserLabel)
        view.addSubview(targetUserLabel)
        view.addSubview(targetUserTextField)
        view.addSubview(leftView)
        view.addSubview(rightView)
        
        view.addSubview(closeButton)
        view.addSubview(callButton)
        view.addSubview(hangupButton)
        view.addSubview(muteAudioButton)
        view.addSubview(muteVideoButton)
        
        currentUserLabel.frame = CGRect(x: 10, y: 80, width: 200, height: 40)
        targetUserLabel.sizeToFit()
        targetUserTextField.frame = CGRect(x: 10 + targetUserLabel.frame.width + 10, y: 120, width: 200, height: 40)
        targetUserLabel.frame = CGRect(x: 10, y: 120, width: targetUserLabel.frame.width, height: 40)
        
        closeButton.frame = CGRect(x: view.frame.width - 50, y: UIDevice.current.safeDistanceTop, width: 40, height: 40)
        let top = view.frame.height - UIDevice.current.safeDistanceBottom - 40
        callButton.frame = CGRect(x: view.frame.width - 50, y: top, width: 40, height: 40)
        hangupButton.frame = callButton.frame
        
        muteAudioButton.frame = CGRect(x: view.frame.width - 96,
                                       y: callButton.frame.minY - 54,
                                       width: 80,
                                       height: 44)
        muteVideoButton.frame = CGRect(x: view.frame.width - 96,
                                       y: muteAudioButton.frame.minY - 54,
                                       width: 80,
                                       height: 44)
        
        leftView.frame = CGRect(x: 0, y: 50, width: view.frame.width / 2, height: view.frame.height / 2)
        rightView.frame = CGRect(x: view.frame.width / 2, y: 50, width: view.frame.width / 2, height: view.frame.height / 2)
        
        self.callState = .idle
        initCallApi { success in
        }
    }
    
    private func initCallApi(completion: @escaping ((Bool)->())) {
        // External creation requires managing login by oneself
        // 外部创建需要自己管理登录
        self.signalClient.login() {[weak self] err in
            guard let self = self else {return}
            if let err = err {
                NSLog("login error = \(err.localizedDescription)")
                completion(false)
                return
            }
            self._initialize(completion: completion)
        }
        
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        self.view.endEditing(true)
    }
}

extension EMPure1v1RoomViewController {
    private func _initialize(completion: @escaping ((Bool)->())) {
        let config = CallConfig()
        config.appId = KeyCenter.AppId
        config.userId = currentUid
        config.rtcEngine = rtcEngine
        config.signalClient = self.signalClient
        self.api.initialize(config: config)
        prepareConfig.roomId = "\(currentUid)"
        prepareConfig.localView = rightView
        prepareConfig.remoteView = leftView
        api.addListener(listener: self)
        api.prepareForCall(prepareConfig: prepareConfig) { err in
            completion(err == nil)
        }
    }
    
    private func _checkConnectionAndNotify() -> Bool{
        // If the signaling state is abnormal, callapi operations are not allowed
        // 如果信令状态异常，不允许进行callapi操作
        guard signalClient.isConnected == true else {
            AUIToast.show(text: NSLocalizedString("easemob_connect_fail", comment: ""))
            return false
        }
        
        return true
    }
    
    @objc func closeAction() {
        api.deinitialize {
            self.api.removeListener(listener: self)
            self.rtcEngine.stopPreview()
            self.rtcEngine.delegate = nil
            self.rtcEngine.leaveChannel()
            AgoraRtcEngineKit.destroy()
            self.signalClient.delegate = nil
            self.signalClient.logout()
            self.dismiss(animated: true)
        }
    }

    @objc func callAction() {
        guard _checkConnectionAndNotify() else { return }
        
        guard self.callState == .prepared else {
            initCallApi { err in
            }
            AUIToast.show(text: NSLocalizedString("callapi_initialize", comment: ""))
            return
        }
        
        let remoteUserId = targetUserId
        
        let alertController = UIAlertController(title: NSLocalizedString("call", comment: ""),
                                                message: NSLocalizedString("select_call_type", comment: ""),
                                                preferredStyle: .actionSheet)
        // Add video call button
        let action1 = UIAlertAction(title: NSLocalizedString("video_call", comment: ""), style: .default) {[weak self] _ in
            self?.api.call(remoteUserId: remoteUserId) { error in
                guard let error = error, self?.callState == .calling else {return}
                self?.api.cancelCall { err in }
                
                AUIToast.show(text: "\(NSLocalizedString("call_fail", comment: "")): \(error.localizedDescription)")
            }
        }
        alertController.addAction(action1)

        // Add audio call button
        let action2 = UIAlertAction(title: NSLocalizedString("audio_call", comment: ""), style: .default) {[weak self] _ in
            self?.api.call(remoteUserId: remoteUserId,
                           callType: .audio,
                           callExtension: ["test_call": 111]) { error in
                guard let error = error, self?.callState == .calling else {return}
                self?.api.cancelCall { err in }
                
                AUIToast.show(text: "\(NSLocalizedString("call_fail", comment: "")): \(error.localizedDescription)")
            }
        }
        alertController.addAction(action2)

        // Add cancel action
        let cancelAction = UIAlertAction(title: NSLocalizedString("cancel", comment: ""), style: .cancel, handler: nil)
        alertController.addAction(cancelAction)
        present(alertController, animated: true)
    }
    
    @objc func hangupAction() {
        guard _checkConnectionAndNotify() else { return }
        
        api.hangup(remoteUserId: connectedUserId ?? 0, reason: "hangup by user") { error in
        }
    }
    
    @objc func muteVideoAction() {
        guard let roomId = connectedRoomId else { return }
        muteVideoButton.isSelected = !muteVideoButton.isSelected
        let connection = AgoraRtcConnection(channelId: roomId, localUid: Int(currentUid))
        var ret: Int32 = 0
        if (muteVideoButton.isSelected) {
            rtcEngine.stopPreview()
            ret = rtcEngine.muteLocalVideoStreamEx(true, connection: connection)
        } else {
            rtcEngine.startPreview()
            ret = rtcEngine.muteLocalVideoStreamEx(false, connection: connection)
        }
        print("muteVideoAction ret: \(ret)")
    }
    
    @objc func muteAudioAction() {
        guard let roomId = connectedRoomId else { return }
        muteAudioButton.isSelected = !muteAudioButton.isSelected
        let connection = AgoraRtcConnection(channelId: roomId, localUid: Int(currentUid))
        var ret: Int32 = 0
        if (muteAudioButton.isSelected) {
            ret = rtcEngine.muteLocalAudioStreamEx(true, connection: connection)
        } else {
            ret = rtcEngine.muteLocalAudioStreamEx(false, connection: connection)
        }
        print("muteAudioAction ret: \(ret)")
    }
}

extension EMPure1v1RoomViewController {
    @objc func targetUserChanged() {
        targetUserId = UInt(targetUserTextField.text ?? "") ?? 0
    }
}

extension EMPure1v1RoomViewController: AgoraRtcEngineDelegate {
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        NSLog("didJoinedOfUid: \(uid)")
    }
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        NSLog("didJoinChannel: \(channel) uid: \(uid)")
    }
}


extension EMPure1v1RoomViewController:CallApiListenerProtocol {
    func tokenPrivilegeWillExpire() {
        // Update token; 
        NetworkManager.shared.generateToken(channelName: "",
                                            uid: "\(currentUid)",
                                            types: [.rtc]) {[weak self] token in
            guard let self = self else {return}
            guard let token = token else {
                print("generateTokens fail")
                return
            }
            let rtcToken = token
            self.prepareConfig.rtcToken = rtcToken
            self.api.renewToken(with: rtcToken)
        }
    }
    
    private func getCostInfo(map: [String: Int]) -> String {
        var costStr: String = ""
        let array: [(String, Int)] = map.map { ($0.key, $0.value)}.sorted { $0.1 < $1.1}
        array.forEach { (key, value) in
            costStr.append("\(key): \(value) ms\n")
        }
        return costStr
    }
    
    public func onCallStateChanged(with state: CallStateType,
                                   stateReason: CallStateReason,
                                   eventReason: String,
                                   eventInfo: [String : Any]) {
        NSLog("onCallStateChanged state: \(state.rawValue), stateReason: \(stateReason.rawValue), eventReason: \(eventReason), eventInfo: \(eventInfo)")
        
        self.callState = state
        
        switch state {
        case .calling:
            let fromUserId = eventInfo[kFromUserId] as? UInt ?? 0
            let fromRoomId = eventInfo[kFromRoomId] as? String ?? ""
            let toUserId = eventInfo[kRemoteUserId] as? UInt ?? 0
            if let connectedUserId = connectedUserId, connectedUserId != fromUserId {
                api.reject(remoteUserId: fromUserId, reason: "already calling") { err in
                }
                return
            }
            connectedRoomId = fromRoomId
            // Only handle if the user triggering the state is oneself
            // 仅处理触发状态的用户是自己的情况
            if currentUid == toUserId {
                connectedUserId = fromUserId
                let title = String(format: NSLocalizedString("calling_format", comment: ""),
                                   fromUserId,
                                   NSLocalizedString(stateReason == .remoteAudioCall ? "audio": "video", comment: ""))
//                if prepareConfig.autoAccept == false {
                    AUIAlertView()
                        .isShowCloseButton(isShow: true)
                        .title(title: title)
                        .rightButton(title: NSLocalizedString("accept", comment: ""))
                        .leftButton(title: NSLocalizedString("reject", comment: ""))
                        .leftButtonTapClosure {[weak self] in
                            guard let self = self else { return }
                            guard self._checkConnectionAndNotify() else { return }
                            self.api.reject(remoteUserId: fromUserId, reason: "reject by user") { err in
                            }
                        }
                        .rightButtonTapClosure(onTap: {[weak self] text in
                            guard let self = self else { return }
                            guard self._checkConnectionAndNotify() else { return }
                            self.api.accept(remoteUserId: fromUserId) {[weak self] err in
                                guard let err = err else { return }
                                // If there is an error accepting the message, initiate a rejection and return to the initial state
                                // 如果接受消息出错，发起拒绝并返回初始状态
                                self?.api.reject(remoteUserId: fromUserId, reason: err.localizedDescription, completion: { err in
                                })
                                
                                AUIToast.show(text: "\(NSLocalizedString("accept_fail", comment: "")): \(err.localizedDescription)")
                            }
                        })
                        .show()
//                }
            } else if currentUid == fromUserId {
                connectedUserId = toUserId
//                if prepareConfig.autoAccept == false {
                    AUIAlertView()
                        .isShowCloseButton(isShow: true)
                        .title(title: "\(NSLocalizedString("calling_to_user", comment: "")): \(toUserId)")
                        .rightButton(title: NSLocalizedString("cancel", comment: ""))
                        .rightButtonTapClosure(onTap: {[weak self] text in
                            guard let self = self else { return }
                            guard self._checkConnectionAndNotify() else { return }
                            self.api.cancelCall { err in
                            }
                        })
                        .show()
//                }
            }
            break
        case .connected:
            let costMap = eventInfo[kCostTimeMap] as? [String: Int] ?? [:]
            AUIToast.show(text: getCostInfo(map: costMap), postion: .bottom)
            AUIAlertManager.hiddenView()
            
            //setup configuration after join channel ex
            // 加入频道后设置配置
            if let videoEncoderConfig = videoEncoderConfig {
                rtcEngine.setVideoEncoderConfiguration(videoEncoderConfig)
                let cameraConfig = AgoraCameraCapturerConfiguration()
                cameraConfig.cameraDirection = .front
                cameraConfig.dimensions = videoEncoderConfig.dimensions
                cameraConfig.frameRate = Int32(videoEncoderConfig.frameRate.rawValue)
                rtcEngine.setCameraCapturerConfiguration(cameraConfig)
            }
        case .prepared:
            switch stateReason {
            case .localHangup, .remoteHangup:
                AUIToast.show(text: NSLocalizedString("call_did_finish", comment: ""), postion: .bottom)
            case .localRejected, .remoteRejected:
                AUIToast.show(text: NSLocalizedString("call_did_reject", comment: ""))
            case .callingTimeout:
                AUIToast.show(text: NSLocalizedString("call_timeout", comment: ""))
            case .localCancelled, .remoteCancelled:
                AUIToast.show(text: NSLocalizedString("call_is_cancel", comment: ""))
            case .remoteCallBusy:
                AUIToast.show(text: NSLocalizedString("call_is_busy", comment: ""))
            default:
                break
            }
            AUIAlertManager.hiddenView()
            connectedUserId = nil
            connectedRoomId = nil
        case .failed:
            AUIToast.show(text: eventReason, postion: .bottom)
            AUIAlertManager.hiddenView()
            connectedUserId = nil
            connectedRoomId = nil
            closeAction()
        default:
            break
        }
    }
    
    @objc func onCallEventChanged(with event: CallEvent, eventReason: String?) {
        NSLog("onCallEventChanged: \(event.rawValue), eventReason: \(eventReason ?? "")")
        switch event {
        case .remoteLeft:
            // The demo ends abnormal calls by listening for remote user departures. In real business scenarios, it is recommended to use the server to monitor RTC user disconnections for kicking users, while the client listens for kicks to end abnormal calls.
            // 演示通过监听远端用户离开来结束异常通话。在实际业务场景中，建议使用服务器监控RTC用户断开连接来踢用户，同时客户端监听踢人结束异常通话。            
            hangupAction()
        default:
            break
        }
    }
    
    @objc func onCallError(with errorEvent: CallErrorEvent,
                           errorType: CallErrorCodeType,
                           errorCode: Int,
                           message: String?) {
        NSLog("onCallErrorOccur errorEvent:\(errorEvent.rawValue), errorType: \(errorType.rawValue), errorCode: \(errorCode), message: \(message ?? "")")
        if errorEvent == .rtcOccurError, errorType == .rtc, errorCode == AgoraErrorCode.tokenExpired.rawValue {
            // Failed to join RTC channel, need to cancel the call and re-obtain the token
            // 加入RTC频道失败，需要取消通话并重新获取token
            self.api.cancelCall { err in
            }
        }
    }
    
    @objc func callDebugInfo(message: String, logLevel: CallLogLevel) {
        switch logLevel {
        case .normal:
            print("[CallApi]\(message)")
        case .warning:
            print("[CallApi][Warning]\(message)")
        default:
            print("[CallApi][Error]\(message)")
        }
    }
}

extension EMPure1v1RoomViewController: ICallEasemobSignalClientListener {
    func onConnected() {
        NSLog("onConnected")
        AUIToast.show(text: NSLocalizedString("easemob_did_connected", comment: ""))
    }
    
    func onDisconnected() {
        NSLog("onDisconnected")
        AUIToast.show(text: NSLocalizedString("easemob_not_connected", comment: ""))
    }
}
