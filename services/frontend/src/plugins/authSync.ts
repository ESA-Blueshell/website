import {deleteCookie, readJsonCookie} from "@/plugins/cookies"
import type {LoginResponse} from "@/services/api"
import {sanitizeLoginPayload, type StoredLogin, type TypedStore} from "@/plugins/store"

const AUTH_PING_KEY = "auth:ping"
const AUTH_CHANNEL_NAME = "auth"

let authChannel: BroadcastChannel | null = null

function readLoginCookie(): StoredLogin | null {
  const raw = readJsonCookie<LoginResponse>("login") || null
  if (!raw) return null

  if ((raw.token ?? "").length > 0) {
    deleteCookie("login")
    return null
  }

  // Through the same reduction the store writes, so a cookie left by an older version reads as
  // what it would be written as today. Without that, every reconcile would find a difference that
  // is only the fields being dropped, and clear the in-memory token on each one.
  return sanitizeLoginPayload(raw)
}

function serializeLogin(login: StoredLogin | null): string {
  return JSON.stringify(login)
}

export function reconcileAuthFromCookie(store: TypedStore) {
  const cookieLogin = readLoginCookie()
  const currentLogin = store.getters.getLogin
  if (serializeLogin(cookieLogin) === serializeLogin(currentLogin)) return
  store.commit("setLoginState", cookieLogin)
}

export function emitAuthChanged() {
  if (typeof window === "undefined") return

  if (typeof BroadcastChannel !== "undefined") {
    authChannel ??= new BroadcastChannel(AUTH_CHANNEL_NAME)
    authChannel.postMessage({type: "auth:changed", at: Date.now()})
  }

  localStorage.setItem(AUTH_PING_KEY, String(Date.now()))
}

export function setupAuthSync(store: TypedStore) {
  if (typeof window === "undefined") return

  reconcileAuthFromCookie(store)

  window.addEventListener("storage", (event: StorageEvent) => {
    if (event.key !== AUTH_PING_KEY) return
    reconcileAuthFromCookie(store)
  })

  window.addEventListener("focus", () => {
    reconcileAuthFromCookie(store)
  })

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState !== "visible") return
    reconcileAuthFromCookie(store)
  })

  if (typeof BroadcastChannel !== "undefined") {
    authChannel ??= new BroadcastChannel(AUTH_CHANNEL_NAME)
    authChannel.addEventListener("message", () => {
      reconcileAuthFromCookie(store)
    })
  }
}
