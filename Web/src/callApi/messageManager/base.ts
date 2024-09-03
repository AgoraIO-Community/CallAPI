import { AGEventEmitter } from "../common"
import { CallMessageManagerEvents } from "../types"

/**
 * 消息管理器
 */
export abstract class CallMessageManager extends AGEventEmitter<CallMessageManagerEvents> {

  /**
   * 发送消息
   * @param userId 用户ID
   * @param message 消息
   */
  abstract sendMessage(userId: string | number, message: string): Promise<void>
}
