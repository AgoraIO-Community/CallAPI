import { useEffect, useState, useMemo } from "react"
import {
  getRandomUid, apiGenerateToken, isMobile,
  uuidv4, DEFAULT_RTM_CONFIG, APPID, APPCERTIFICATE,
  CALL_TIMEOUT_MILLISECOND, DEFAULT_VIDEO_ENCODER_CONFIG,
} from "./utils"
import { createClient, IAgoraRTCClient } from "agora-rtc-sdk-ng/esm"
import AgoraRTM from "agora-rtm"
import {
  CallRtmMessageManager, LogLevel, CallApi, CallStateType,
  CallErrorCodeType, CallStateReason, CallErrorEvent
} from "./callApi"
import { message } from 'antd';
import VConsole from "vconsole"

if (isMobile()) {
  const vConsole = new VConsole()
}

const { RTM } = AgoraRTM
let rtcClient: IAgoraRTCClient
let rtmClient
let callApi: CallApi


export enum Role {
  // 主叫
  Caller = "caller",
  // 被叫
  Called = "called",
}

function App() {
  const [localUserId, setLocalUserId] = useState(getRandomUid())
  const [remoteUserId, setRemoteUserId] = useState(0)
  const [firstFrameWaittingDisabled, setFirstFrameWaittingDisabled] = useState(false)
  const [state, setState] = useState(CallStateType.idle)
  const [eventInfo, setEventInfo] = useState<any>({})
  // eventInfo.fromUserId => 指向本次通话的主叫方
  // eventInfo.remoteUserId => 指向本次通话的被叫方

  useEffect(() => { init() }, [])

  // 角色
  const role = useMemo(() => {
    return eventInfo?.remoteUserId == localUserId ? Role.Called : Role.Caller
  }, [eventInfo, localUserId])

  useEffect(() => {
    if (role == Role.Called) {
      // 被叫自动设置remoteUserId
      if (eventInfo.fromUserId) {
        setRemoteUserId(eventInfo.fromUserId)
      }
    }
  }, [role, eventInfo])

  const init = async () => {
    const token = await apiGenerateToken(localUserId)
    // rtc init
    rtcClient = createClient({ mode: "live", codec: "vp9", role: "host" })
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
      rtcClient: rtcClient,
      logLevel: LogLevel.DEBUG,
    })
    // listen callApi event
    addCallApiEventListener()
    // first prepareForCall
    callApi.prepareForCall({
      roomId: uuidv4(),
      rtcToken: token,
      // must in dom
      localView: document.getElementById("local-view")!,
      // must in dom
      remoteView: document.getElementById("remote-view")!,
      autoAccept: false,
      callTimeoutMillisecond: CALL_TIMEOUT_MILLISECOND,
      firstFrameWaittingDisabled,
      videoConfig: {
        encoderConfig: DEFAULT_VIDEO_ENCODER_CONFIG,
      },
    })
  }

  const addCallApiEventListener = () => {
    callApi.on("callInfoChanged", (info) => {
      // show call info if needed
    })
    callApi.on(
      "callStateChanged",
      (state, stateReason, eventReason, eventInfo) => {
        setState(state)
        switch (state) {
          case CallStateType.prepared:
            setEventInfo(eventInfo)
            if (stateReason == CallStateReason.remoteHangup) {
              message.info("对方结束连线")
            } else if (stateReason == CallStateReason.remoteRejected) {
              message.info("对方已拒绝")
            } else if (stateReason == CallStateReason.remoteCallBusy) {
              message.info("对方已拒绝")
            } else if (stateReason == CallStateReason.callingTimeout) {
              // call timeout
            }
            break
          case CallStateType.calling:
            setEventInfo(eventInfo)
        }
      },
    )

    callApi.on("callError", (errorEvent, errorType, errorCode, errMessage) => {
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
    })
  }

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setRemoteUserId(Number(e.target.value))
  }

  const onClickFirstFrameWaittingDisabled = () => {
    setFirstFrameWaittingDisabled(!firstFrameWaittingDisabled)
    callApi.prepareForCall({
      firstFrameWaittingDisabled: !firstFrameWaittingDisabled,
    })
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

  return <div>
    <div className="item">localUserId: {localUserId}</div>
    <div className="item">
      remoteUserId: <input type="text" value={remoteUserId} onChange={onChange} />
    </div>
    <div className="item">
      <button onClick={onClickFirstFrameWaittingDisabled}>音频首帧与接通相关 {String(!firstFrameWaittingDisabled)}</button>
    </div>
    <div className="item">
      {state == CallStateType.prepared ? <button onClick={call}>call 呼叫</button> : null}
      {state == CallStateType.calling && role == Role.Caller ? <button onClick={cancelCall}>cancelCall 取消呼叫</button> : null}
      {state == CallStateType.calling && role == Role.Called ? <button onClick={accept}>accept 接受</button> : null}
      {state == CallStateType.calling && role == Role.Called ? <button onClick={reject}>reject 拒绝</button> : null}
      {state !== CallStateType.prepared && state !== CallStateType.idle ? <button onClick={hangup}>hangup 挂断</button> : null}
    </div>
    {state == CallStateType.connected ?
      <div className="item">
        RTC 频道号: {callApi.roomId}
      </div> : null}
    <div className={`stream-section ${state !== CallStateType.connected ? "hidden" : ""} `} >
      <div className="local">
        <span className="text">localUserId:{localUserId}</span>
        <div id="local-view"></div>
      </div>
      <div className="remote">
        <span className="text">remoteUserId:{remoteUserId}</span>
        <div id="remote-view"></div>
      </div>
    </div>
  </div>
}

export default App
