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
    // Live channel name
    // 直播频道名
    private var showRoomId: String
    // Host UID, if the host is the broadcaster, it is the same as currentUid
    // 主播UID，如果主播是广播者，则与currentUid相同
    private var showUserId: UInt
    // Live token
    // 直播Token
    private var showRoomToken: String
    // Current user UID
    // 当前用户UID
    private var currentUid: UInt
    // Role
    // 角色
    private var role: CallRole
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
                muteVideoButton.isSelected = false
                muteVideoButton.isHidden = false
                leftView.isHidden = false
                hangupButton.isHidden = false
            case .prepared, .idle, .failed:
                rtcEngine.enableLocalAudio(true)
                rtcEngine.enableLocalVideo(true)
                self.publishMedia(true)
                self.setupCanvas(self.showView)
                muteAudioButton.isHidden = true
                muteVideoButton.isHidden = true
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
        view.addSubview(muteVideoButton)
        
        view.addSubview(connectStatusLabel)
        
        roomInfoLabel.text = " \(NSLocalizedString("room_id", comment: "")): \(showRoomId) "
        roomInfoLabel.sizeToFit()
        closeButton.frame = CGRect(x: view.frame.width - 50, y: UIDevice.current.safeDistanceTop, width: 40, height: 40)
        roomInfoLabel.frame = CGRect(x: 10, y: UIDevice.current.safeDistanceTop, width: roomInfoLabel.frame.width, height: 40)
        roomInfoLabel.layer.cornerRadius = 20
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
        
        leftView.frame = CGRect(x: 5, y: 50, width: view.frame.width / 2 - 10, height: view.frame.height / 2)
        rightView.frame = CGRect(x: view.frame.width / 2 + 5, y: 50, width: view.frame.width / 2 - 10, height: view.frame.height / 2)
        
        showView.backgroundColor = .black
        showView.frame = view.bounds
        
        self.callState = .idle
        
        // External creation requires self-management of login
        // 外部创建需要自行管理登录
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
        //Cannot setup canvasView = nil multiple times
        //不能多次设置 canvasView = nil
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
        
        
        //Setup configuration after join channel
        //加入频道后设置配置
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
        
        let remoteUserId = showUserId
        
        let alertController = UIAlertController(title: NSLocalizedString("call", comment: ""),
                                                message: NSLocalizedString("select_call_type", comment: ""),
                                                preferredStyle: .actionSheet)
        // Add video call button
        let action1 = UIAlertAction(title: NSLocalizedString("video_call", comment: ""), style: .default) {[weak self] _ in
            self?.publishMedia(false)
            self?.api.call(remoteUserId: remoteUserId) { error in
                guard let error = error, self?.callState == .calling else { return }
                self?.api.cancelCall { err in }
                
                AUIToast.show(text: "\(NSLocalizedString("call_fail", comment: "")): \(error.localizedDescription)")
            }
        }
        alertController.addAction(action1)

        //add audio call button
        let action2 = UIAlertAction(title: NSLocalizedString("audio_call", comment: ""), style: .default) {[weak self] _ in
            self?.publishMedia(false)
            self?.api.call(remoteUserId: remoteUserId,
                           callType: .audio,
                           callExtension: ["test_call": 111]) { error in
                guard let error = error, self?.callState == .calling else { return }
                self?.api.cancelCall { err in }
                
                AUIToast.show(text: "\(NSLocalizedString("call_fail", comment: "")): \(error.localizedDescription)")
            }
        }
        alertController.addAction(action2)

        // add cancel button
        let cancelAction = UIAlertAction(title: NSLocalizedString("cancel", comment: ""), style: .cancel, handler: nil)
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
            // Only handle if the user triggering the state is oneself
            // 仅处理触发状态的用户是自己的情况
            if currentUid == toUserId {
                connectedUserId = fromUserId
                self.api.accept(remoteUserId: fromUserId) {[weak self] err in
                    guard let err = err else { return }
                    // If there is an error accepting the message, initiate a rejection and return to the initial state
                    // 如果接受消息出错，发起拒绝并返回初始状态
                    self?.api.reject(remoteUserId: fromUserId, reason: err.localizedDescription, completion: { err in
                    })
                    
                    AUIToast.show(text: "\(NSLocalizedString("accept_fail", comment: "")): \(err.localizedDescription)")
                }
            } else if currentUid == fromUserId {
                connectedUserId = toUserId
                
//                if prepareConfig.autoAccept == false {
                    AUIAlertView()
                        .isShowCloseButton(isShow: true)
                        .title(title: "\(NSLocalizedString("calling_to_user", comment: "")): \(toUserId)")
                        .rightButton(title: NSLocalizedString("cancel", comment: ""))
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
            //加入频道后设置配置
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
                AUIToast.show(text: NSLocalizedString("call_did_finish", comment: ""), postion: .bottom)
            case .localRejected, .remoteRejected:
                AUIToast.show(text: NSLocalizedString("call_did_reject", comment: ""))
            case .callingTimeout:
                AUIToast.show(text: NSLocalizedString("call_timeout", comment: ""))
            case .remoteCallBusy:
                AUIToast.show(text: NSLocalizedString("call_is_busy", comment: ""))
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
    
    @objc func onCallConnected(roomId: String,
                               callUserId: UInt,
                               currentUserId: UInt,
                               timestamp: UInt64) {
        NSLog("onCallConnected roomId: \(roomId) callUserId: \(callUserId) currentUserId: \(currentUserId) timestamp: \(timestamp)")
        
        connectStatusLabel.text = "Call started \nRTC Channel ID: \(roomId) \nCalling User ID: \(callUserId) \nCurrent User ID: \(currentUserId) \nStart Timestamp: \(timestamp)"
        layoutConnectStatus()
    }
    
    @objc func onCallDisconnected(roomId: String,
                                  hangupUserId: UInt,
                                  currentUserId: UInt,
                                  timestamp: UInt64,
                                  duration: UInt64) {
        NSLog("onCallDisconnected roomId: \(roomId) hangupUserId: \(hangupUserId) currentUserId: \(currentUserId) timestamp: \(timestamp) duration: \(duration)ms")
        
        connectStatusLabel.text = "Call ended \nRTC Channel ID: \(roomId) \nHangup User ID: \(hangupUserId) \nCurrent User ID: \(currentUserId) \nEnd Timestamp: \(timestamp) \nCall Duration: \(duration) ms"
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
        AUIToast.show(text: NSLocalizedString("easemob_did_connected", comment: ""))
    }
    
    func onDisconnected() {
        NSLog("onDisconnected")
        AUIToast.show(text: NSLocalizedString("easemob_not_connected", comment: ""))
    }
}
