const decoder = new TextDecoder()
const encoder = new TextEncoder()

function _pad(num: number) {
  return num.toString().padStart(2, "0")
}


// -------------------- dom utils --------------------

export const isInDOM = (element: string | HTMLElement): boolean => {
  let finElement: HTMLElement
  if (typeof element == "string") {
    finElement = document.getElementById(element)!
  } else {
    finElement = element
  }
  return document.body.contains(finElement);
}


export const setElementVisibility = (element: string | HTMLElement, visible: boolean) => {
  let finElement: HTMLElement
  if (typeof element == "string") {
    finElement = document.getElementById(element)!
    if (!finElement) {
      return
    }
  } else {
    finElement = element
  }
  finElement.style.visibility = visible ? "visible" : "hidden"
}


export const serializeHTMLElement = (
  element: string | HTMLElement | undefined,
): { id: string; className: string, tagName: string } | null => {
  if (!element) {
    return null
  }
  let finElement: HTMLElement | null
  if (typeof element == "string") {
    finElement = document.getElementById(element)
    if (!finElement) {
      return null
    }
  } else {
    finElement = element
  }
  return {
    id: finElement?.id,
    className: finElement?.className,
    tagName: finElement?.tagName,
  }
}

export const clearHTMLElement = (element: string | HTMLElement) => {
  let finElement: HTMLElement
  if (typeof element == "string") {
    finElement = document.getElementById(element)!
    if (!finElement) {
      return
    }
  } else {
    finElement = element
  }
  finElement.innerHTML = ""
}


// -------------------- dom utils --------------------


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
