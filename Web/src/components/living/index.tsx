import { useEffect, useState, useMemo, useCallback } from "react"
import {
  getRandomUid, apiGenerateToken,
  uuidv4, DEFAULT_RTM_CONFIG, APPID, APPCERTIFICATE,
  CALL_TIMEOUT_MILLISECOND, DEFAULT_VIDEO_ENCODER_CONFIG,
} from "@/utils"
import {
  createClient, IAgoraRTCClient, createMicrophoneAndCameraTracks,
  IMicrophoneAudioTrack,
  ICameraVideoTrack,
  IRemoteAudioTrack,
  IRemoteVideoTrack
} from "agora-rtc-sdk-ng/esm"
import AgoraRTM from "agora-rtm"
import {
  CallRtmMessageManager, LogLevel, CallApi, CallStateType,
  CallErrorCodeType, CallStateReason, CallErrorEvent
} from "@/callApi"
import { CallRole, RtcRole } from "@/types"
import { message, Radio } from 'antd';
import type { RadioChangeEvent } from 'antd';

import "./index.css"

interface IUser {
  uid: number
  videoTrack?: ICameraVideoTrack | IRemoteVideoTrack | undefined
  audioTrack?: IMicrophoneAudioTrack | IRemoteAudioTrack | undefined
}

enum Scene {
  None,
  Live,
  One2One
}

const { RTM } = AgoraRTM
AgoraRTM.setParameter('LOG_UPLOAD_INTERVAL', 3 * 1000);


// 1v1 rtc client
let rtc1v1Client: IAgoraRTCClient
// living rtc client
let rtcLivingClient: IAgoraRTCClient
let rtmClient: any
let callApi: CallApi
let tracks: [IMicrophoneAudioTrack, ICameraVideoTrack]


// 秀场转1v1
const Living = () => {
  const [localUserId] = useState(getRandomUid())
  const [remoteUserId, setRemoteUserId] = useState(0)
  const [rtcRole, setRtcRole] = useState(RtcRole.Host)
  const [state, setState] = useState(CallStateType.idle)
  const [firstFrameWaittingDisabled, setFirstFrameWaittingDisabled] = useState(false)
  // 主播用户
  const [hostUser, setHostUser] = useState<IUser>()
  // 当前场景
  const [scene, setScene] = useState<Scene>(Scene.None)

  useEffect(() => {
    // 页面初始化
    mount()

    return () => {
      // 页面卸载
      unmount()
    }
  }, [])

  useEffect(() => {
    addCallApiEventListener()

    return () => {
      removeCallApiEventListener()
    }
  }, [callApi, rtcRole, scene])


  // 秀场rtc频道号
  const livingChannel = useMemo(() => {
    if (rtcRole == RtcRole.Host) {
      return `${localUserId}_live`
    } else {
      return `${remoteUserId}_live`
    }
  }, [rtcRole, localUserId, remoteUserId])


  const mount = async () => {
    const token = await apiGenerateToken(localUserId)
    // rtc init
    rtc1v1Client = createClient({ mode: "live", codec: "vp9", role: "host" })
    // rtm init
    const rtmConfig = DEFAULT_RTM_CONFIG
    rtmConfig.token = token
    rtmClient = new RTM(APPID, localUserId + "", rtmConfig)
    // rtm login 
    try {
      await rtmClient.login()
      message.success("rtm login success")
    } catch (e: any) {
      // catch rtm login error
      message.error(e.message)
    }
    // init callMessageManager
    const callMessageManager = new CallRtmMessageManager({
      appId: APPID,
      userId: localUserId,
      rtmToken: token,
      rtmClient: rtmClient,
    })
    // init callApi
    callApi = new CallApi({
      appId: APPID,
      appCertificate: APPCERTIFICATE,
      userId: localUserId,
      callMessageManager,
      rtcClient: rtc1v1Client,
      logLevel: LogLevel.DEBUG,
    })
    // listen callApi event
    addCallApiEventListener()
    // first prepareForCall
    callApi.prepareForCall({
      roomId: uuidv4(),
      rtcToken: token,
      // must in dom
      localView: "local-view",
      // must in dom
      remoteView: "remote-view",
      callTimeoutMillisecond: CALL_TIMEOUT_MILLISECOND,
      firstFrameWaittingDisabled,
      videoConfig: {
        encoderConfig: DEFAULT_VIDEO_ENCODER_CONFIG,
      },
    })
  }

  const unmount = async () => {
    tracks?.forEach(track => {
      // 关闭设备采集
      track.close()
    })
    // callApi destroy
    await callApi?.destroy()
    // rtm logout
    await rtmClient?.logout()
    // rtc living client leave channel
    await rtcLivingClient?.leave()
  }

  const addCallApiEventListener = () => {
    callApi?.on("callStateChanged", handleCallStateChanged)
    callApi?.on("callError", handleCallError)
  }


  const removeCallApiEventListener = () => {
    callApi?.off("callStateChanged", handleCallStateChanged)
    callApi?.off("callError", handleCallError)
  }

  const handleCallStateChanged = async (state: CallStateType, stateReason: CallStateReason, eventReason?: string, eventInfo?: Record<string, any>) => {
    setState(state)
    switch (state) {
      case CallStateType.prepared:
        if (scene == Scene.One2One) {
          // 退出1v1 回到秀场
          setScene(Scene.Live)
          if (rtcRole == RtcRole.Host) {
            // 主播恢复推流
            if (tracks) {
              await rtcLivingClient.publish(tracks)
              setHostUser({
                uid: localUserId,
                videoTrack: tracks[1],
                audioTrack: tracks[0]
              })
            }
          }
        }
        if (stateReason == CallStateReason.remoteHangup) {
          message.info("对方结束连线")
        } else if (stateReason == CallStateReason.remoteRejected) {
          message.info("对方已拒绝")
        } else if (stateReason == CallStateReason.remoteCallBusy) {
          message.info("用户正忙")
        } else if (stateReason == CallStateReason.callingTimeout) {
          message.info("对方已拒绝")
        }
        break
      case CallStateType.calling:
        setScene(Scene.One2One)
        if (rtcRole == RtcRole.Host) {
          // 主播身份
          // eventInfo.fromUserId => 指向本次通话的主叫方
          // eventInfo.remoteUserId => 指向本次通话的被叫方
          setRemoteUserId(eventInfo!.fromUserId)
          if (tracks) {
            // 主播取消推流
            await rtcLivingClient.unpublish(tracks)
            setHostUser({
              uid: localUserId,
              videoTrack: undefined,
              audioTrack: undefined
            })
          }
          // 主播自动接听
          await callApi.accept(eventInfo!.fromUserId)
        }
    }
  }

  const handleCallError = (errorEvent: CallErrorEvent, errorType: CallErrorCodeType, errorCode: string | number, errorMessage: string) => {
    switch (errorType) {
      case CallErrorCodeType.normal:
        // 常规错误
        break
      case CallErrorCodeType.rtc:
        // rtc错误
        // https://doc.shengwang.cn/doc/rtc/javascript/error-code
        if (errorCode == "PERMISSION_DENIED") {
          message.error("请检查摄像头和麦克风权限")
        }
        break
      case CallErrorCodeType.message:
        // 消息错误
        // https://doc.shengwang.cn/doc/rtm2/javascript/error-codes#%E9%94%99%E8%AF%AF%E7%A0%81%E5%AF%B9%E7%85%A7%E8%A1%A8
        if (errorEvent == CallErrorEvent.sendMessageFail) {
          message.error("消息发送失败")
        }
        break
    }
  }

  const onChangeRemoteUserId = (e: React.ChangeEvent<HTMLInputElement>) => {
    setRemoteUserId(Number(e.target.value))
  }


  const call = async () => {
    if (!checkRemoteUserId()) {
      return
    }
    // prepareForCall update roomId
    callApi.prepareForCall({
      roomId: uuidv4(),
    })
    try {
      await callApi.call(remoteUserId)
    } catch (e: any) {
      message.error(`call failed! ${e.message}`)
    }
  }

  const cancelCall = async () => {
    try {
      await callApi.cancelCall()
    } catch (e: any) {
      message.error(`cancelCall failed! ${e.message}`)
    }
  }

  const accept = async () => {
    if (!checkRemoteUserId()) {
      return
    }
    try {
      await callApi.accept(remoteUserId)
    } catch (e: any) {
      message.error(`accept failed! ${e.message}`)
    }
  }

  const reject = async () => {
    if (!checkRemoteUserId()) {
      return
    }
    try {
      await callApi.reject(remoteUserId)
    } catch (e: any) {
      message.error(`reject failed! ${e.message}`)
    }
  }

  const hangup = async () => {
    if (!checkRemoteUserId()) {
      return
    }
    try {
      await callApi.hangup(remoteUserId)
    } catch (e: any) {
      message.error(`hangup failed! ${e.message}`)
    }

  }

  const checkRemoteUserId = () => {
    if (!remoteUserId) {
      message.error("please input remoteUserId!")
      return false
    }

    return true
  }

  const onChangeRole = (e: RadioChangeEvent) => {
    setRtcRole(e.target.value);
  }

  const onClickLive = async () => {
    if (rtcRole == RtcRole.Audience && !remoteUserId) {
      return message.error("请填写主播Id")
    }
    rtcLivingClient = createClient({ mode: "live", codec: "vp9", role: "host" })
    const rtcToken = await apiGenerateToken(localUserId, livingChannel)
    // 监听秀场频道事件
    listenRtcLivingClientEvent()
    // 先加入秀场频道
    await rtcLivingClient.join(APPID, livingChannel, rtcToken, localUserId)
    // 主播身份
    if (rtcRole == RtcRole.Host) {
      // 创建麦克风和摄像头track
      tracks = await createMicrophoneAndCameraTracks()
      // 创建主播
      const user: IUser = {
        uid: localUserId,
        videoTrack: tracks[1],
        audioTrack: tracks[0]
      }
      setHostUser(user)
      // 主播播放本地视频流
      user.videoTrack?.play("living-player")
      // 发布本地流
      await rtcLivingClient.publish(tracks)
    }
    setScene(Scene.Live)
  }


  const listenRtcLivingClientEvent = () => {
    // 监听远端用户发流
    rtcLivingClient.on("user-published", async (user, mediaType) => {
      if (rtcRole !== RtcRole.Audience || user.uid != remoteUserId) {
        return
      }
      // 订阅
      await rtcLivingClient.subscribe(user, mediaType)
      const hostUser: IUser = {
        uid: Number(user.uid),
        videoTrack: user.videoTrack,
        audioTrack: user.audioTrack
      }
      setHostUser(hostUser)
      if (mediaType === "video") {
        // 播放秀场主播视频
        user.videoTrack?.play("living-player")
      } else {
        // 播放秀场主播音频
        user.audioTrack?.play()
      }
    })
    // 监听远端用户取消发流
    rtcLivingClient.on("user-unpublished", async (user, mediaType) => {
      if (rtcRole !== RtcRole.Audience || user.uid != remoteUserId) {
        return
      }
      // 取消订阅
      await rtcLivingClient.unsubscribe(user, mediaType)
      const hostUser: IUser = {
        uid: Number(user.uid),
        videoTrack: user.videoTrack,
        audioTrack: user.audioTrack
      }
      setHostUser(hostUser)
    })
  }



  const onClickFirstFrameWaittingDisabled = () => {
    setFirstFrameWaittingDisabled(!firstFrameWaittingDisabled)
    callApi.prepareForCall({
      firstFrameWaittingDisabled: !firstFrameWaittingDisabled,
    })
  }



  return <div>
    <div className="item">
      <Radio.Group onChange={onChangeRole} value={rtcRole} disabled={scene !== Scene.None}>
        <Radio value={RtcRole.Host}>主播</Radio>
        <Radio value={RtcRole.Audience}>观众</Radio>
      </Radio.Group>
    </div>
    <div className="item">
      localUserId: {localUserId}
    </div>
    <div className="item">
      <button onClick={onClickFirstFrameWaittingDisabled}>音频首帧与接通相关 {String(!firstFrameWaittingDisabled)}</button>
    </div>
    {rtcRole == RtcRole.Audience ? <div className="item">
      主播Id: <input type="text" value={remoteUserId} onChange={onChangeRemoteUserId} />
    </div> : null}
    {scene == Scene.None ? <div className="item">
      <button onClick={onClickLive}>{rtcRole == RtcRole.Host ? "创建秀场转1v1" : "加入秀场转1v1"}</button>
    </div> : null}
    {scene == Scene.Live ? <div className="item">
      (秀场中) RTC 频道号: {livingChannel}
    </div> : null}
    <div className="item">
      {state == CallStateType.prepared && rtcRole == RtcRole.Audience && scene == Scene.Live ? <button onClick={call}>call 呼叫</button> : null}
      {state == CallStateType.calling && rtcRole == RtcRole.Audience ? <button onClick={cancelCall}>cancelCall 取消呼叫</button> : null}
      {state == CallStateType.calling && rtcRole == RtcRole.Host ? <button onClick={accept}>accept 接受</button> : null}
      {state == CallStateType.calling && rtcRole == RtcRole.Host ? <button onClick={reject}>reject 拒绝</button> : null}
      {state !== CallStateType.prepared && state !== CallStateType.idle ? <button onClick={hangup}>hangup 挂断</button> : null}
    </div>
    {state == CallStateType.connected && scene == Scene.One2One ?
      <div className="item">
        (1v1通话中) RTC 频道号: {callApi?.roomId}
      </div> : null}
    <section className="stream-section">
      {/* 秀场 */}
      <section className={`live ${hostUser?.videoTrack && scene == Scene.Live ? "" : "hidden"}`}>
        <div className="player-wrapper">
          <div className="player-text">uid:({hostUser?.uid}) 主播</div>
          <div id="living-player"></div>
        </div>
      </section>
      {/* 1v1 */}
      <section className={`one-one ${state == CallStateType.connected && scene == Scene.One2One ? "" : "hidden"} `} >
        <div className="player-wrapper">
          <span className="player-text">localUserId:{localUserId}</span>
          <div id="local-view"></div>
        </div>
        <div className="player-wrapper">
          <span className="player-text">remoteUserId:{remoteUserId}</span>
          <div id="remote-view"></div>
        </div>
      </section>
    </section>


  </div >
}


export default Living
