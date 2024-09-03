import { ICallInfo } from "../types"

export class CallInfo {
  info: ICallInfo
  private _time: number = 0
  private _started: boolean = false

  constructor() {
    this.info = {
      remoteUserRecvCall: 0,
      acceptCall: 0,
      localUserJoinChannel: 0,
      remoteUserJoinChannel: 0,
      recvFirstFrame: 0,
    }
  }

  getInfo(): ICallInfo {
    return JSON.parse(JSON.stringify(this.info))
  }

  start() {
    if (this._started) {
      return
    }
    this._time = Date.now()
    this._started = true
  }

  add(key: keyof ICallInfo) {
    const time = Date.now()
    this.info = {
      ...this.info,
      [key]: time - this._time,
    }
  }

  end() {
    this.info = {
      remoteUserRecvCall: 0,
      acceptCall: 0,
      localUserJoinChannel: 0,
      remoteUserJoinChannel: 0,
      recvFirstFrame: 0,
    }
    this._time = 0
    this._started = false
  }
}
