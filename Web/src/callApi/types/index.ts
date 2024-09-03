import { CallMessageManager } from "../messageManager"
import type {
  IAgoraRTCClient,
  ICameraVideoTrack,
  IMicrophoneAudioTrack,
  IRemoteAudioTrack,
  IRemoteVideoTrack,
  MicrophoneAudioTrackInitConfig,
  CameraVideoTrackInitConfig,
} from "agora-rtc-sdk-ng/esm"

export interface ICallConfig {
  /** 声网 App Id */
  appId: string
  /** 声网 App 证书 */
  appCertificate: string
  /** 用户 ID */
  userId: number
  /**  rtc client 实例 */
  rtcClient?: IAgoraRTCClient
  /** 日志等级 */
  logLevel?: LogLevel
  /** CallMessageManager 实例  */
  callMessageManager: CallMessageManager
}

export interface IPrepareConfig {
  /** 房间ID（自己的RTC频道名，用于呼叫对端用户时让对端用户加入这个RTC频道） */
  roomId?: string
  /** rtc token（需要使用万能token，token创建的时候channel name为空字符串） */
  rtcToken?: string
  /** 自动接受 */
  autoAccept?: boolean
  /** 显示本地流的画布  */
  localView?: HTMLElement
  /** 显示远端流的画布  */
  remoteView?: HTMLElement
  /** 呼叫超时时间 (0表示内部不处理超时) */
  callTimeoutMillisecond?: number
  /** 音频track配置 */
  audioConfig?: MicrophoneAudioTrackInitConfig
  /** 视频track配置 */
  videoConfig?: CameraVideoTrackInitConfig
  /**
   * 接通状态是否禁止等待首帧 
   * 
   * true:是，主叫收到接受消息即认为通话成功，被叫点击接受即认为通话成功，注意，使用该方式可能会造成无音视频权限时也能接通，以及接通时由于弱网等情况看不到画面
   *
   * false: 否，会等待音频首帧（音频呼叫）或视频首帧(视频呼叫)
   */
  firstFrameWaittingDisabled?: boolean
}

/**
 * 呼叫状态
 * @enum
 */
export enum CallStateType {
  /** 空闲 */
  idle = 0,
  /** 已准备 */
  prepared = 1,
  /** 呼叫中 */
  calling = 2,
  /** 连接中 */
  connecting = 3,
  /** 通话中 */
  connected = 4,
  /** 失败 */
  failed = 10,
}

/**
 * 呼叫状态改变的原因
 */
export enum CallStateReason {
  /** 无 */
  none = 0,
  /** 加入RTC失败 */
  joinRTCFailed = 1,
  /** 设置RTM失败 */
  rtmSetupFailed = 2,
  /** 设置RTM成功 */
  rtmSetupSuccessed = 3,
  /** 消息发送失败 */
  messageFailed = 4,
  /** 本地用户拒绝 */
  localRejected = 5,
  /** 远端用户拒绝 */
  remoteRejected = 6,
  /** 远端用户接受 */
  remoteAccepted = 7,
  /** 本地用户接受 */
  localAccepted = 8,
  /** 本地用户挂断 */
  localHangup = 9,
  /** 远端用户挂断 */
  remoteHangup = 10,
  /** 本地用户取消呼叫 */
  localCancel = 11,
  /** 远端用户取消呼叫 */
  remoteCancel = 12,
  /** 收到远端首帧 */
  recvRemoteFirstFrame = 13,
  /** 呼叫超时 */
  callingTimeout = 14,
  /** 同样的主叫呼叫不同频道导致取消 */
  cancelByCallerRecall = 15,
  /** rtm超时断连 */
  rtmLost = 16,
  /** 远端用户忙 */
  remoteCallBusy = 17,
  /** 本地发起视频呼叫 */
  localVideoCall = 30,
  /** 本地发起音频呼叫 */
  localAudioCall = 31,
  /** 远端发起视频呼叫 */
  remoteVideoCall = 32,
  /** 远端发起音频呼叫 */
  remoteAudioCall = 33,
}

/**
 * 呼叫事件
 */
export enum CallEvent {
  /** 无 */
  none = 0,
  /** 销毁 */
  deinitialize = 1,
  /** 没有收到消息回执 */
  // missingReceipts = 2,
  /** 呼叫超时 */
  callingTimeout = 3,
  /** 远端呼叫超时 */
  remoteCallingTimeout = 4,
  /** 加入RTC成功 */
  joinRTCSuccessed = 5,
  /** 状态流转异常 */
  stateMismatch = 9,
  /** 开始加入rtc */
  joinRTCStart = 10,
  /** 主叫呼叫成功 */
  remoteUserRecvCall = 99,
  /** 本地用户拒绝 */
  localRejected = 100,
  /** 远端用户拒绝 */
  remoteRejected = 101,
  /** 变成呼叫中 */
  onCalling = 102,
  /** 远端用户接受 */
  remoteAccepted = 103,
  /** 本地用户接受 */
  localAccepted = 104,
  /** 本地用户挂断 */
  localHangup = 105,
  /** 远端用户挂断 */
  remoteHangup = 106,
  /** 远端用户加入RTC频道 */
  remoteJoined = 107,
  /** 远端用户离开RTC频道 */
  remoteLeft = 108,
  /** 本地用户取消呼叫 */
  localCancelled = 109,
  /** 远端用户取消呼叫 */
  remoteCancelled = 110,
  /** 本地用户加入RTC频道 */
  localJoined = 111,
  /** 本地用户离开RTC频道 */
  localLeft = 112,
  /** 收到远端首帧 */
  recvRemoteFirstFrame = 113,
  /** 远端用户忙 */
  remoteCallBusy = 117,
  /** 推送首帧视频帧成功 */
  publishFirstLocalVideoFrame = 120,
  /** 本地发起视频呼叫 */
  localVideoCall = 140,
  /** 本地发起音频呼叫 */
  localAudioCall = 141,
  /** 远端发起视频呼叫 */
  remoteVideoCall = 142,
  /** 远端发起音频呼叫 */
  remoteAudioCall = 143,
}

/**
 * 呼叫类型
 */
export enum CallType {
  /** 视频呼叫  */
  video = 0,
  /** 音频呼叫  */
  audio,
}

/** 呼叫错误事件 */
export enum CallErrorEvent {
  /** 通用错误 */
  normalError = 0,
  /** rtc出现错误 */
  rtcOccurError = 100, //
  //  startCaptureFail = 110       //rtc开启采集失败
  /** 消息发送失败 */
  sendMessageFail = 210,
}

/** 呼叫错误事件的错误码类型 */
export enum CallErrorCodeType {
  /** 业务类型的错误 */
  normal = 0,
  /**
   * rtc错误
   * https://doc.shengwang.cn/doc/rtc/javascript/error-code
   */
  rtc = 1,
  /**
   * 消息的错误
   * 若消息通道为rtm，则参考 https://doc.shengwang.cn/doc/rtm2/javascript/error-codes
   */
  message = 2,
}

export interface CallApiEvents {
  /**
   * 状态响应回调
   * @param state 状态类型
   * @param stateReason 状态变更的原因
   * @param eventReason 事件类型描述
   * @param eventInfo 扩展信息，不同事件类型参数不同
   */
  callStateChanged: (
    state: CallStateType,
    stateReason: CallStateReason,
    eventReason?: string,
    eventInfo?: Record<string, any>,
  ) => void
  /**
   *  内部详细事件变更回调
   *  @param event 事件
   *  @param eventReason 事件原因
   */
  callEventChanged: (event: CallEvent, eventReason?: string) => void
  /**
   *  内部详细通话信息指标回调
   *  @param callInfo 通话信息
   */
  callInfoChanged: (callInfo: ICallInfo) => void
  /**
   * 发生错误的回调
   * @param errorEvent 错误事件
   * @param errorType 错误类型
   * @param errorCode 错误码
   * @param message 错误信息
   */
  callError: (
    errorEvent: CallErrorEvent,
    errorType: CallErrorCodeType,
    errorCode: string | number,
    message: string,
  ) => void
}

export interface CallMessageManagerEvents {
  messageReceive: (data: string) => void
  // TODO:[callApi] tokenWillExpire internal implementation
  tokenWillExpire: (channelName: string) => void // channelName
  disconnected: (channelName: string) => void // channelName
}

/** 通话信息 */
export interface ICallInfo {
  /** 主叫呼叫成功，收到呼叫成功表示已经送达对端(被叫) */
  remoteUserRecvCall: number
  /** 主叫收到被叫接受呼叫(onAccept)/被叫点击接受(accept) */
  acceptCall: number
  /** 本地用户加入频道 */
  localUserJoinChannel: number
  /** 远端用户加入频道 */
  remoteUserJoinChannel: number
  /** 收到对端首帧 */
  recvFirstFrame: number
}

/**
 * 拒绝原因
 */
export enum RejectByInternal {
  /** 非内部拒绝 */
  External = 0,
  /** 内部拒绝(表示被叫呼叫正忙) */
  Internal = 1,
}

/** 日志等级 */
export enum LogLevel {
  DEBUG = 0,
  WARN = 1,
  ERROR = 2,
}

/** 本地用户轨道 */
export interface ILocalTracks {
  videoTrack?: ICameraVideoTrack
  audioTrack?: IMicrophoneAudioTrack
}

/** 远端用户轨道 */
export interface IRemoteTracks {
  videoTrack?: IRemoteVideoTrack
  audioTrack?: IRemoteAudioTrack
}

// -------------- hidden --------------

/** @hidden */
export type EventHandler<T extends any[]> = (...data: T) => void

/** @hidden */
export interface ICallMessage {
  message_version: string
  message_timestamp?: number
  callId: string
  remoteUserId: number
  fromUserId: number
  message_action: CallAction
  fromRoomId: string
  rejectReason?: string // 拒绝原因
  rejectByInternal?: RejectByInternal // 拒绝原因
  cancelCallByInternal?: RejectByInternal // 取消呼叫原因
}

/** @hidden */
export enum CallAction {
  VideoCall = 0, // 视频呼叫,
  Cancel = 1, // 取消呼叫
  Accept = 2, // 接受
  Reject = 3, // 拒绝
  Hangup = 4, // 挂断
  AudioCall = 10, // 音频呼叫
}
