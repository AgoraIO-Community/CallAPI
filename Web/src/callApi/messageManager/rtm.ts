import { RTMClient } from "agora-rtm"
import { CallMessageManager } from "./base"
import { decodeUint8Array, encodeUint8Array } from "../common"

/** RTM消息管理器配置 */
export interface ICallRtmMessageManagerConfig {
  appId: string
  userId: number
  rtmToken: string
  rtmClient: RTMClient
}

/** RTM消息管理器 */
export class CallRtmMessageManager extends CallMessageManager {
  config: ICallRtmMessageManagerConfig

  get rtmClient(): RTMClient {
    return this.config.rtmClient
  }

  constructor(config: ICallRtmMessageManagerConfig) {
    super()
    this.config = config
    this.init()
  }

  async sendMessage(userId: string | number, message: string) {
    const msg = encodeUint8Array(message)
    // console.log("[test] sendMessage", userId, message, msg)
    await this.rtmClient?.publish(userId.toString(), msg, {
      channelType: "USER",
    })
  }

  private init() {
    this.rtmClient.addEventListener("message", (event) => {
      const { channelType, channelName, publisher, message } = event
      if (channelType == "USER") {
        this.emit("messageReceive", decodeUint8Array(message as Uint8Array))
      }
    })
    // this.rtmClient.addEventListener("presence", (event) => {
    //   console.log("[test] presence", event)
    // })
  }
}
