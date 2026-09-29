import type {Component} from "vue"
import type {VueWrapper} from "@vue/test-utils"
import router from "@/plugins/router.ts"
import store, {type StoredLogin} from "@/plugins/store"
import {mountInApp, settle} from "./testUtils"

/**
 * Mounts a page as the app runs it: every child real, the app's own router standing at [path]
 * and its own store, signed in as [login] or signed out. The one thing a spec mocks is the api
 * client, `vi.mock("@/services/api")`, so a test reads what a visitor sees and presses what a
 * visitor presses, and a renamed ref or a split child cannot break it.
 */
export async function mountPage(
  component: Component,
  options: {path?: string, login?: StoredLogin | null, width?: number} = {},
): Promise<VueWrapper<any>> {
  // Vuetify reads the breakpoint off the window and follows its resize events, so a page laid
  // out for a wide screen is asked for by making the window wide rather than by mocking Vuetify.
  window.innerWidth = options.width ?? 1024
  window.dispatchEvent(new Event("resize"))
  store.commit("setLoginState", options.login ?? null)
  await router.push(options.path ?? "/")
  await router.isReady()
  const wrapper = mountInApp(component, {global: {plugins: [router, store]}})
  await settle()
  return wrapper
}

/** A board member's sign-in, which is what the management pages ask for. */
export const boardLogin: StoredLogin = {
  userId: 1,
  username: "board",
  roles: ["BOARD"] as StoredLogin["roles"],
  twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: true, required: false},
}
