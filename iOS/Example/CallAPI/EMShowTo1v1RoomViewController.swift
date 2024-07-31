//
//  EMShowTo1v1RoomViewController.swift
//  CallAPI_Example
//
//  Created by wushengtao on 2024/2/22.
//  Copyright © 2024 Agora. All rights reserved.
//

import UIKit
import CallAPI
import AgoraRtcKit

class EMShowTo1v1RoomViewController: UIViewController {
    private var showRoomId: String           //直播频道名
    private var showUserId: UInt             //房主uid，如果是主播，那么和currentUid一致
    private var showRoomToken: String        //直播token
    private var currentUid: UInt             //当前用户UID
    private var role: CallRole               //角色
    private var prepareConfig: PrepareConfig
    var videoEncoderConfig: AgoraVideoEncoderConfiguration?
    private var connectedUserId: UInt?
    private var connectedRoomId: String?
    
    private let api = CallApiImpl()
    private let showView: UIView = UIView()
    private let leftView: UIView = {
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
    
    private var callState: CallStateType = .idle {
        didSet {
            switch callState {
            case .calling:
                publishMedia(false)
                setupCanvas(nil)
                self.rightView.isHidden = false
                hangupButton.isHidden = false
                callButton.isHidden = true
            case .connected:
                muteAudioButton.isSelected = false
                muteAudioButton.isHidden = false
                leftView.isHidden = false
                hangupButton.isHidden = false
            case .prepared, .idle, .failed:
                rtcEngine.enableLocalAudio(true)
                rtcEngine.enableLocalVideo(true)
                self.publishMedia(true)
                self.setupCanvas(self.showView)
                muteAudioButton.isHidden = true
                self.leftView.isHidden = true
                self.rightView.isHidden = true
                self.callButton.isHidden = isBroadcaster
                self.hangupButton.isHidden = true
            default:
                break
            }
        }
    }
    
    private var isBroadcaster: Bool {
        return self.role == .callee
    }
    
    private func _createRtcEngine() ->AgoraRtcEngineKit {
        let config = AgoraRtcEngineConfig()
        config.appId = KeyCenter.AppId
        config.channelProfile = .liveBroadcasting
        config.audioScenario = .gameStreaming
        config.areaCode = .global
        let engine = AgoraRtcEngineKit.sharedEngine(with: config,
                                                    delegate: self)
        
        engine.setClientRole(isBroadcaster ? .broadcaster : .audience)
        return engine
    }
    
    private func _createSignalClient() -> CallEasemobSignalClient {
        let signalClient = CallEasemobSignalClient(appKey: KeyCenter.IMAppKey, userId: "\(currentUid)")
        signalClient.delegate = self
        return signalClient
    }
    
    private lazy var roomInfoLabel: UILabel = {
        let label = UILabel()
        label.backgroundColor = .white.withAlphaComponent(0.5)
        label.textColor = .black
        label.layer.cornerRadius = 8
        label.font = UIFont.systemFont(ofSize: 15)
        label.clipsToBounds = true
        return label
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
    
    required init(showRoomId: String,
                  showUserId: UInt,
                  showRoomToken: String,
                  currentUid: UInt,
                  role: CallRole,
                  prepareConfig: PrepareConfig) {
        self.showRoomId = showRoomId
        self.showUserId = showUserId
        self.showRoomToken = showRoomToken
        self.currentUid = currentUid
        self.role = role
        self.prepareConfig = prepareConfig
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        view.addSubview(showView)
        view.addSubview(leftView)
        view.addSubview(rightView)
        
        view.addSubview(roomInfoLabel)
        view.addSubview(closeButton)
        view.addSubview(callButton)
        view.addSubview(hangupButton)
        view.addSubview(muteAudioButton)
        
        view.addSubview(connectStatusLabel)
        
        roomInfoLabel.text = " 房间Id: \(showRoomId) "
        roomInfoLabel.sizeToFit()
        closeButton.frame = CGRect(x: view.frame.width - 50, y: UIDevice.current.safeDistanceTop, width: 40, height: 40)
        roomInfoLabel.frame = CGRect(x: 10, y: UIDevice.current.safeDistanceTop, width: roomInfoLabel.frame.width, height: 40)
        roomInfoLabel.layer.cornerRadius = 20
        let top = view.frame.height - UIDevice.current.safeDistanceBottom - 40
        callButton.frame = CGRect(x: view.frame.width - 50, y: top, width: 40, height: 40)
        hangupButton.frame = callButton.frame
        
        muteAudioButton.frame = CGRect(x: callButton.frame.origin.x - callButton.frame.size.width - 10,
                                       y: top,
                                       width: callButton.frame.width,
                                       height: callButton.frame.height)
        
        leftView.frame = CGRect(x: 5, y: 50, width: view.frame.width / 2 - 10, height: view.frame.height / 2)
        rightView.frame = CGRect(x: view.frame.width / 2 + 5, y: 50, width: view.frame.width / 2 - 10, height: view.frame.height / 2)
        
        showView.backgroundColor = .black
        showView.frame = view.bounds
        
        self.callState = .idle
        
        //外部创建需要自行管理login
        signalClient.login {[weak self] err in
            guard let self = self else {return}
            if let err = err {
                NSLog("login error = \(err.localizedDescription)")
                return
            }
            self._initialize() { success in
                NSLog("_initialize: \(success)")
            }
        }
        
        NSLog("will joinChannel  \(self.showRoomId) \(self.currentUid)")
        let options = AgoraRtcChannelMediaOptions()
        options.clientRoleType = isBroadcaster ? .broadcaster : .audience
        options.publishMicrophoneTrack = isBroadcaster
        options.publishCameraTrack = isBroadcaster
        options.autoSubscribeAudio = !isBroadcaster
        options.autoSubscribeVideo = !isBroadcaster
        rtcEngine.joinChannel(byToken: showRoomToken,
                              channelId: showRoomId,
                              uid: currentUid,
                              mediaOptions: options) { channel, uid, elapsed in
            NSLog("joinChannel success")
        }
    }
}

extension EMShowTo1v1RoomViewController {
    private func setupCanvas(_ canvasView: UIView?) {
        if role == .caller {
            _setupRemoteVideo(roomId: showRoomId, uid: showUserId, canvasView: canvasView)
        } else {
            _setupLocalVideo(uid: currentUid, canvasView: canvasView)
        }
    }
    
    private func _setupLocalVideo(uid: UInt, canvasView: UIView?) {
        //cannot setup canvasView = nil multiple times
        if canvas.view == canvasView {
            return
        }
        canvas.view = canvasView
        canvas.uid = uid
        canvas.mirrorMode = .auto
        rtcEngine.enableAudio()
        rtcEngine.enableVideo()
        rtcEngine.setDefaultAudioRouteToSpeakerphone(true)
        rtcEngine.setupLocalVideo(canvas)
        rtcEngine.startPreview()
        
        
        //setup configuration after join channel
        rtcEngine.setVideoEncoderConfiguration(videoEncoderConfig!)

        let cameraConfig = AgoraCameraCapturerConfiguration()
        cameraConfig.cameraDirection = .front
        cameraConfig.dimensions = videoEncoderConfig!.dimensions
        cameraConfig.frameRate = Int32(videoEncoderConfig!.frameRate.rawValue)
        rtcEngine.setCameraCapturerConfiguration(cameraConfig)
    }
    
    private func _setupRemoteVideo(roomId: String, uid: UInt, canvasView: UIView?) {
        let videoCanvas = AgoraRtcVideoCanvas()
        videoCanvas.uid = uid
        videoCanvas.view = canvasView
        videoCanvas.renderMode = .hidden
        let ret = rtcEngine.setupRemoteVideo(videoCanvas)
        NSLog("setupRemoteVideo: \(ret)")
    }
    
    private func publishMedia(_ publish: Bool) {
        NSLog("publishMedia: \(publish)")
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.publishMicrophoneTrack = publish
        mediaOptions.publishCameraTrack = publish
        mediaOptions.autoSubscribeVideo = publish
        mediaOptions.autoSubscribeAudio = publish
        rtcEngine.updateChannel(with: mediaOptions)
    }
}

extension EMShowTo1v1RoomViewController {
    private func _initialize(completion: @escaping ((Bool)->())) {
        let config = CallConfig()
        config.appId = KeyCenter.AppId
        config.userId = currentUid
        config.rtcEngine = rtcEngine
        config.signalClient = signalClient
        self.api.initialize(config: config)
        
        prepareConfig.roomId = "\(currentUid)"
        prepareConfig.localView = rightView
        prepareConfig.remoteView = leftView
        
        api.addListener(listener: self)
        self.api.prepareForCall(prepareConfig: prepareConfig) { err in
            completion(err == nil)
        }
    }
    
    private func _checkConnectionAndNotify() -> Bool{
        //如果信令状态异常，不允许执行callapi操作
        guard signalClient.isConnected == true else {
            AUIToast.show(text: "环信未登录或连接异常")
            return false
        }
        
        return true
    }
    
    @objc func closeAction() {
        api.deinitialize {
            self.role = .callee
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
        
        guard role == .caller else {
            return
        }
        
        publishMedia(false)
        let remoteUserId = showUserId
        
        let alertController = UIAlertController(title: "呼叫", message: "请选择呼叫类型", preferredStyle: .actionSheet)
        // 添加操作按钮
        let action1 = UIAlertAction(title: "视频呼叫", style: .default) {[weak self] _ in
            self?.api.call(remoteUserId: remoteUserId) { error in
                guard let _ = error, self?.callState == .calling else { return }
                self?.api.cancelCall { err in }
            }
        }
        alertController.addAction(action1)

        let action2 = UIAlertAction(title: "音频呼叫", style: .default) {[weak self] _ in
            self?.api.call(remoteUserId: remoteUserId,
                           callType: .audio,
                           callExtension: ["test_call": 111]) { error in
                guard let _ = error, self?.callState == .calling else { return }
                self?.api.cancelCall { err in }
            }
        }
        alertController.addAction(action2)

        // 添加取消按钮
        let cancelAction = UIAlertAction(title: "取消", style: .cancel, handler: nil)
        alertController.addAction(cancelAction)
        present(alertController, animated: true)
    }
    
    @objc func hangupAction() {
        guard _checkConnectionAndNotify() else { return }
        
        guard let connectedUserId = connectedUserId else {
            return
        }
        api.hangup(remoteUserId: connectedUserId, reason: "hangup by user") { error in
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
}


extension EMShowTo1v1RoomViewController: AgoraRtcEngineDelegate {
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        NSLog("didJoinedOfUid: \(uid)")
    }
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        NSLog("didJoinChannel: \(channel) uid: \(uid)")
    }
}


extension EMShowTo1v1RoomViewController:CallApiListenerProtocol {
    func tokenPrivilegeWillExpire() {
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
            
            self.showRoomToken = rtcToken
            rtcEngine.renewToken(self.showRoomToken)
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
            // 触发状态的用户是自己才处理
            if currentUid == toUserId {
                connectedUserId = fromUserId
                self.api.accept(remoteUserId: fromUserId) { err in
                }
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
            AUIAlertManager.hiddenView()
            let costMap = eventInfo[kCostTimeMap] as? [String: Int] ?? [:]
            AUIToast.show(text: getCostInfo(map: costMap))
            
            //setup configuration after join channel
            rtcEngine.setVideoEncoderConfiguration(videoEncoderConfig!)

            let cameraConfig = AgoraCameraCapturerConfiguration()
            cameraConfig.cameraDirection = .front
            cameraConfig.dimensions = videoEncoderConfig!.dimensions
            cameraConfig.frameRate = Int32(videoEncoderConfig!.frameRate.rawValue)
            rtcEngine.setCameraCapturerConfiguration(cameraConfig)
        case .prepared:
            AUIAlertManager.hiddenView()
            switch stateReason {
            case .localHangup, .remoteHangup:
                AUIToast.show(text: "通话结束", postion: .bottom)
            case .localRejected, .remoteRejected:
                AUIToast.show(text: "通话被拒绝")
            case .callingTimeout:
                AUIToast.show(text: "无应答")
            case .remoteCallBusy:
                AUIToast.show(text: "用户正忙")
            default:
                break
            }
            connectedUserId = nil
            connectedRoomId = nil
        case .failed:
            AUIAlertManager.hiddenView()
            AUIToast.show(text: eventReason, postion: .bottom)
            closeAction()
            connectedUserId = nil
            connectedRoomId = nil
        default:
            break
        }
    }
    
    @objc func onCallEventChanged(with event: CallEvent, eventReason: String?) {
        NSLog("onCallEventChanged event: \(event.rawValue), eventReason: \(eventReason ?? "")")
        switch event {
        case .remoteLeft:
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
        NSLog("onCallConnected roomId: \(roomId) callUserId: \(callUserId) currentUserId: \(currentUserId) timestamp: \(timestamp)")
        
        connectStatusLabel.text = "通话开始 \nRTC 频道号: \(roomId) \n呼叫用户id: \(callUserId) \n当前用户id: \(currentUserId) \n开始时间戳: \(timestamp)"
        layoutConnectStatus()
    }
    
    @objc func onCallDisconnected(roomId: String,
                                  hangupUserId: UInt,
                                  currentUserId: UInt,
                                  timestamp: UInt64,
                                  duration: UInt64) {
        NSLog("onCallDisconnected roomId: \(roomId) hangupUserId: \(hangupUserId) currentUserId: \(currentUserId) timestamp: \(timestamp) duration: \(duration)ms")
        
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

extension EMShowTo1v1RoomViewController: ICallEasemobSignalClientListener {
    func onConnected() {
        NSLog("onConnected")
        AUIToast.show(text: "环信已连接")
    }
    
    func onDisconnected() {
        NSLog("onDisconnected")
        AUIToast.show(text: "环信未连接")
    }
}
