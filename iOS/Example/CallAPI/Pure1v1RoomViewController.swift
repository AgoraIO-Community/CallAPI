//
//  Pure1v1RoomViewController.swift
//  CallAPI_Example
//
//  Created by wushengtao on 2023/7/3.
//  Copyright © 2023 Agora. All rights reserved.
//

import UIKit
import CallAPI
import AgoraRtcKit

class Pure1v1RoomViewController: UIViewController {
    var currentUid: UInt = 0             //当前用户UID
    var tokenConfig: CallTokenConfig?
    var videoEncoderConfig: AgoraVideoEncoderConfiguration?
    
    private let api = CallApiImpl()
    private let leftView: UIView = UIView()
    private let rightView: UIView = UIView()
    private lazy var rtcEngine = _createRtcEngine()
    
    private var connectedUserId: UInt?
    
    private var callState: CallStateType = .idle {
        didSet {
            switch callState {
            case .calling:
                self.leftView.isHidden = false
                self.rightView.isHidden = false
                hangupButton.isHidden = true
                callButton.isHidden = true
            case .connected:
                hangupButton.isHidden = false
            case .prepared, .idle, .failed:
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
    
    private lazy var currentUserLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.text = "当前用户id: \(currentUid)"
        return label
    }()
    
    private lazy var targetUserLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.text = "目标用户id"
        return label
    }()
    
    private lazy var targetUserTextField: UITextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = "需要呼叫的用户id"
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
    
    private lazy var canvas: AgoraRtcVideoCanvas = {
        let canvas = AgoraRtcVideoCanvas()
        canvas.mirrorMode = .disabled
        return canvas
    }()
    
    deinit {
        print("deinit-- Pure1v1RoomViewController")
    }
    
    override init(nibName nibNameOrNil: String?, bundle nibBundleOrNil: Bundle?) {
        super.init(nibName: nibNameOrNil, bundle: nibBundleOrNil)
        print("init-- Pure1v1RoomViewController")
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
        
        currentUserLabel.frame = CGRect(x: 10, y: 80, width: 200, height: 40)
        targetUserLabel.sizeToFit()
        targetUserTextField.frame = CGRect(x: 10 + targetUserLabel.frame.width + 10, y: 120, width: 200, height: 40)
        targetUserLabel.frame = CGRect(x: 10, y: 120, width: targetUserLabel.frame.width, height: 40)
        
        closeButton.frame = CGRect(x: view.frame.width - 50, y: UIDevice.current.safeDistanceTop, width: 40, height: 40)
        let top = view.frame.height - UIDevice.current.safeDistanceBottom - 40
        callButton.frame = CGRect(x: view.frame.width - 50, y: top, width: 40, height: 40)
        hangupButton.frame = callButton.frame
        
        leftView.frame = CGRect(x: 0, y: 50, width: view.frame.width / 2, height: view.frame.height / 2)
        rightView.frame = CGRect(x: view.frame.width / 2, y: 50, width: view.frame.width / 2, height: view.frame.height / 2)
        leftView.backgroundColor = .black
        rightView.backgroundColor = .black
        
        leftView.isHidden = true
        rightView.isHidden = true
        
        self.callState = .idle
        _initialize() { success in
            print("_initialize: \(success)")
        }
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        self.view.endEditing(true)
    }
}

extension Pure1v1RoomViewController {
    private func _initialize(completion: @escaping ((Bool)->())) {
        let config = CallConfig()
        config.role = .caller   // 纯1v1只能设置成主叫
        config.mode = .pure1v1
        config.appId = KeyCenter.AppId
        config.userId = currentUid
        config.autoAccept = false
        config.rtcEngine = _createRtcEngine()
        config.localView = rightView
        config.remoteView = leftView
        
        self.api.initialize(config: config, token: tokenConfig!) { error in
            // 需要主动调用prepareForCall
            let prepareConfig = PrepareConfig.callerConfig()
            prepareConfig.autoLoginRTM = true
            prepareConfig.autoSubscribeRTM = true
//            prepareConfig.autoJoinRTC = true
            self.api.prepareForCall(prepareConfig: prepareConfig) { err in
                completion(err == nil)
            }
        }
        
        api.addListener(listener: self)
    }
    
    @objc func closeAction() {
        api.deinitialize {
            self.api.removeListener(listener: self)
            self.rtcEngine.stopPreview()
            self.rtcEngine.delegate = nil
            self.rtcEngine.leaveChannel()
            AgoraRtcEngineKit.destroy()
            self.dismiss(animated: true)
        }
    }

    @objc func callAction() {
        api.call(roomId: "\(targetUserId)", remoteUserId: targetUserId) { error in
        }

        leftView.isHidden = false
        rightView.isHidden = false
    }
    
    @objc func hangupAction() {
        api.hangup(roomId: "\(connectedUserId ?? 0)") { error in
        }
        
        leftView.isHidden = true
        rightView.isHidden = true
    }
}

extension Pure1v1RoomViewController {
    @objc func targetUserChanged() {
        targetUserId = UInt(targetUserTextField.text ?? "") ?? 0
    }
}

extension Pure1v1RoomViewController: AgoraRtcEngineDelegate {
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        print("didJoinedOfUid: \(uid)")
    }
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        print("didJoinChannel: \(channel) uid: \(uid)")
    }
}


extension Pure1v1RoomViewController:CallApiListenerProtocol {
    public func onCallStateChanged(with state: CallStateType,
                                   stateReason: CallReason,
                                   eventReason: String,
                                   elapsed: Int,
                                   eventInfo: [String : Any]) {
        let publisher = UInt(eventInfo[kPublisher] as? String ?? "") ?? currentUid
        
        guard publisher == currentUid else {
            return
        }
        print("onCallStateChanged state: \(state.rawValue), stateReason: \(stateReason.rawValue), eventReason: \(eventReason), elapsed: \(elapsed) ms, eventInfo: \(eventInfo) publisher: \(publisher) / \(currentUid)")
        
        self.callState = state
        
        switch state {
        case .calling:
            let fromUserId = eventInfo[kFromUserId] as? UInt ?? 0
            let fromRoomId = eventInfo[kFromRoomId] as? String ?? ""
            let toUserId = eventInfo[kRemoteUserId] as? UInt ?? 0
            if let connectedUserId = connectedUserId, connectedUserId != fromUserId {
                api.reject(roomId: fromRoomId, remoteUserId: fromUserId, reason: "already calling") { err in
                }
                return
            }
            // 触发状态的用户是自己才处理
            if currentUid == toUserId {
                connectedUserId = fromUserId
                AUIAlertView()
                    .isShowCloseButton(isShow: true)
                    .title(title: "用户 \(fromUserId) 邀请您1对1通话")
                    .rightButton(title: "同意")
                    .leftButton(title: "拒绝")
                    .leftButtonTapClosure {[weak self] in
                        guard let self = self else { return }
                        self.api.reject(roomId: fromRoomId, remoteUserId: fromUserId, reason: "reject by user") { err in
                        }
                    }
                    .rightButtonTapClosure(onTap: {[weak self] text in
                        guard let self = self else { return }
                        NetworkManager.shared.generateTokens(channelName: fromRoomId,
                                                             uid: "\(toUserId)",
                                                             tokenGeneratorType: .token007,
                                                             tokenTypes: [.rtc, .rtm]) {[weak self] tokens in
                            guard let self = self else {return}
                            guard tokens.count == 2 else {
                                print("generateTokens fail")
                                self.view.isUserInteractionEnabled = true
                                return
                            }
                            let rtcToken = tokens[AgoraTokenType.rtc.rawValue]!
                            self.api.accept(roomId: fromRoomId, remoteUserId: fromUserId, rtcToken: rtcToken) { err in
                            }
                        }
                    })
                    .show()
                
            } else if currentUid == fromUserId {
                connectedUserId = toUserId
                AUIAlertView()
                    .isShowCloseButton(isShow: true)
                    .title(title: "呼叫用户 \(toUserId) 中")
                    .rightButton(title: "取消")
                    .rightButtonTapClosure(onTap: {[weak self] text in
                        guard let self = self else { return }
                        self.api.cancelCall { err in
                        }
                    })
                    .show()
            }
            break
        case .connected:
            AUIToast.show(text: "通话开始\(eventInfo[kDebugInfo] as? String ?? "")", postion: .bottom)
            AUIAlertManager.hiddenView()
            
            //setup configuration after join channel ex
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
                AUIToast.show(text: "通话结束", postion: .bottom)
            case .localRejected, .remoteRejected:
                AUIToast.show(text: "通话被拒绝")
            case .callingTimeout:
                AUIToast.show(text: "无应答")
            case .localCancel, .remoteCancel:
                AUIToast.show(text: "通话被取消")
            default:
                break
            }
            AUIAlertManager.hiddenView()
            connectedUserId = nil
        case .failed:
            AUIToast.show(text: eventReason, postion: .bottom)
            AUIAlertManager.hiddenView()
            connectedUserId = nil
        default:
            break
        }
    }
    
    @objc func onCallEventChanged(with event: CallEvent, elapsed: Int) {
        print("onCallEventChanged: \(event.rawValue), elapsed: \(elapsed)")
        switch event {
        case .remoteLeave:
            hangupAction()
        default:
            break
        }
    }
    
    @objc func callDebugInfo(message: String) {
        print("[CallApi]\(message)")
    }
    
    @objc func callDebugWarning(message: String) {
        print("[CallApi]\(message)")
    }
}
