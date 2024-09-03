const decoder = new TextDecoder()
const encoder = new TextEncoder()

export const genDivHtmlElement = () => {
  const node = document.createElement("div")
  node.style.width = "100%"
  node.style.height = "100%"
  node.style.position = "relative"

  return node
}

export const LOCAL_VIEW_ELEMENT = genDivHtmlElement()
export const REMOTE_VIEW_ELEMENT = genDivHtmlElement()

function _pad(num: number) {
  return num.toString().padStart(2, "0")
}

export const genHTMLElement = (node: HTMLElement | string): HTMLElement => {
  if (node instanceof HTMLElement) {
    return node
  } else {
    const res = document.querySelector(node)
    if (!res) {
      throw new Error(`can not find element by ${node}`)
    }
    return res as HTMLElement
  }
}

export const decodeUint8Array = (array: Uint8Array) => {
  return decoder.decode(array)
}

export const encodeUint8Array = (str: string) => {
  return encoder.encode(str)
}

export const formatTime = (date?: Date) => {
  if (!date) {
    date = new Date()
  }
  const hours = date.getHours()
  const minutes = date.getMinutes()
  const seconds = date.getSeconds()
  const milliseconds = date.getMilliseconds()

  return `${_pad(hours)}:${_pad(minutes)}:${_pad(seconds)}:${_pad(milliseconds)}`
}

export const isMobile = () =>
  /Mobile|iPhone|iPad|Android|Windows Phone/i.test(navigator.userAgent)

export const serializeHTMLElement = (
  element: any,
): { id: string; className: string } | null => {
  if (!element) {
    return null
  }
  if (!(element instanceof HTMLElement)) {
    throw new Error("Input is not an HTMLElement")
  }
  return {
    id: element.id,
    className: element.className,
  }
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

