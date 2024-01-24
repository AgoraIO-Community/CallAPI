//
//  ViewController.swift
//  CallAPI
//
//  Created by wushengtao on 05/29/2023.
//  Copyright (c) 2023 Agora. All rights reserved.
//

import UIKit
import CallAPI
import AgoraRtcKit

private let fpsItems: [AgoraVideoFrameRate] = [
    .fps1,
    .fps7,
    .fps10,
    .fps15,
    .fps24,
    .fps30,
    .fps60
]

class ViewController: UIViewController {
    
    private let videoEncoderConfig = AgoraVideoEncoderConfiguration()
    
    private lazy var modeControl: UISegmentedControl = {
        let items = ["秀场转1v1", "纯1v1"]
        let control = UISegmentedControl(items: items)
        control.selectedSegmentIndex = 0
        control.addTarget(self, action: #selector(modeChanged), for: .valueChanged)
        return control
    }()
    
    private lazy var roleControl: UISegmentedControl = {
        let items = ["主播", "观众"]
        let control = UISegmentedControl(items: items)
        control.selectedSegmentIndex = 0
        control.addTarget(self, action: #selector(roleChanged), for: .valueChanged)
        return control
    }()
    
    private lazy var enterButton1: UIButton = {
        let button = UIButton()
        button.tag = 1
        button.setTitle("创建秀场转1v1", for: .normal)
        button.setTitle("加入秀场转1v1", for: .selected)
        button.backgroundColor = .blue
        button.addTarget(self, action: #selector(enterShowTo1v1(_ :)), for: .touchUpInside)
        return button
    }()
    
    private lazy var enterButton2: UIButton = {
        let button = UIButton()
        button.setTitle("进入纯1v1", for: .normal)
        button.backgroundColor = .blue
        button.addTarget(self, action: #selector(enterPure1v1(_ :)), for: .touchUpInside)
        return button
    }()
    
    private var role: CallRole = .callee {
        didSet {
            if role == .callee {
                enterButton1.setTitle("创建秀场转1v1", for: .normal)
            } else {
                enterButton1.setTitle("加入秀场转1v1", for: .normal)
            }
            
            updateUI()
        }
    }
    
    private var modeIndex: Int {
        get {
            let idx = Int(UserDefaults.standard.integer(forKey: "modeIndex"))
            return idx
        } set {
            UserDefaults.standard.set(newValue, forKey: "modeIndex")
            
            if newValue == 0 {
                roleControl.isHidden = false
                enterButton1.isHidden = false
                enterButton2.isHidden = true
            } else {
                roleControl.isHidden = true
                enterButton1.isHidden = true
                enterButton2.isHidden = false
            }
            modeControl.selectedSegmentIndex = newValue
            updateUI()
        }
    }
    
    private var roleIndex: Int {
        get {
            let uid = Int(UserDefaults.standard.integer(forKey: "roleIndex"))
            return uid
        } set {
            UserDefaults.standard.set(newValue, forKey: "roleIndex")
            roleControl.selectedSegmentIndex = newValue
            self.role = newValue == 1 ? .caller : .callee
        }
    }
    
    private var currentUserId: UInt {
        get {
            var uid = UInt(UserDefaults.standard.integer(forKey: "currentUserId"))
            if uid > 0 {
                return uid
            }
            uid = UInt(arc4random_uniform(99999))
            UserDefaults.standard.set(uid, forKey: "currentUserId")
            return uid
        } set {
            UserDefaults.standard.set(newValue, forKey: "currentUserId")
        }
    }
    
    private var callUserId: UInt {
        get {
            var uid = UInt(UserDefaults.standard.integer(forKey: "callUserId"))
            if uid > 0 {
                return uid
            }
            uid = UInt(arc4random_uniform(99999))
            UserDefaults.standard.set(uid, forKey: "callUserId")
            return uid
        } set {
            UserDefaults.standard.set(newValue, forKey: "callUserId")
        }
    }
    
    private var fpsIndex: Int {
        get {
            let fpsIdx = Int(UserDefaults.standard.string(forKey: "fpsIndex") ?? "") ?? 3
            return fpsIdx
        } set {
            UserDefaults.standard.set("\(newValue)", forKey: "fpsIndex")
            videoEncoderConfig.frameRate = fpsItems[newValue]
            fpsButton.setTitle("FPS: \(videoEncoderConfig.frameRate.rawValue)", for: .normal)
        }
    }
    
    private var dimW: Int {
        get {
            let val = Int(UserDefaults.standard.integer(forKey: "dimW"))
            return val == 0 ? 1280 : val
        } set {
            UserDefaults.standard.set(newValue, forKey: "dimW")
            dimensionsWTf.text = "\(dimW)"
            videoEncoderConfig.dimensions = CGSize(width: dimW, height: dimH)
        }
    }
    
    private var dimH: Int {
        get {
            let val = Int(UserDefaults.standard.integer(forKey: "dimH"))
            return val == 0 ? 720: val
        } set {
            UserDefaults.standard.set(newValue, forKey: "dimH")
            dimensionsHTf.text = "\(dimH)"
            videoEncoderConfig.dimensions = CGSize(width: dimW, height: dimH)
        }
    }
    
    private var isAutoJoinRTC: Bool {
        get {
            let val = UserDefaults.standard.bool(forKey: "autoJoinRTC")
            return val
        } set {
            UserDefaults.standard.set(newValue, forKey: "autoJoinRTC")
        }
    }
    
    private var isAutoAccept: Bool {
        get {
            let val = UserDefaults.standard.bool(forKey: "autoAccept")
            return val
        } set {
            UserDefaults.standard.set(newValue, forKey: "autoAccept")
        }
    }
    
    private lazy var userLabel: UILabel = {
        let label = UILabel()
        label.text = "当前的用户id"
        label.textColor = .black
        return label
    }()
    
    private lazy var userTextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = "当前的用户id"
        tf.textColor = .black
        tf.keyboardType = .numberPad
        tf.addTarget(self, action: #selector(currentUserChanged), for: .editingChanged)
        return tf
    }()
    
    private lazy var callUserLabel: UILabel = {
        let label = UILabel()
        label.text = "主播id"
        label.textColor = .black
        return label
    }()
    
    private lazy var callUserTextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = "主播id"
        tf.textColor = .black
        tf.keyboardType = .numberPad
        tf.addTarget(self, action: #selector(callUserTfChanged), for: .editingChanged)
        return tf
    }()
    
    private lazy var fpsButton: UIButton = {
        let button = UIButton()
        button.backgroundColor = .blue
        button.setTitle("FPS", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.textAlignment = .left
        button.layer.cornerRadius = 5
        button.addTarget(self, action: #selector(fpsChanged), for: .touchUpInside)
        return button
    }()
    
    private lazy var dimensionsLabel: UILabel = {
        let label = UILabel()
        label.text = "分辨率"
        label.textColor = .black
        return label
    }()
    
    private lazy var dimensionsWTf: UITextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = "宽"
        tf.textColor = .black
        tf.keyboardType = .numberPad
        tf.addTarget(self, action: #selector(dimensionsWTfChanged), for: .editingChanged)
        return tf
    }()
    
    private lazy var dimensionsHTf: UITextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = "高"
        tf.textColor = .black
        tf.keyboardType = .numberPad
        tf.addTarget(self, action: #selector(dimensionsHTfChanged), for: .editingChanged)
        return tf
    }()
    
    private lazy var autoAcceptLabel: UILabel = {
        let label = UILabel()
        label.text = "收到呼叫自动接受"
        label.textColor = .black
        label.isHidden = true
        return label
    }()
    
    private lazy var autoAcceptSwitch: UISwitch = {
        let uiSwitch = UISwitch()
        uiSwitch.isOn = isAutoAccept
        uiSwitch.addTarget(self, action: #selector(onAutoAcceptAction), for: .touchUpInside)
        uiSwitch.isHidden = true
        return uiSwitch
    }()
    
    private lazy var autoJointRTCLabel: UILabel = {
        let label = UILabel()
        label.text = "提前加入RTC频道"
        label.textColor = .black
        return label
    }()
    
    private lazy var autoJoinRTCSwitch: UISwitch = {
        let uiSwitch = UISwitch()
        uiSwitch.addTarget(self, action: #selector(onAutoJoinAction), for: .touchUpInside)
        uiSwitch.isOn = isAutoJoinRTC
        return uiSwitch
    }()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // Do any additional setup after loading the view, typically from a nib.
        
        let tapGes = UITapGestureRecognizer(target: self, action: #selector(tapView))
        view.addGestureRecognizer(tapGes)
        
        view.addSubview(modeControl)
        view.addSubview(roleControl)
        userTextField.text = "\(currentUserId)"
        callUserTextField.text = "\(callUserId)"
        
        view.addSubview(userLabel)
        view.addSubview(userTextField)
        view.addSubview(callUserLabel)
        view.addSubview(callUserTextField)
        
        view.addSubview(fpsButton)
        view.addSubview(dimensionsLabel)
        view.addSubview(dimensionsWTf)
        view.addSubview(dimensionsHTf)
        
        view.addSubview(autoAcceptLabel)
        view.addSubview(autoAcceptSwitch)
        
        view.addSubview(autoJointRTCLabel)
        view.addSubview(autoJoinRTCSwitch)
        
        view.addSubview(enterButton1)
        view.addSubview(enterButton2)
        
        enterButton1.frame = CGRect(x: 10, y: view.frame.height - 100, width: view.frame.width - 20, height: 40)
        enterButton2.frame = enterButton1.frame
        
        //reset role
        roleIndex = roleIndex
        modeIndex = modeIndex
        dimW = dimW
        dimH = dimH
        fpsIndex = fpsIndex
    }
    
    private func updateUI() {
        if role == .caller, modeIndex == 0 {
            callUserLabel.isHidden = false
            callUserTextField.isHidden = false
        } else {
            callUserLabel.isHidden = true
            callUserTextField.isHidden = true
        }
        
        modeControl.frame = CGRect(x: 10, y: 80, width: 200, height: 40)
        var topEdge: CGFloat = modeControl.frame.origin.y + modeControl.frame.height + 10
        if roleControl.isHidden == false {
            roleControl.frame = CGRect(x: 10, y: topEdge, width: 200, height: 40)
            topEdge += 50
        }
        userLabel.frame = CGRect(x: 10, y: topEdge, width: 150, height: 40)
        userTextField.frame = CGRect(x: 150, y: topEdge, width: 200, height: 40)
        topEdge += 50
        
        if !callUserLabel.isHidden {
            callUserLabel.frame = CGRect(x: 10, y: topEdge, width: 150, height: 40)
            callUserTextField.frame = CGRect(x: 150, y: topEdge, width: 200, height: 40)
            topEdge += 50
        }
        
        fpsButton.frame = CGRect(x: 10, y: topEdge, width: 100, height: 40)
        topEdge += 50
        dimensionsLabel.sizeToFit()
        dimensionsLabel.frame = CGRect(x: 10, y: topEdge, width: dimensionsLabel.frame.width, height: 40)
        dimensionsWTf.frame = CGRect(x: dimensionsLabel.frame.size.width + dimensionsLabel.frame.origin.x + 10, y: topEdge, width: 80, height: 40)
        dimensionsHTf.frame = CGRect(x: dimensionsWTf.frame.size.width + dimensionsWTf.frame.origin.x + 10, y: topEdge, width: 80, height: 40)
        
        autoAcceptLabel.sizeToFit()
        autoAcceptLabel.frame = CGRect(x: 10, 
                                       y: dimensionsLabel.frame.origin.y + dimensionsLabel.frame.height + 10,
                                       width: autoAcceptLabel.frame.width,
                                       height: 40)
        autoAcceptSwitch.frame = CGRect(x: autoAcceptLabel.frame.origin.x + autoAcceptLabel.frame.width + 10,
                                        y: autoAcceptLabel.frame.origin.y,
                                        width: 60,
                                        height: 40)
        
        
        autoJointRTCLabel.sizeToFit()
        autoJointRTCLabel.frame = CGRect(x: 10,
                                         y: autoAcceptLabel.frame.origin.y + autoAcceptLabel.frame.height + 10,
                                         width: autoJointRTCLabel.frame.width,
                                         height: 40)
        autoJoinRTCSwitch.frame = CGRect(x: autoJointRTCLabel.frame.origin.x + autoJointRTCLabel.frame.width + 10,
                                         y: autoJointRTCLabel.frame.origin.y,
                                         width: 60,
                                         height: 40)
    }
    
    @objc func currentUserChanged() {
        currentUserId = UInt(userTextField.text ?? "") ?? 0
        print("currentUserId: \(currentUserId)")
    }
    
    @objc func callUserTfChanged() {
        callUserId = UInt(callUserTextField.text ?? "") ?? 0
        print("callUserId: \(callUserId)")
    }
    
    @objc func fpsChanged() {
        let vc = UIAlertController(title: "选择fps", message: nil, preferredStyle: .alert)
        for (idx, item) in fpsItems.enumerated() {
            let action = UIAlertAction(title: "\(item.rawValue)", style: .default) {[weak self] action in
                self?.fpsIndex = idx
            }
            vc.addAction(action)
        }
        self.present(vc, animated: true)
    }
    
    @objc func dimensionsHTfChanged() {
        dimH = Int(dimensionsHTf.text ?? "") ?? 0
    }
    
    @objc func dimensionsWTfChanged() {
        dimW = Int(dimensionsWTf.text ?? "") ?? 0
    }
    
    @objc func onAutoAcceptAction() {
        self.isAutoAccept = autoAcceptSwitch.isOn
    }
    
    @objc func onAutoJoinAction() {
        self.isAutoJoinRTC = autoJoinRTCSwitch.isOn
    }
    
    @objc func tapView() {
        view.endEditing(true)
    }
    
    @objc func modeChanged() {
        self.modeIndex = modeControl.selectedSegmentIndex
    }
    
    @objc func roleChanged() {
        self.roleIndex = roleControl.selectedSegmentIndex
    }
    
    @objc func enterShowTo1v1(_ button: UIButton) {
        if role == .caller, currentUserId == callUserId {
            AUIToast.show(text: "直播频道名和1v1频道名不能相同")
            return
        }
        
        view.isUserInteractionEnabled = false
        
        let prepareConfig = PrepareConfig()
//        prepareConfig.autoAccept = autoAcceptSwitch.isOn
        prepareConfig.autoJoinRTC = autoJoinRTCSwitch.isOn
        NetworkManager.shared.generateTokens(channelName: "",
                                             uid: "\(currentUserId)",
                                             tokenGeneratorType: .token007,
                                             tokenTypes: [.rtc, .rtm]) {[weak self] tokens in
            guard let self = self else {return}
            self.view.isUserInteractionEnabled = true
            guard tokens.count == 2 else {
                print("generateTokens fail")
                return
            }
            prepareConfig.rtcToken = tokens[AgoraTokenType.rtc.rawValue]!
            prepareConfig.rtmToken = tokens[AgoraTokenType.rtm.rawValue]!
            
            let targetUserId = role == .caller ? "\(callUserId)" : "\(currentUserId)"
            
            let vc = ShowTo1v1RoomViewController(showRoomId: "\(targetUserId)_live",
                                                 showUserId: role == .callee ? currentUserId : callUserId,
                                                 showRoomToken: prepareConfig.rtcToken,
                                                 currentUid: currentUserId,
                                                 role: role,
                                                 prepareConfig: prepareConfig)
            vc.modalPresentationStyle = .fullScreen
            vc.videoEncoderConfig = videoEncoderConfig
            self.present(vc, animated: true)
        }
    }
    
    @objc func enterPure1v1(_ button: UIButton) {
        view.isUserInteractionEnabled = false
        
        let prepareConfig = PrepareConfig()
//        prepareConfig.autoAccept = autoAcceptSwitch.isOn
        prepareConfig.autoJoinRTC = autoJoinRTCSwitch.isOn
        NetworkManager.shared.generateTokens(channelName: "",
                                             uid: "\(currentUserId)",
                                             tokenGeneratorType: .token007,
                                             tokenTypes: [.rtc, .rtm]) {[weak self] tokens in
            guard let self = self else {return}
            guard tokens.count == 2 else {
                print("generateTokens fail")
                self.view.isUserInteractionEnabled = true
                return
            }
            prepareConfig.rtcToken = tokens[AgoraTokenType.rtc.rawValue]!
            prepareConfig.rtmToken = tokens[AgoraTokenType.rtm.rawValue]!
            
            let vc = Pure1v1RoomViewController(currentUid: currentUserId, prepareConfig: prepareConfig)
            vc.modalPresentationStyle = .fullScreen
            vc.videoEncoderConfig = videoEncoderConfig
            self.view.isUserInteractionEnabled = true
            self.present(vc, animated: true)
        }
    }
}

//MARK: debug
extension ViewController {
    private func _cacheDir() ->String {
        let dir = NSSearchPathForDirectoriesInDomains(FileManager.SearchPathDirectory.cachesDirectory,
                                                      FileManager.SearchPathDomainMask.userDomainMask, true).first
        return dir ?? ""
    }
    override func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {
        super.motionEnded(motion, with: event)
        
        let url = URL(fileURLWithPath: _cacheDir())
        let vc = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        
        self.present(vc, animated: true)
    }
}
