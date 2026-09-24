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
    expect(router.currentRoute.value.path).toBe("/account/security")
    expect(router.currentRoute.value.query.setUp).toBe("1")

    await router.push("/login")
    expect(router.currentRoute.value.path).toBe("/login")
  }, 20_000)

  it("lets everybody else go where they like", async () => {
    signInAs(false)

    await router.push("/account/games")
    expect(router.currentRoute.value.path).toBe("/account/games")
  }, 20_000)
})
