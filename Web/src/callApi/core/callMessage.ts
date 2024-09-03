import { logger } from "../common"
import { ICallMessage } from "../types"

export class CallMessage {
  callId: string = ""

  constructor() {}

  setCallId(callId: string) {
    this.callId = callId
  }

  getCallId() {
    return this.callId
  }

  encode(message: Partial<ICallMessage>): string {
    if (!this.callId) {
      const msg = "callId is not set"
      logger.error(msg)
      throw new Error(msg)
    }
    const finMessage = {
      ...message,
      callId: this.callId,
      message_version: "1.0",
      message_timestamp: new Date().getTime(),
    }
    return JSON.stringify(finMessage)
  }

  decode(message: string) {
    return JSON.parse(message) as ICallMessage
  }
}
