import {
  IAgoraRTCClient,
  enableLogUpload,
  setParameter,
  createClient,
  setLogLevel,
  createMicrophoneAndCameraTracks
} from "agora-rtc-sdk-ng/esm"
import { CallInfo } from "./callInfo"
import {
  ICallConfig,
  IPrepareConfig,
  ICallMessage,
  CallApiEvents,
  ILocalTracks,
  IRemoteTracks,
  CallStateType,
  CallStateReason,
  CallAction,
  RejectByInternal,
  CallType,
  LogLevel,
  CallEvent,
  CallErrorEvent,
  CallErrorCodeType,
} from "../types"
import {
  AGEventEmitter,
  logger,
  serializeHTMLElement,
  uuidv4,
  setElementVisibility,
  clearHTMLElement,
  isInDOM,
  encodeMessage,
  decodeMessage
} from "../common"

enableLogUpload()
setLogLevel(1)
setParameter("ENABLE_INSTANT_VIDEO", true)

export const VERSION = "1.0.0-beta.3"

export class CallApi extends AGEventEmitter<CallApiEvents> {
  version: string = VERSION
  callConfig: ICallConfig
  prepareConfig: IPrepareConfig = {}
  state: CallStateType = CallStateType.idle
  remoteUserId: number = 0
  callId: string = ""
  localTracks: ILocalTracks = {}
  remoteTracks: IRemoteTracks = {}
  rtcClient: IAgoraRTCClient
  callType: CallType = CallType.video
  // ------- private -------
  private _callInfo: CallInfo = new CallInfo()
  private _rtcJoined: boolean = false
  private _receiveRemoteFirstFrameDecoded = false
  private _cancelCallTimer: any = null
  private _clientId: string = ""

  get callMessageManager() {
    if (!this.callConfig.callMessageManager) {
      logger.error(`[${this._clientId}]  callMessageManager is undefined `)
      throw new Error("callMessageManager is undefined")
    }
    return this.callConfig.callMessageManager
  }

  get roomId() {
    return this.prepareConfig?.roomId || ""
  }

  get isBusy() {
    return (
      this.state == CallStateType.calling ||
      this.state == CallStateType.connected ||
      this.state == CallStateType.connecting
    )
  }

  constructor(config: ICallConfig) {
    super()
    this.callConfig = config
    this._clientId = `call-client-${uuidv4()}`;
    this.rtcClient = config.rtcClient
      ? config.rtcClient
      : createClient({ mode: "live", codec: "vp9", role: "host" })
    if (typeof config.logLevel == "number") {
      logger.setLogLevel(config.logLevel)
    }
    this._listenRtcEvents()
    this._listenMessagerManagerEvents()
    // privacy protection （Do not print sensitive information）
    logger.debug(`[${this._clientId}] init client success , `, {
      userId: config.userId,
      logLevel: config.logLevel,
      version: this.version,
    })
  }

  // ------- public -------

  /**
   * 设置日志等级
   * @param level 等级
   */
  setLogLevel(level: LogLevel) {
    logger.setLogLevel(level)
  }

  /**
   * 获取通话Id
   */
  getCallId() {
    return this.callId
  }

  /**
   * 准备呼叫
   * @param prepareConfig 准备呼叫配置
   */
  async prepareForCall(prepareConfig: Partial<IPrepareConfig>) {
    logger.debug(`[${this._clientId}] start set prepareConfig`);
    if (this.isBusy) {
      this._callEventChange(CallEvent.stateMismatch)
      const message = "currently busy!"
      logger.error(`[${this._clientId}]`,message)
      throw new Error(message)
    }
    this.prepareConfig = {
      ...this.prepareConfig,
      ...prepareConfig,
    }
    this._callStateChange(CallStateType.prepared, CallStateReason.none)
    const { localView, remoteView, rtcToken, ...printConfig } =
      this.prepareConfig
    logger.debug(
      `[${this._clientId}] prepareForCall success`,
      JSON.stringify({
        ...printConfig,
        localView: serializeHTMLElement(localView),
        remoteView: serializeHTMLElement(remoteView),
      }),
    )
  }

  /**
   * 发起呼叫 （主叫）
   * @param remoteUserId 远端用户Id
   * @param callType 呼叫类型
   */
  async call(remoteUserId: number, callType?: CallType) {
    logger.debug(
      `[${this._clientId}] start call,remoteUserId:${remoteUserId},callType:${
        callType ?? CallType.video
      }`
    )
    if (this.state !== CallStateType.prepared) {
      this._callEventChange(CallEvent.stateMismatch)
      const message = `call failed! current state:${this.state} is not prepared state ${CallStateType.prepared}`
      logger.error(`[${this._clientId}]`, message)
      throw new Error(message)
    }
    this._callInfo.start()
    this.remoteUserId = remoteUserId
    this.callType = callType ?? CallType.video
    const callStateReason =
      this.callType == CallType.video
        ? CallStateReason.localVideoCall
        : CallStateReason.localAudioCall
    this._callStateChange(CallStateType.calling, callStateReason, "", {
      remoteUserId,
      fromUserId: this.callConfig.userId,
    })
    this._callEventChange(CallEvent.onCalling)
    this.callId = uuidv4()
    const callAction =
      this.callType == CallType.video
        ? CallAction.VideoCall
        : CallAction.AudioCall
    this._autoCancelCall(true)
    this._rtcLocalJoinPlayPublish()
    await this._publishMessage(remoteUserId, {
      callId: this.callId,
      fromUserId: this.callConfig.userId,
      remoteUserId,
      fromRoomId: this.prepareConfig?.roomId,
      message_action: callAction,
    })
    this._callInfo.add("remoteUserRecvCall")
    this._callEventChange(CallEvent.remoteUserRecvCall)
    logger.debug(`[${this._clientId}] call success,remoteUserId:${remoteUserId}`)
  }

  /**
   * 取消呼叫 (主叫)
   */
  async cancelCall() {
    logger.debug(`[${this._clientId}] start cancelCall`)
    try {
    await this.destroy()
    this._callStateChange(CallStateType.prepared, CallStateReason.localCancel)
    this._callEventChange(CallEvent.localCancelled)
    // temp data
    const callId = this.callId
    const remoteUserId = this.remoteUserId
    const fromUserId = this.callConfig.userId
    // then reset data
    this._resetData()
    await this._publishMessage(remoteUserId, {
      callId: callId,
      fromUserId: fromUserId,
      remoteUserId: remoteUserId,
      message_action: CallAction.Cancel,
      cancelCallByInternal: RejectByInternal.External,
    })
    logger.debug(`[${this._clientId}] cancelCall success`)
    } catch (error) {
      logger.error(`[${this._clientId}] cancelCall error ${JSON.stringify(error)}`)
    }
  }

  /**
   * 拒绝通话 (被叫)
   * @param remoteUserId 远端用户Id
   * @param reason 原因
   */
  async reject(remoteUserId: number, reason?: string) {
    logger.debug(`[${this._clientId}] start reject,remoteUserId:${remoteUserId},reason:${reason}`)
    await this.destroy()
    this._callStateChange(
      CallStateType.prepared,
      CallStateReason.localRejected,
      reason,
    )
    this._callEventChange(CallEvent.localRejected)
    // temp data
    const callId = this.callId
    const fromUserId = this.callConfig.userId
    // reset data
    this._resetData()
    await this._publishMessage(remoteUserId, {
      callId: callId,
      fromUserId: fromUserId,
      remoteUserId,
      message_action: CallAction.Reject,
      rejectReason: reason,
      rejectByInternal: RejectByInternal.External,
    })
    logger.debug(`[${this._clientId}] reject success,remoteUserId:${remoteUserId},reason:${reason}`)
  }

  /**
   * 接受通话 (被叫)
   * @param remoteUserId 远端用户Id
   */
  async accept(remoteUserId: number) {
    logger.debug(`[${this._clientId}] start accept,remoteUserId:${remoteUserId}`)
    if (this.state !== CallStateType.calling) {
      this._callEventChange(CallEvent.stateMismatch)
      const message = `accept fail! current state:${this.state} is not calling state ${CallStateType.calling}`
      logger.error(`[${this._clientId}]`,message)
      throw new Error(message)
    }
    if (remoteUserId !== this.remoteUserId) {
      logger.warn(`[${this._clientId}] accept uid:${remoteUserId} but not expected uid:${this.remoteUserId}`)
    }
    this._callEventChange(CallEvent.localAccepted)
    this._callInfo.add("acceptCall")
    this._callStateChange(
      CallStateType.connecting,
      CallStateReason.localAccepted,
    )
    await Promise.all([
      this._publishMessage(remoteUserId, {
        callId: this.callId,
        fromUserId: this.callConfig.userId,
        remoteUserId,
        message_action: CallAction.Accept,
      }),
      this._checkViewVisible()
    ])
    logger.debug(`[${this._clientId}] accept success,remoteUserId:${remoteUserId}`)
  }

  /**
   * 挂断通话
   * @param remoteUserId 远端用户Id
   */
  async hangup(remoteUserId: number) {
    logger.debug(`[${this._clientId}] start hangup ,remoteUserId:${remoteUserId}`)
    await this.destroy()
    this._callStateChange(CallStateType.prepared, CallStateReason.localHangup)
    this._callEventChange(CallEvent.localHangup)
    // temp data
    const callId = this.callId
    const fromUserId = this.callConfig.userId
    // then reset data
    this._resetData()
    await this._publishMessage(remoteUserId, {
      callId: callId,
      fromUserId: fromUserId,
      remoteUserId,
      message_action: CallAction.Hangup,
    })
    logger.debug(`[${this._clientId}] hangup success,remoteUserId:${remoteUserId}`)
  }

  /**
   * 销毁
   */
  async destroy() {
    logger.debug(`[${this._clientId}] start destroy`)
    try {
      // stop remote audio track
      this.remoteTracks.audioTrack?.stop()
      // clear local tracks 
      if (this.localTracks?.audioTrack) {
        logger.debug(`[${this._clientId}] local audio track close start`)
        this.localTracks?.audioTrack.close()
        logger.debug(`[${this._clientId}] local audio track close success`)
      }
      if (this.localTracks?.videoTrack) {
        logger.debug(`[${this._clientId}] local video track close start`)
        this.localTracks?.videoTrack.close()
        logger.debug(`[${this._clientId}] local video track close success`)
      }
      // clear views
      if (this.prepareConfig.localView) {
        clearHTMLElement(this.prepareConfig.localView)
        logger.debug(`[${this._clientId}]  localView clear success`)
      }
      if (this.prepareConfig.remoteView) {
        clearHTMLElement(this.prepareConfig.remoteView)
        logger.debug(`[${this._clientId}] remoteView clear success`)
      }
      // deal rtc leave
      if (this._rtcJoined) {
        logger.debug(`[${this._clientId}]  rtc leave start`)
        await this.rtcClient.leave()
        logger.debug(`[${this._clientId}]  rtc leave success`)
        this._rtcJoined = false
        this._callEventChange(CallEvent.localLeft)
      }
    } catch (e) {
      this._callError(CallErrorEvent.rtcOccurError, CallErrorCodeType.rtc, e)
    }
    logger.debug(`[${this._clientId}]  destroy success`)
  }
  // ------- public -------

  // ------- private -------
  private _listenMessagerManagerEvents() {
    this.callMessageManager.on("messageReceive", async (message) => {
      const data = decodeMessage(message)
      const { message_action } = data
      logger.debug(
        `[${this._clientId}]  message receive success action:${CallAction[message_action]},remoteUserId:${data.remoteUserId} `,
        message
      )
      switch (message_action) {
        // receive video call
        case CallAction.VideoCall:
          await this._receiveVideoCall(data)
          break
        // receive audio call
        case CallAction.AudioCall:
          // TODO: audio call
          break
        // receive cancel
        case CallAction.Cancel:
          await this._receiveCancelCall(data)
          break
        // receive accept
        case CallAction.Accept:
          await this._receiveAccept(data)
          break
        // receive reject
        case CallAction.Reject:
          await this._receiveReject(data)
          break
        // receive hangup
        case CallAction.Hangup:
          await this._receiveHangup(data)
          break
      }
    })
  }

  private async _receiveCancelCall(data: ICallMessage) {
    const { fromUserId, cancelCallByInternal } = data
    if (!this._isCallingUser(fromUserId)) {
      return
    }
    await this.destroy()
    this._callStateChange(
      CallStateType.prepared,
      CallStateReason.remoteCancel,
      "",
      { cancelCallByInternal },
    )
    this._callEventChange(CallEvent.remoteCancelled)
    this._resetData()
  }

  private async _receiveVideoCall(data: ICallMessage) {
    const { callId, fromUserId, fromRoomId, remoteUserId } = data
    if (!this._isCallingUser(fromUserId)) {
      this._autoReject(Number(fromUserId))
      return
    }
    this._callInfo.start()
    this.callId = callId
    this.remoteUserId = Number(fromUserId)
    this.prepareConfig.roomId = fromRoomId
    this._autoCancelCall(false)
    this._callStateChange(
      CallStateType.calling,
      CallStateReason.remoteVideoCall,
      "",
      // on this eventInfo
      // remoteUserId 指向本次通话的被叫方
      // fromUserId 指向本次通话的主叫方
      {
        remoteUserId: Number(remoteUserId),
        fromUserId: Number(fromUserId),
      },
    )
    this._callEventChange(CallEvent.onCalling)
    this._rtcLocalJoinPlayPublish()
  }

  private async _receiveAccept(data: ICallMessage) {
    const { fromUserId } = data
    if (this.state !== CallStateType.calling || !this._isCallingUser(fromUserId)) {
      return
    }
    this._callInfo.add("acceptCall")
    this._callEventChange(CallEvent.remoteAccepted)
    this._callStateChange(
      CallStateType.connecting,
      CallStateReason.remoteAccepted,
    )
    this._checkViewVisible()
  }

  private async _receiveHangup(data: ICallMessage) {
    const { fromUserId } = data
    if (!this._isCallingUser(fromUserId)) {
      return
    }
    await this.destroy()
    this._callStateChange(CallStateType.prepared, CallStateReason.remoteHangup)
    this._callEventChange(CallEvent.remoteHangup)
    this._resetData()
  }

  private async _receiveReject(data: ICallMessage) {
    const { fromUserId, rejectByInternal, rejectReason } = data
    if (!this._isCallingUser(fromUserId)) {
      return
    }
    await this.destroy()
    const stateReason =
      rejectByInternal == RejectByInternal.Internal
        ? CallStateReason.remoteCallBusy
        : CallStateReason.remoteRejected
    if (stateReason == CallStateReason.remoteCallBusy) {
      this._callEventChange(CallEvent.remoteCallBusy)
    }
    this._callStateChange(CallStateType.prepared, stateReason, "", {
      rejectReason,
    })
    this._callEventChange(CallEvent.remoteRejected)
    this._resetData()
  }

  private _isCallingUser = (userId: string | number) => {
    if (!this.remoteUserId) {
      return true
    }
    return this.remoteUserId == Number(userId)
  }

  /**
   * 检查是否可以将 localView/remoteView 显示出来
   * prepareConfig.firstFrameWaittingDisabled true 不等待首帧可append
   * prepareConfig.firstFrameWaittingDisabled false 需要等待首帧才要append
   */
  private _checkViewVisible() {
    if (this.state !== CallStateType.connecting) {
      return
    }
    if (
      this.prepareConfig?.firstFrameWaittingDisabled ||
      this._receiveRemoteFirstFrameDecoded
    ) {
      this._callStateChange(
        CallStateType.connected,
        CallStateReason.recvRemoteFirstFrame,
      )
      // show localView 
      if (this.prepareConfig.localView) {
        setElementVisibility(this.prepareConfig.localView, true)
        logger.debug(`[${this._clientId}]  localView set visibility visible`)
      }
      // show remoteView
      if (this.prepareConfig.remoteView) {
        setElementVisibility(this.prepareConfig.remoteView, true)
        logger.debug(`[${this._clientId}]  remoteView set visibility visible`)
      }
      // play remote audio
      this._playRemoteAudio()
    }
  }

  private async _rtcLocalJoinPlayPublish() {
    try {
      await Promise.all([
        this._createLocalTracks(),
        this._rtcJoin()
      ])
      // play local video track 
      this._playLocalVideo()
      // then publish track
      await this._rtcPublish()
      if (this.state == CallStateType.prepared) {
        // when call then fast cancelCall
        await this.destroy()
      }
    } catch (err) {
      this._callError(CallErrorEvent.rtcOccurError, CallErrorCodeType.rtc, err)
    }
  }

  private _playLocalVideo() {
    const videoTrack = this.localTracks.videoTrack
    if (!videoTrack) {
      const msg = "local video track is undefined"
      return logger.debug(`[${this._clientId}]`,msg)
    }
    if (videoTrack.isPlaying) {
      return logger.debug(`[${this._clientId}] local video track is playing`)
    }
    if (!this.prepareConfig.localView) {
      const message = "localView is undefined"
      const error = new Error(message)
      logger.error(`[${this._clientId}]`,message)
      throw error
    }
    if (!isInDOM(this.prepareConfig.localView)) {
      const message = "localView is not in dom"
      const error = new Error(message)
      logger.error(`[${this._clientId}]`,message)
      throw error
    }
    if (this.state != CallStateType.connected) {
      // when connected localView should be visible
      // when not connected hide localView
      setElementVisibility(this.prepareConfig.localView, false)
      logger.debug(`[${this._clientId}] localView set visibility hidden`)
    }
    // clear localView inner html
    clearHTMLElement(this.prepareConfig.localView)
    // play local video
    videoTrack.play(this.prepareConfig.localView)
    logger.debug(`[${this._clientId}] local video track play success`)
  }

  private _palyRemoteVideo() {
    logger.debug(`[${this._clientId}] start play remote video`)
    const videoTrack = this.remoteTracks.videoTrack
    if (!videoTrack) {
      const msg = "remote video track is undefined"
      return logger.debug(`[${this._clientId}]`,msg)
    }
    if (videoTrack.isPlaying) {
      return logger.debug(`[${this._clientId}] remote video track is playing`)
    }
    if (!this.prepareConfig.remoteView) {
      const message = "remoteView is undefined"
      const error = new Error(message)
      logger.error(`[${this._clientId}]`,message)
      throw error
    }
    if (!isInDOM(this.prepareConfig.remoteView)) {
      const message = "remoteView is not in dom"
      const error = new Error(message)
      logger.error(`[${this._clientId}]`,message)
      throw error
    }
    if (this.state != CallStateType.connected) {
      // when connected remoteView should be visible
      // when not connected hide remoteView
      setElementVisibility(this.prepareConfig.remoteView, false)
      logger.debug(`[${this._clientId}] remoteView set visibility hidden`)
    }
    // clear remoteView inner html
    clearHTMLElement(this.prepareConfig.remoteView)
    // play remote video
    videoTrack.play(this.prepareConfig.remoteView)
    logger.debug(`[${this._clientId}] remote video track play success`)
  }

  private _playRemoteAudio() {
    logger.debug(`[${this._clientId}] start play remote audio`)
    const audioTrack = this.remoteTracks.audioTrack
    if (!audioTrack) {
      const msg = "remote audio track is undefined"
      return logger.debug(`[${this._clientId}]`,msg)
    }
    if (audioTrack.isPlaying) {
      return logger.debug(`[${this._clientId}] remote audio track is playing`)
    }
    audioTrack.play()
    logger.debug(`[${this._clientId}] remote audio track play success`)
  }

  private _listenRtcEvents() {
    this.rtcClient.on("user-joined", (user) => {
      logger.debug(`[${this._clientId}] RTC EVENT: remote user joined ,uid:${user.uid}`)
      if (user.uid != this.remoteUserId) {
        return
      }
      logger.debug(`[${this._clientId}] rtc remote user join,uid:${user.uid}`)
      this._callInfo.add("remoteUserJoinChannel")
      this._callEventChange(CallEvent.remoteJoined)
    })
    this.rtcClient.on("user-left", async (user) => {
      logger.debug(`[${this._clientId}] RTC EVENT: remote user left ,uid:${user.uid}`)
      if (user.uid != this.remoteUserId) {
        return
      }
      logger.debug(`[${this._clientId}] rtc remote user leave,uid:${user.uid}`)
      this._callEventChange(CallEvent.remoteLeft)
    })
    this.rtcClient.on("user-published", async (user, mediaType) => {
      if (user.uid != this.remoteUserId) {
        return
      }
      logger.debug(`[${this._clientId}] RTC EVENT: user-published,uid:${user.uid},mediaType:${mediaType}`)
      await this.rtcClient?.subscribe(user, mediaType)
      logger.debug(
        `[${this._clientId}] subscribe user success,uid:${user.uid},mediaType:${mediaType}`,
      )
      if (mediaType === "video") {
        const remoteVideoTrack = user.videoTrack
        this.remoteTracks.videoTrack = remoteVideoTrack
        remoteVideoTrack?.on(
          "first-frame-decoded",
          this._handleRemoteFirstFrameDecoded.bind(this),
        )
        this._palyRemoteVideo()
      } else if (mediaType == "audio") {
        const remoteAudioTrack = user.audioTrack
        this.remoteTracks.audioTrack = remoteAudioTrack
        if (this.state == CallStateType.connected) {
          // firstFrameWaittingDisabled true  有可能先connected再收到远端音频流
          // 这种情况下需要主动播放远端音频流声音
          this._playRemoteAudio()
        }
      }
    })
    this.rtcClient.on("user-unpublished", async (user, mediaType) => {
      logger.debug(`[${this._clientId}] RTC EVENT: user-unpublished,uid:${user.uid},mediaType:${mediaType}`)
      if (user.uid != this.remoteUserId) {
        return
      }
      await this.rtcClient?.unsubscribe(user, mediaType)
      logger.debug(
        `[${this._clientId}] unsubscribe user success,uid:${user.uid},mediaType:${mediaType}`,
      )
      if (mediaType === "video") {
        this.remoteTracks.videoTrack = undefined
      } else if (mediaType == "audio") {
        this.remoteTracks.audioTrack = undefined
      }
    })
    // this.rtcClient.on("token-privilege-will-expire", async () => {
    //   // rtm renew token
    // })
  }

  private async _autoReject(remoteUserId: number) {
    await this._publishMessage(remoteUserId, {
      callId: this.callId,
      fromUserId: this.callConfig.userId,
      remoteUserId,
      message_action: CallAction.Reject,
      rejectReason: "busy",
      rejectByInternal: RejectByInternal.Internal,
    })
    logger.debug(`[${this._clientId}] busy state, auto reject remoteUserId:${remoteUserId} success`)
  }

  private async _autoCancelCall(isLocal: boolean) {
    if (!this.remoteUserId) {
      return
    }
    logger.warn(`[${this._clientId}] timeout,start auto cancelCall`)
    const time = this.prepareConfig?.callTimeoutMillisecond
    if (time) {
      if (this._cancelCallTimer) {
        clearTimeout(this._cancelCallTimer)
        this._cancelCallTimer = null
      }
      this._cancelCallTimer = setTimeout(async () => {
        if (
          this.state == CallStateType.calling ||
          this.state == CallStateType.connecting
        ) {
          await this.destroy()
          this._callStateChange(
            CallStateType.prepared,
            CallStateReason.callingTimeout,
          )
          this._callEventChange(isLocal ? CallEvent.callingTimeout : CallEvent.remoteCallingTimeout)
          // temp data 
          const callId = this.callId
          const remoteUserId = this.remoteUserId
          const fromUserId = this.callConfig.userId
          // then reset data
          this._resetData()
          await this._publishMessage(remoteUserId, {
            callId: callId,
            fromUserId: fromUserId,
            remoteUserId: remoteUserId,
            message_action: CallAction.Cancel,
            cancelCallByInternal: RejectByInternal.Internal,
          })
          logger.debug(`[${this._clientId}]  auto cancelCall success`)
        }
      }, time)
    }
  }

  private async _rtcJoin() {
    if (this._rtcJoined) {
      return logger.warn(`[${this._clientId}]  rtc has joined`)
    }
    const { appId, userId } = this.callConfig
    const { rtcToken, roomId } = this.prepareConfig
    if (!roomId) {
      logger.error(`[${this._clientId}] rtc has joined`)
      throw new Error("roomId is undefined")
    }
    if (!rtcToken) {
      logger.error(`[${this._clientId}] rtcToken is undefined`)
      throw new Error("rtcToken is undefined")
    }
    logger.debug(`[${this._clientId}] start join rtc channel,roomId:${roomId},userId:${userId}`)
    this._callEventChange(CallEvent.joinRTCStart)
    await this.rtcClient.join(appId, roomId, rtcToken, userId)
    logger.debug(`[${this._clientId}] rtc join success,roomId:${roomId},userId:${userId}`)
    this._rtcJoined = true
    this._callEventChange(CallEvent.joinRTCSuccessed)
    this._callEventChange(CallEvent.localJoined)
    this._callInfo.add("localUserJoinChannel")
  }

  private async _createLocalTracks() {
    const { audioConfig, videoConfig } = this.prepareConfig!
    const tracks = await createMicrophoneAndCameraTracks(
      audioConfig,
      videoConfig,
    )
    this.localTracks.audioTrack = tracks[0]
    this.localTracks.videoTrack = tracks[1]
  }

  private async _rtcPublish() {
    if (!this._rtcJoined) {
      // when call then fast cancelCall
      return logger.warn(`[${this._clientId}] rtc not joined when publish`)
    }
    if (this.localTracks.videoTrack && this.localTracks.audioTrack) {
      logger.debug(`[${this._clientId}] start publish video and audio track`)
      await this.rtcClient.publish([
        this.localTracks.videoTrack,
        this.localTracks.audioTrack,
      ])
      logger.debug(`[${this._clientId}] rtc publish success`)
      this._callEventChange(CallEvent.publishFirstLocalVideoFrame)
    } else {
      const msg = "videoTrack or audioTrack is undefined"
      logger.error(`[${this._clientId}] `,msg)
      throw new Error(msg)
    }
  }

  private async _publishMessage(
    uid: string | number,
    message: Partial<ICallMessage>,
  ) {
    try {
      const finMessage = encodeMessage(message)
      logger.debug(`[${this._clientId}]  start send message, remote uid:${uid} `, finMessage)
      await this.callMessageManager.sendMessage(uid.toString(), finMessage)
      logger.debug(`[${this._clientId}]  message send success, remote uid:${uid} `, finMessage)
    } catch (e) {
      this._callError(
        CallErrorEvent.sendMessageFail,
        CallErrorCodeType.message,
        e,
      )
      throw e
    }
  }

  private _callError(
    errorEvent: CallErrorEvent,
    errorType: CallErrorCodeType,
    err: any, // Error
  ) {
    logger.error(
      `[${this._clientId}]  onCallError! errorEvent:${errorEvent},errorType:${errorType},errorCode:${err.code},message:${err.message}`
    )
    this.emit("callError", errorEvent, errorType, err.code, err.message)
  }

  private _callStateChange(
    state: CallStateType,
    stateReason: CallStateReason,
    eventReason?: string,
    eventInfo?: Record<string, any>,
  ) {
    if (this.state == state) {
      return
    }
    logger.debug(
      `[${this._clientId}] callStateChanged current state number:[${this.state} status is :${
        CallStateType[this.state]
      }],target state:[${state} status is :${
        CallStateType[state]
      }]stateReason:${stateReason},eventReason:${eventReason},eventInfo:${JSON.stringify(
        eventInfo
      )}`
    )
    this.state = state
    this.emit("callStateChanged", state, stateReason, eventReason, eventInfo)
  }

  private _callEventChange(event: CallEvent) {
    logger.debug(
      `[${this._clientId}] callEventChanged event code: [${event} status is :${CallEvent[event]}]`
    )
    this.emit("callEventChanged", event)
  }

  private _resetData() {
    this.callId = ""
    this.remoteUserId = 0
    this.localTracks = {}
    this.remoteTracks = {}
    this._rtcJoined = false
    this._receiveRemoteFirstFrameDecoded = false
    if (this._cancelCallTimer) {
      clearTimeout(this._cancelCallTimer)
      this._cancelCallTimer = null
    }
    this._callInfo.end()
  }

  private _handleRemoteFirstFrameDecoded() {
    logger.debug(`[${this._clientId}] remote first frame decoded`)
    this._callEventChange(CallEvent.recvRemoteFirstFrame)
    this._receiveRemoteFirstFrameDecoded = true
    this._callInfo.add("recvFirstFrame")
    const info = this._callInfo.getInfo()
    this.emit("callInfoChanged", info)
    logger.debug(`[${this._clientId}] callInfoChanged: `, info)
    this._checkViewVisible()
  }
  // ------- private -------
}
