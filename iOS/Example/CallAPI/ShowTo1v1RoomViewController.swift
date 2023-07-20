//
//  ShowTo1v1RoomViewController.swift
//  CallAPI_Example
//
//  Created by wushengtao on 2023/6/1.
//  Copyright © 2023 Agora. All rights reserved.
//

import UIKit
import CallAPI
import AgoraRtcKit
import CoreData

class ShowTo1v1RoomViewController: UIViewController {
    var showRoomId: String = ""          //直播频道名
    var showUserId: UInt = 0             //房主uid，如果是主播，那么和currentUid一致
    var showRoomToken: String = ""       //直播token
    var role: CallRole = .callee         //角色
    var currentUid: UInt = 0             //当前用户UID
    var tokenConfig: CallTokenConfig?
    var videoEncoderConfig: AgoraVideoEncoderConfiguration?
    
    private var isAutoCall: Bool = false
    
    private lazy var debugPath: String = {
        let path = "\(NSHomeDirectory())/Documents/ts/"
        do {
            try FileManager.default.createDirectory(atPath: path, withIntermediateDirectories: true)
        } catch {
            print(error.localizedDescription)
        }
        
        return path
    }()
    
    private let api = CallApiImpl()
    private let showView: UIView = UIView()
    private let leftView: UIView = UIView()
    private let rightView: UIView = UIView()
    private lazy var rtcEngine = _createRtcEngine()
    
    private var callState: CallStateType = .idle {
        didSet {
            switch callState {
            case .calling:
                publishMedia(false)
                setupCanvas(nil)
                self.leftView.isHidden = false
                self.rightView.isHidden = false
                hangupButton.isHidden = role == .caller ? false : true
                callButton.isHidden = true
            case .prepared, .idle, .failed:
                self.publishMedia(true)
                self.setupCanvas(self.showView)
                self.leftView.isHidden = true
                self.rightView.isHidden = true
                self.callButton.isHidden = self.role == .caller ? false : true
                self.hangupButton.isHidden = true
            default:
                break
            }
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
    
    private lazy var resetDebugButton: UIButton = {
        let btn = UIButton()
        btn.addTarget(self, action: #selector(resetAction), for: .touchUpInside)
        btn.setTitle("重置耗时统计", for: .normal)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: 14)
        btn.setTitleColor(.red, for: .normal)
        return btn
    }()
    
    private lazy var autoCallButton: UIButton = {
        let btn = UIButton()
        btn.addTarget(self, action: #selector(autoCallAction), for: .touchUpInside)
        btn.setTitle("自动呼叫: 关闭", for: .normal)
        btn.setTitle("自动呼叫: 开启", for: .selected)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: 14)
        btn.setTitleColor(.red, for: .normal)
        return btn
    }()
    
    private lazy var printAvgButton: UIButton = {
        let btn = UIButton()
        btn.addTarget(self, action: #selector(showAvgTs), for: .touchUpInside)
        btn.setTitle("显示平均耗时", for: .normal)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: 14)
        btn.setTitleColor(.red, for: .normal)
        return btn
    }()
    
    private lazy var canvas: AgoraRtcVideoCanvas = {
        let canvas = AgoraRtcVideoCanvas()
        canvas.mirrorMode = .disabled
        return canvas
    }()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        view.addSubview(showView)
        view.addSubview(leftView)
        view.addSubview(rightView)
        
        view.addSubview(roomInfoLabel)
        view.addSubview(closeButton)
        view.addSubview(callButton)
        view.addSubview(hangupButton)
        
        if role == .caller {
            view.addSubview(resetDebugButton)
            view.addSubview(printAvgButton)
            view.addSubview(autoCallButton)
        }
        
        roomInfoLabel.text = " 房间Id: \(showRoomId) "
        roomInfoLabel.sizeToFit()
        closeButton.frame = CGRect(x: view.frame.width - 50, y: UIDevice.current.safeDistanceTop, width: 40, height: 40)
        roomInfoLabel.frame = CGRect(x: 10, y: UIDevice.current.safeDistanceTop, width: roomInfoLabel.frame.width, height: 40)
        roomInfoLabel.layer.cornerRadius = 20
        let top = view.frame.height - UIDevice.current.safeDistanceBottom - 40
        callButton.frame = CGRect(x: view.frame.width - 50, y: top, width: 40, height: 40)
        hangupButton.frame = callButton.frame
        
        resetDebugButton.frame = CGRect(x: 10, y: top, width: 100, height: 40)
        printAvgButton.frame = CGRect(x: 10, y: top - 60, width: 100, height: 40)
        autoCallButton.frame = CGRect(x: 10, y: top - 120, width: 100, height: 40)
        
        leftView.frame = CGRect(x: 5, y: 50, width: view.frame.width / 2 - 10, height: view.frame.height - 500)
        rightView.frame = CGRect(x: view.frame.width / 2 + 5, y: 50, width: view.frame.width / 2 - 10, height: view.frame.height - 500)
        leftView.backgroundColor = .black
        rightView.backgroundColor = .black
        
        leftView.isHidden = true
        rightView.isHidden = true
        
        showView.backgroundColor = .black
        showView.frame = view.bounds
        
        self.callState = .idle
        _initialize(role: role) { success in
            print("_initialize: \(success)")
        }
        
        print("will joinChannel  \(self.showRoomId) \(self.currentUid)")
        let options = AgoraRtcChannelMediaOptions()
        options.clientRoleType = role == .caller ? .audience : .broadcaster
        options.publishMicrophoneTrack = role == .caller ? false : true
        options.publishCameraTrack = role == .caller ? false : true
        options.autoSubscribeAudio = role == .caller ? true : false
        options.autoSubscribeVideo = role == .caller ? true : false
        rtcEngine.joinChannel(byToken: showRoomToken,
                              channelId: showRoomId,
                              uid: currentUid,
                              mediaOptions: options) { channel, uid, elapsed in
            print("joinChannel success")
        }
    }
}

extension ShowTo1v1RoomViewController {
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
        print("setupRemoteVideo: \(ret)")
    }
    
    private func publishMedia(_ publish: Bool) {
        print("publishMedia: \(publish)")
        let mediaOptions = AgoraRtcChannelMediaOptions()
        mediaOptions.publishMicrophoneTrack = publish
        mediaOptions.publishCameraTrack = publish
        mediaOptions.autoSubscribeVideo = publish
        mediaOptions.autoSubscribeAudio = publish
        rtcEngine.updateChannel(with: mediaOptions)
    }
}

extension ShowTo1v1RoomViewController {
    
    private func _initialize(role: CallRole, completion: @escaping ((Bool)->())) {
        let config = CallConfig()
        config.role = role
        config.ownerRoomId = showRoomId
        config.appId = KeyCenter.AppId
        config.userId = currentUid
        config.rtcEngine = _createRtcEngine()
        if role == .caller {
            config.localView = rightView
            config.remoteView = leftView
        } else {
            config.localView = leftView
            config.remoteView = rightView
        }
        
        // 如果是被叫会隐式调用prepare
        self.api.initialize(config: config, token: tokenConfig!) { error in
            self.role = role
            
            guard role == .caller else {
                completion(true)
                return
            }
            // 如果是主叫并且想加快呼叫，可以在init完成之后调用prepare
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
            self.role = .callee
            self.api.removeListener(listener: self)
            self.rtcEngine.stopPreview()
            self.rtcEngine.delegate = nil
            self.rtcEngine.leaveChannel()
            AgoraRtcEngineKit.destroy()
            self.dismiss(animated: true)
        }
    }

    @objc func callAction() {
        guard role == .caller else {
            return
        }
        
        publishMedia(false)
        api.call(roomId: showRoomId, remoteUserId: showUserId) { error in
        }
        
        leftView.isHidden = false
        rightView.isHidden = false
    }
    
    @objc func hangupAction() {
        guard role == .caller else {
            return
        }
        
        api.hangup(roomId: showRoomId) { error in
        }
        
        leftView.isHidden = true
        rightView.isHidden = true
    }
    
    @objc func resetAction() {
        do {
            try FileManager.default.removeItem(atPath: debugPath)
            try FileManager.default.createDirectory(atPath: debugPath, withIntermediateDirectories: true)
        } catch {
            print(error.localizedDescription)
        }
    }
    
    @objc func showAvgTs() {
        let folderPath = debugPath
        DispatchQueue.global().async {
            guard let names = try? FileManager.default.contentsOfDirectory(atPath: folderPath) else {
                return
            }
            let fileNames = names.filter { name in
                name.contains(".debug")
            }
            let count = fileNames.count
            var tsMap = [String: Float]()
            try? fileNames.forEach { name in
                let path = "\(folderPath)/\(name)"
                let data = try! Data.init(contentsOf: URL(fileURLWithPath: path))
                let map = try NSKeyedUnarchiver.unarchivedObject(ofClass: NSDictionary.self, from: data) as! [String: Float]
                map.forEach { k, v in
                    let oldValue = tsMap[k] ?? 0
                    tsMap[k] = oldValue + v / Float(count)
                }
            }
            
            DispatchQueue.main.async {
                var toastStr = "[\(count)]次呼叫平均耗时\n"
                var tsArray = [String]()
                tsMap.forEach { k, v in
                    tsArray.append("\(k): \(v) ms\n")
                }
                tsArray = tsArray.sorted(by: { str1, str2 in
                    return Int(str1.prefix(1)) ?? 0 < Int(str2.prefix(1)) ?? 0
                })
                tsArray.forEach { str in
                    toastStr += str
                }
                AUIToast.show(text: toastStr)
            }
        }
    }
    
    @objc func autoCallAction() {
        autoCallButton.isSelected = !autoCallButton.isSelected
        isAutoCall = autoCallButton.isSelected
        
        if isAutoCall, callState == .prepared {
            callAction()
        }
    }
}


extension ShowTo1v1RoomViewController: AgoraRtcEngineDelegate {
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        print("didJoinedOfUid: \(uid)")
    }
    public func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        print("didJoinChannel: \(channel) uid: \(uid)")
    }
}


extension ShowTo1v1RoomViewController:CallApiListenerProtocol {
    public func onCallStateChanged(with state: CallStateType,
                                   stateReason: CallReason,
                                   eventReason: String,
                                   elapsed: Int,
                                   eventInfo: [String : Any]) {
        let publisher = UInt(eventInfo[kPublisher] as? String ?? "") ?? currentUid
        
        // 触发状态的用户是自己才处理
        guard publisher == currentUid else {
            switch state {
            case .calling, .connecting, .connected:
                setupCanvas(nil)
            default:
                setupCanvas(showView)
            }
            return
        }
        print("onCallStateChanged state: \(state.rawValue), stateReason: \(stateReason.rawValue), eventReason: \(eventReason), elapsed: \(elapsed) ms, eventInfo: \(eventInfo) publisher: \(publisher) / \(currentUid)")
        
        self.callState = state
        
        switch state {
        case .connected:
            if let debugMap = eventInfo[kDebugInfoMap] as? [String: Int], debugMap.count == 6 {
                if let data = try? NSKeyedArchiver.archivedData(withRootObject: debugMap, requiringSecureCoding: false) {
                    let path = "\(debugPath)/\(Int(Date().timeIntervalSince1970)).debug"
                    do {
                        try data.write(to: URL(fileURLWithPath: path))
                    } catch {
                        print(error.localizedDescription)
                    }
                }
            }
            AUIToast.show(text: "通话开始\(eventInfo[kDebugInfo] as? String ?? "")", postion: .bottom)
            
            if isAutoCall {
                DispatchQueue.main.asyncAfter(deadline: DispatchTime.now() + 1) {
                    self.hangupAction()
                }
            }
            
            //setup configuration after join channel
            rtcEngine.setVideoEncoderConfiguration(videoEncoderConfig!)

            let cameraConfig = AgoraCameraCapturerConfiguration()
            cameraConfig.cameraDirection = .front
            cameraConfig.dimensions = videoEncoderConfig!.dimensions
            cameraConfig.frameRate = Int32(videoEncoderConfig!.frameRate.rawValue)
            rtcEngine.setCameraCapturerConfiguration(cameraConfig)
        case .prepared:
            switch stateReason {
            case .localHangup, .remoteHangup:
                AUIToast.show(text: "通话结束", postion: .bottom)
                if isAutoCall {
                    DispatchQueue.main.asyncAfter(deadline: DispatchTime.now() + 1) {
                        self.callAction()
                    }
                }
            case .localRejected, .remoteRejected:
                AUIToast.show(text: "通话被拒绝")
            case .callingTimeout:
                AUIToast.show(text: "无应答")
            default:
                break
            }
        case .failed:
            AUIToast.show(text: eventReason, postion: .bottom)
        default:
            break
        }
    }
    
    func onOneForOneCache(oneForOneRoomId: String, fromUserId: UInt, toUserId: UInt) {
        
        //有缓存
        if fromUserId == currentUid, role == .caller {
            
        } else if toUserId == currentUid, role == .callee {
            
        } else {
            return
        }
        
        let token = tokenConfig?.rtcToken ?? ""
        AUIAlertView()
            .isShowCloseButton(isShow: true)
            .title(title: "需要恢复上次的一对一")
            .rightButton(title: "是")
            .leftButton(title: "否")
            .leftButtonTapClosure {[weak self] in
                guard let self = self else { return }
                if self.role == .caller {
                    self.api.reject(roomId: self.showRoomId, remoteUserId: toUserId, reason: "") { err in
                    }
                } else if self.role == .callee {
                    self.api.reject(roomId: oneForOneRoomId, remoteUserId: fromUserId, reason: "") { err in
                    }
                }
            }
            .rightButtonTapClosure(onTap: {[weak self] text in
                guard let self = self else { return }
                if self.role == .caller {
                    self.api.call(roomId: self.showRoomId, remoteUserId: toUserId) { err in
                    }
                } else if self.role == .callee {
                    self.api.accept(roomId: oneForOneRoomId, remoteUserId: fromUserId, rtcToken: token) { err in
                        
                    }
                }
            })
            .show()
    }
}
