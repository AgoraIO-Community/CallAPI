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
import AgoraRtmKit

class Pure1v1RoomViewController: UIViewController {
    private var currentUid: UInt             //当前用户UID
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
    private var rtmClient: AgoraRtmClientKit?
    
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
                self.leftView.isHidden = false
                hangupButton.isHidden = false
            case .prepared, .idle, .failed:
                muteAudioButton.isHidden = true
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
    
    private lazy var muteAudioButton: UIButton = {
        let btn = UIButton()
        btn.addTarget(self, action: #selector(muteAudioAction), for: .touchUpInside)
        btn.setImage(UIImage(named: "mic_unmute"), for: .normal)
        btn.setImage(UIImage(named: "mic_mute"), for: .selected)
        return btn
    }()
    
    private lazy var connectStatusLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.font = UIFont.systemFont(ofSize: 12)
        label.numberOfLines = 0
        return label
    }()
    
    private lazy var canvas: AgoraRtcVideoCanvas = {
        let canvas = AgoraRtcVideoCanvas()
        canvas.mirrorMode = .disabled
        return canvas
    }()
    
    deinit {
        print("deinit-- Pure1v1RoomViewController")
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
        
        view.addSubview(connectStatusLabel)
        
        currentUserLabel.frame = CGRect(x: 10, y: 80, width: 200, height: 40)
        targetUserLabel.sizeToFit()
        targetUserTextField.frame = CGRect(x: 10 + targetUserLabel.frame.width + 10, y: 120, width: 200, height: 40)
        targetUserLabel.frame = CGRect(x: 10, y: 120, width: targetUserLabel.frame.width, height: 40)
        
        closeButton.frame = CGRect(x: view.frame.width - 50, y: UIDevice.current.safeDistanceTop, width: 40, height: 40)
        let top = view.frame.height - UIDevice.current.safeDistanceBottom - 40
        callButton.frame = CGRect(x: view.frame.width - 50, y: top, width: 40, height: 40)
        hangupButton.frame = callButton.frame
        
        muteAudioButton.frame = CGRect(x: callButton.frame.origin.x - callButton.frame.size.width - 10,
                                       y: top,
                                       width: callButton.frame.width,
                                       height: callButton.frame.height)
        
        leftView.frame = CGRect(x: 0, y: 50, width: view.frame.width / 2, height: view.frame.height / 2)
        rightView.frame = CGRect(x: view.frame.width / 2, y: 50, width: view.frame.width / 2, height: view.frame.height / 2)
        
        self.callState = .idle
        //外部创建rtmClient
        rtmClient = _createRtmClient()
        initCallApi { success in
        }
    }
    
    private func initCallApi(completion: @escaping ((Bool)->())) {
        //外部创建需要自行管理login
        NSLog("login")
        rtmClient?.login(prepareConfig.rtmToken) {[weak self] resp, err in
            guard let self = self else {return}
            if let err = err {
                NSLog("login error = \(err.localizedDescription)")
                completion(false)
                return
            }
            self._initialize(rtmClient: self.rtmClient, completion: completion)
        }
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        self.view.endEditing(true)
    }
}

extension Pure1v1RoomViewController {
    private func _initialize(rtmClient: AgoraRtmClientKit?, completion: @escaping ((Bool)->())) {
        let config = CallConfig()
        config.appId = KeyCenter.AppId
        config.userId = currentUid
        config.rtcEngine = rtcEngine
        config.rtmClient = rtmClient
        self.api.initialize(config: config)
        prepareConfig.roomId = "\(currentUid)"
        prepareConfig.localView = rightView
        prepareConfig.remoteView = leftView
        api.addListener(listener: self)
        api.prepareForCall(prepareConfig: prepareConfig) { err in
            completion(err == nil)
        }
    }
    
    @objc func closeAction() {
        api.deinitialize {
            self.api.removeListener(listener: self)
            self.rtcEngine.stopPreview()
            self.rtcEngine.delegate = nil
            self.rtcEngine.leaveChannel()
            AgoraRtcEngineKit.destroy()
            self.rtmClient?.logout()
            self.rtmClient?.destroy()
            self.dismiss(animated: true)
        }
    }

    @objc func callAction() {
        guard self.callState == .prepared else {
            initCallApi { err in
            }
            AUIToast.show(text: "CallAPi初始化中")
            return
        }
        api.call(remoteUserId: targetUserId) {[weak self] error in
            guard let _ = error, self?.callState == .calling else {return}
            self?.api.cancelCall(completion: { err in
            })
        }
    }
    
    @objc func hangupAction() {
        api.hangup(remoteUserId: connectedUserId ?? 0, reason: "hangup by user") { error in
        }
    }
    
    @objc func muteAudioAction() {
        guard let roomId = connectedRoomId else { return }
        muteAudioButton.isSelected = !muteAudioButton.isSelected
        
        let connection = AgoraRtcConnection(channelId: roomId, localUid: Int(currentUid))
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.publishMicrophoneTrack = muteAudioButton.isSelected == false ? true : false
        rtcEngine.updateChannelEx(with: mediaOptions, connection: connection)
    }
    
    //创建RTM
    private func _createRtmClient() -> AgoraRtmClientKit {
        let rtmConfig = AgoraRtmClientConfig(appId: KeyCenter.AppId, userId: "\(currentUid)")
        if rtmConfig.userId.count == 0 {
            print("userId is empty")
        }
        if rtmConfig.appId.count == 0 {
            print("appId is empty")
        }

        var rtmClient: AgoraRtmClientKit? = nil
        do {
            rtmClient = try AgoraRtmClientKit(rtmConfig, delegate: nil)
        } catch {
            print("create rtm client fail: \(error.localizedDescription)")
        }
        return rtmClient!
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
    func tokenPrivilegeWillExpire() {
        //更新token
        NetworkManager.shared.generateTokens(channelName: "",
                                             uid: "\(currentUid)",
                                             tokenGeneratorType: .token007,
                                             tokenTypes: [.rtc, .rtm]) {[weak self] tokens in
            guard let self = self else {return}
            let rtcToken = tokens[AgoraTokenType.rtc.rawValue]!
            self.prepareConfig.rtcToken = rtcToken
            let rtmToken = tokens[AgoraTokenType.rtm.rawValue]!
            self.prepareConfig.rtmToken = rtmToken
            self.api.renewToken(with: rtcToken, rtmToken: rtmToken)
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
        print("onCallStateChanged state: \(state.rawValue), stateReason: \(stateReason.rawValue), eventReason: \(eventReason), eventInfo: \(eventInfo)")
        
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
            // 触发状态的用户是自己才处理
            if currentUid == toUserId {
                connectedUserId = fromUserId
//                if prepareConfig.autoAccept == false {
                    AUIAlertView()
                        .isShowCloseButton(isShow: true)
                        .title(title: "用户 \(fromUserId) 邀请您1对1通话")
                        .rightButton(title: "同意")
                        .leftButton(title: "拒绝")
                        .leftButtonTapClosure {[weak self] in
                            guard let self = self else { return }
                            self.api.reject(remoteUserId: fromUserId, reason: "reject by user") { err in
                            }
                        }
                        .rightButtonTapClosure(onTap: {[weak self] text in
                            guard let self = self else { return }
                            self.api.accept(remoteUserId: fromUserId) { err in
                            }
                        })
                        .show()
//                }
            } else if currentUid == fromUserId {
                connectedUserId = toUserId
//                if prepareConfig.autoAccept == false {
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
//                }
            }
            break
        case .connected:
            let costMap = eventInfo[kCostTimeMap] as? [String: Int] ?? [:]
            AUIToast.show(text: getCostInfo(map: costMap), postion: .bottom)
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
            case .remoteCallBusy:
                AUIToast.show(text: "用户正忙")
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
        print("onCallEventChanged event: \(event.rawValue), eventReason: \(eventReason ?? "")")
        switch event {
        case .remoteLeave:
            hangupAction()
        default:
            break
        }
    }
    
    @objc func onCallError(with errorEvent: CallErrorEvent,
                           errorType: CallErrorCodeType,
                           errorCode: Int,
                           message: String?) {
        print("onCallErrorOccur errorEvent:\(errorEvent.rawValue), errorType: \(errorType.rawValue), errorCode: \(errorCode), message: \(message ?? "")")
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
    
    @objc func onCallConnected(roomId: String,
                               callUserId: UInt,
                               currentUserId: UInt,
                               timestamp: UInt64) {
        print("onCallConnected roomId: \(roomId) callUserId: \(callUserId) currentUserId: \(currentUserId) timestamp: \(timestamp)")
        
        connectStatusLabel.text = "通话开始 \nRTC 频道号: \(roomId) \n呼叫用户id: \(callUserId) \n当前用户id: \(currentUserId) \n开始时间戳: \(timestamp)"
        layoutConnectStatus()
    }
    
    @objc func onCallDisconnected(roomId: String,
                                  hangupUserId: UInt,
                                  currentUserId: UInt,
                                  timestamp: UInt64,
                                  duration: UInt64) {
        print("onCallDisconnected roomId: \(roomId) hangupUserId: \(hangupUserId) currentUserId: \(currentUserId) timestamp: \(timestamp) duration: \(duration)ms")
        
        connectStatusLabel.text = "通话结束 \nRTC 频道号: \(roomId) \n挂断用户id: \(hangupUserId) \n当前用户id: \(currentUserId) \n结束时间戳: \(timestamp) \n通话时长: \(duration)ms"
        layoutConnectStatus()
    }
    
    private func layoutConnectStatus() {
        connectStatusLabel.frame = self.view.bounds
        connectStatusLabel.sizeToFit()
        connectStatusLabel.frame = CGRect(x: 20,
                                          y: self.view.frame.height - connectStatusLabel.frame.height - 40,
                                          width: connectStatusLabel.frame.width,
                                          height: connectStatusLabel.frame.height)
    }
}
