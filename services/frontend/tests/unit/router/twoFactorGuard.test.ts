import {afterEach, describe, expect, it} from "vitest"
import router from "@/plugins/router"
import store from "@/plugins/store"
import {Role} from "@/services/api"

const signInAs = (required: boolean) =>
  store.commit("setLoginState", {
    userId: 3,
    username: "alice",
    roles: [Role.MEMBER],
    twoFactor: {on: !required, required, offered: false, backupCodesLeft: 0},
  })

describe("the two-factor guard", () => {
  afterEach(() => store.commit("setLoginState", null))

  it("keeps somebody whose granted role waits for two-factor on the set-up, whatever they open", async () => {
    signInAs(true)

    await router.push("/account/games")
    expect(router.currentRoute.value.path).toBe("/account/set-up-two-factor")
    expect(router.currentRoute.value.query.redirect).toBe("/account/games")

    await router.push("/login")
    expect(router.currentRoute.value.path).toBe("/login")
  }, 20_000)

  it("lets everybody else go where they like", async () => {
    signInAs(false)

    await router.push("/account/games")
    expect(router.currentRoute.value.path).toBe("/account/games")
  }, 20_000)

  it("sends everybody else to the set-up that asks for the password", async () => {
    signInAs(false)

    await router.push("/account/set-up-two-factor?redirect=/events")
    expect(router.currentRoute.value.path).toBe("/account/security/two-factor/set-up")
    expect(router.currentRoute.value.query.redirect).toBe("/events")
  }, 20_000)
})
