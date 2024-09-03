import { LogLevel } from "../types"
import { logger as rtcLogger } from "@agora-js/report"

// tip: 由于 rtc sdk log 一版是设置 setLogLevel(1) 为 info 级别，
// 所以这里的 logger  debug 级别 对应 rtc sdk 的 info 级别

export interface LoggerConfig {
  level?: LogLevel
  prefix?: string
}

class Logger {
  level: LogLevel = LogLevel.ERROR
  prefix?: string = ""
  preTime?: number = 0

  constructor(config: LoggerConfig) {
    const { level, prefix } = config
    if (level !== undefined) {
      this.level = level
    }
    if (prefix) {
      this.prefix = prefix
    }
  }

  setLogLevel(level: LogLevel) {
    this.level = level
  }

  debug(...args: any[]) {
    if (this.level <= LogLevel.DEBUG) {
      this._log(...args);
    }
  }

  warn(...args: any[]) {
    if (this.level <= LogLevel.WARN) {
      this._warn(...args);
    }
  }

  error(...args: any[]) {
    if (this.level <= LogLevel.ERROR) {
      this._err(...args);
    }
  }

  time(...args: any[]) {
    const time = new Date().getTime()
    let cost = 0
    let start = ""
    if (this.preTime) {
      cost = time - this.preTime
    } else {
      start = "start"
    }
    this.preTime = time
    if (this.level <= LogLevel.DEBUG) {
      this._log(
        `${this._genPrefix()}[time]:  -------------- cost:${cost}ms ${start} -----------------   \n`,
        ...args,
      );
    }
  }

  timeEnd(...args: any[]) {
    const time = new Date().getTime()
    let cost = 0
    if (this.preTime) {
      cost = time - this.preTime
    }
    this.preTime = 0
    if (this.level <= LogLevel.DEBUG) {
      this._log(
        `${this._genPrefix()}[time]:  -------------- cost:${cost}ms end -----------------   \n`,
        ...args,
      );
    }
  }

  //  ---------------------------- private ----------------------------

  private _log(...args: any[]) {
    rtcLogger.info(this._genPrefix(), ...args)
  }

  private _warn(...args: any[]) {
    rtcLogger.warn(this._genPrefix(), ...args)
  }

  private _err(...args: any[]) {
    rtcLogger.error(this._genPrefix(), ...args)
  }

  private _genPrefix() {
    return this.prefix ? ` [${this.prefix}]` : ""
  }
}

export const logger = new Logger({
  level: LogLevel.ERROR,
  prefix: "CallApi",
})
