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
import SVProgressHUD

enum CallRole: Int {
    case callee = 0
    case caller
}

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
        let items = [
            NSLocalizedString("show_to_1v1_rtm", comment: ""),
            NSLocalizedString("1v1_rtm", comment: ""),
            NSLocalizedString("show_to_1v1_easemob", comment: ""),
            NSLocalizedString("1v1_easemob", comment: "")
        ]
        let control = UISegmentedControl(items: items)
        control.setTitleTextAttributes([.foregroundColor: UIColor.black, .font: UIFont.systemFont(ofSize: 12)], for: .normal)
        control.setTitleTextAttributes([.foregroundColor: UIColor.blue, .font: UIFont.systemFont(ofSize: 12)], for: .selected)
        control.selectedSegmentIndex = 0
        control.addTarget(self, action: #selector(modeChanged), for: .valueChanged)
        return control
    }()
    
    private lazy var roleControl: UISegmentedControl = {
        let items = [NSLocalizedString("broadcaster", comment: ""), NSLocalizedString("audience", comment: "")]
        let control = UISegmentedControl(items: items)
        control.setTitleTextAttributes([.foregroundColor: UIColor.black], for: .normal)
        control.setTitleTextAttributes([.foregroundColor: UIColor.blue], for: .selected)
        control.selectedSegmentIndex = 0
        control.addTarget(self, action: #selector(roleChanged), for: .valueChanged)
        return control
    }()
    
    private lazy var enterButton1: UIButton = {
        let button = UIButton()
        button.tag = 1
        button.setTitle(NSLocalizedString("create_show_to_1v1", comment: ""), for: .normal)
        button.setTitle(NSLocalizedString("join_show_to_1v1", comment: ""), for: .selected)
        button.backgroundColor = .blue
        button.addTarget(self, action: #selector(enterShowTo1v1(_ :)), for: .touchUpInside)
        return button
    }()
    
    private lazy var enterButton2: UIButton = {
        let button = UIButton()
        button.setTitle(NSLocalizedString("enter_1v1", comment: ""), for: .normal)
        button.backgroundColor = .blue
        button.addTarget(self, action: #selector(enterPure1v1(_ :)), for: .touchUpInside)
        return button
    }()
    
    private var role: CallRole = .callee {
        didSet {
            if role == .callee {
                enterButton1.setTitle(NSLocalizedString("create_show_to_1v1", comment: ""), for: .normal)
            } else {
                enterButton1.setTitle(NSLocalizedString("join_show_to_1v1", comment: ""), for: .normal)
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
            
            if newValue % 2 == 0 {
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
    
    private var firstFrameConnectedDisable: Bool {
        get {
            let disable = UserDefaults.standard.bool(forKey: "firstFrameConnectedDisable")
            return disable
        } set {
            UserDefaults.standard.set(newValue, forKey: "firstFrameConnectedDisable")
            firstFrameDisableButton.isSelected = newValue
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
    
    private lazy var userLabel: UILabel = {
        let label = UILabel()
        label.text = NSLocalizedString("current_user_id", comment: "")
        label.textColor = .black
        return label
    }()
    
    private lazy var userTextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = NSLocalizedString("current_user_id", comment: "")
        tf.textColor = .black
        tf.keyboardType = .numberPad
        tf.addTarget(self, action: #selector(currentUserChanged), for: .editingChanged)
        return tf
    }()
    
    private lazy var callUserLabel: UILabel = {
        let label = UILabel()
        label.text = NSLocalizedString("broadcaster_id", comment: "")
        label.textColor = .black
        return label
    }()
    
    private lazy var callUserTextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = NSLocalizedString("broadcaster_id", comment: "")
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
    
    private lazy var firstFrameDisableButton: UIButton = {
        let button = UIButton()
        button.backgroundColor = .blue
        button.titleLabel?.adjustsFontSizeToFitWidth = true
        button.setTitle(NSLocalizedString("first_frame_related_connected", comment: ""), for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.setTitle(NSLocalizedString("first_frame_not_related_connected", comment: ""), for: .selected)
        button.setTitleColor(.white, for: .selected)
        button.titleLabel?.textAlignment = .left
        button.layer.cornerRadius = 5
        button.addTarget(self, action: #selector(firstFrameDisable), for: .touchUpInside)
        return button
    }()
    
    private lazy var dimensionsLabel: UILabel = {
        let label = UILabel()
        label.text = NSLocalizedString("select_resolution", comment: "")
        label.textColor = .black
        return label
    }()
    
    private lazy var dimensionsWTf: UITextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = NSLocalizedString("select_width", comment: "")
        tf.textColor = .black
        tf.keyboardType = .numberPad
        tf.addTarget(self, action: #selector(dimensionsWTfChanged), for: .editingChanged)
        return tf
    }()
    
    private lazy var dimensionsHTf: UITextField = {
        let tf = UITextField()
        tf.backgroundColor = .white
        tf.borderStyle = .roundedRect
        tf.placeholder = NSLocalizedString("select_height", comment: "")
        tf.textColor = .black
        tf.keyboardType = .numberPad
        tf.addTarget(self, action: #selector(dimensionsHTfChanged), for: .editingChanged)
        return tf
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
        view.addSubview(firstFrameDisableButton)
        view.addSubview(dimensionsLabel)
        view.addSubview(dimensionsWTf)
        view.addSubview(dimensionsHTf)
        
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
        firstFrameConnectedDisable = firstFrameConnectedDisable
    }
    
    private func updateUI() {
        if role == .caller, modeIndex % 2 == 0 {
            callUserLabel.isHidden = false
            callUserTextField.isHidden = false
        } else {
            callUserLabel.isHidden = true
            callUserTextField.isHidden = true
        }
        
        modeControl.frame = CGRect(x: 10, y: 80, width: view.frame.width - 20, height: 40)
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
        
        fpsButton.frame = CGRect(x: 10, y: topEdge, width: 80, height: 40)
        firstFrameDisableButton.sizeToFit()
        firstFrameDisableButton.frame = CGRect(x: 100, y: topEdge, width: 220, height: 40)
        topEdge += 50
        dimensionsLabel.sizeToFit()
        dimensionsLabel.frame = CGRect(x: 10, y: topEdge, width: dimensionsLabel.frame.width, height: 40)
        dimensionsWTf.frame = CGRect(x: dimensionsLabel.frame.size.width + dimensionsLabel.frame.origin.x + 10, y: topEdge, width: 80, height: 40)
        dimensionsHTf.frame = CGRect(x: dimensionsWTf.frame.size.width + dimensionsWTf.frame.origin.x + 10, y: topEdge, width: 80, height: 40)
    }
    
    @objc func currentUserChanged() {
        currentUserId = UInt(userTextField.text ?? "") ?? 0
        print("currentUserId: \(currentUserId)")
    }
    
    @objc func firstFrameDisable() {
        firstFrameDisableButton.isSelected = !firstFrameDisableButton.isSelected
        firstFrameConnectedDisable = firstFrameDisableButton.isSelected
    }
    
    @objc func callUserTfChanged() {
        callUserId = UInt(callUserTextField.text ?? "") ?? 0
        print("callUserId: \(callUserId)")
    }
    
    @objc func fpsChanged() {
        let vc = UIAlertController(title: NSLocalizedString("select_fps", comment: ""), message: nil, preferredStyle: .alert)
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
            AUIToast.show(text: "The live stream channel name and the 1v1 channel name cannot be the same.")
            return
        }
        
        view.isUserInteractionEnabled = false
        
        let prepareConfig = PrepareConfig()
        prepareConfig.firstFrameWaittingDisabled = firstFrameConnectedDisable
        SVProgressHUD.show()
        NetworkManager.shared.generateToken(channelName: "",
                                            uid: "\(currentUserId)",
                                            types: [.rtc, .rtm]) {[weak self] token in
            SVProgressHUD.dismiss()
            guard let self = self else {return}
            self.view.isUserInteractionEnabled = true
            guard let token = token else {
                print("generateTokens fail")
                return
            }
            prepareConfig.rtcToken = token
            
            let targetUserId = role == .caller ? "\(callUserId)" : "\(currentUserId)"
            
            var showVc: UIViewController? = nil
            if modeIndex == 0 {
            #if canImport(AgoraRtmKit)
                let rtmToken = token
                let vc = ShowTo1v1RoomViewController(showRoomId: "\(targetUserId)_live",
                                                     showUserId: role == .callee ? currentUserId : callUserId,
                                                     showRoomToken: prepareConfig.rtcToken,
                                                     currentUid: currentUserId,
                                                     role: role,
                                                     rtmToken: rtmToken,
                                                     prepareConfig: prepareConfig)
                vc.videoEncoderConfig = videoEncoderConfig
                showVc = vc
            #else
                AUIToast.show(text: NSLocalizedString("rtm_not_found", comment: ""))
            #endif
            } else {
                let vc = EMShowTo1v1RoomViewController(showRoomId: "\(targetUserId)_live",
                                                       showUserId: role == .callee ? currentUserId : callUserId,
                                                       showRoomToken: prepareConfig.rtcToken,
                                                       currentUid: currentUserId,
                                                       role: role,
                                                       prepareConfig: prepareConfig)
                vc.videoEncoderConfig = videoEncoderConfig
                showVc = vc
            }
            guard let vc = showVc else { return }
            vc.modalPresentationStyle = .fullScreen
            self.present(vc, animated: true)
        }
    }
    
    @objc func enterPure1v1(_ button: UIButton) {
        view.isUserInteractionEnabled = false
        
        let prepareConfig = PrepareConfig()
        prepareConfig.firstFrameWaittingDisabled = firstFrameConnectedDisable
        SVProgressHUD.show()
        NetworkManager.shared.generateToken(channelName: "",
                                            uid: "\(currentUserId)",
                                            types: [.rtc, .rtm]) {[weak self] token in
            SVProgressHUD.dismiss()
            guard let self = self else {return}
            self.view.isUserInteractionEnabled = true
            guard let token = token else {
                print("generateTokens fail")
                return
            }
            prepareConfig.rtcToken = token
            
            var showVc: UIViewController? = nil
            if modeIndex == 1 {
            #if canImport(AgoraRtmKit)
                let rtmToken = token
                let vc = Pure1v1RoomViewController(currentUid: currentUserId,
                                                   prepareConfig: prepareConfig,
                                                   rtmToken: rtmToken)
                vc.videoEncoderConfig = videoEncoderConfig
                showVc = vc
            #else
                AUIToast.show(text: NSLocalizedString("rtm_not_found", comment: ""))
            #endif
            } else {
                let vc = EMPure1v1RoomViewController(currentUid: currentUserId,
                                                     prepareConfig: prepareConfig)
                vc.videoEncoderConfig = videoEncoderConfig
                showVc = vc
            }
            guard let vc = showVc else { return }
            vc.modalPresentationStyle = .fullScreen
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
