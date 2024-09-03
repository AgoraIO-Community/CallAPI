import { RTMConfig } from "agora-rtm"

export const APPID = import.meta.env.VITE_AGORA_APP_ID
export const APPCERTIFICATE = import.meta.env.VITE_AGORA_APP_CERTIFICATE
export const DEFAULT_VIDEO_ENCODER_CONFIG = "720p_2"
export const CALL_TIMEOUT_MILLISECOND = 15 * 1000 // ms
export const DEFAULT_RTM_CONFIG: RTMConfig = {
  logLevel: "error",
  logUpload: true,
  presenceTimeout: 30,
}


export const getRandomUid = () => {
  return Math.floor(1000 + Math.random() * 9000);
}


export const apiGenerateToken = async (
  uid: string | number,
  channelName: string = "",
) => {
  const url = "https://toolbox.bj2.agoralab.co/v2/token/generate"
  const data = {
    appId: APPID,
    appCertificate: APPCERTIFICATE,
    channelName,
    expire: 7200,
    src: "ios",
    types: [1, 2],
    uid: uid + "",
  }
  let resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  }) as unknown as any
  resp = (await resp.json()) || {}
  return resp?.data?.token || null
}


export const uuidv4 = (): string => {
  if (crypto && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0
    const v = c === "x" ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export const isMobile = () =>
  /Mobile|iPhone|iPad|Android|Windows Phone/i.test(navigator.userAgent)
