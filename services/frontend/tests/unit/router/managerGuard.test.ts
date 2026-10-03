import {afterEach, describe, expect, it} from "vitest"
import router from "@/plugins/router"
import store from "@/plugins/store"
import {Role} from "@/services/api"

const signInAs = (roles: Role[]) =>
  store.commit("setLoginState", {
    userId: 3,
    username: "alice",
    roles,
    twoFactor: {on: true, required: false, offered: false, backupCodesLeft: 0},
  })

const MANAGERS = ["/user-manager", "/recovery/manage"]

describe("the user, address and recovery managers", () => {
  afterEach(() => store.commit("setLoginState", null))

  it("send a signed-in member without the board role home, as every refused management page does", async () => {
    signInAs([Role.MEMBER])

    for (const manager of MANAGERS) {
      await router.push(manager)
      expect(router.currentRoute.value.path, manager).toBe("/")
    }
  }, 20_000)

  it("open for the board and for an admin", async () => {
    signInAs([Role.MEMBER, Role.BOARD])
    await router.push("/recovery/manage")
    expect(router.currentRoute.value.path).toBe("/recovery/manage")

    await router.push("/")
    signInAs([Role.ADMIN])
    await router.push("/recovery/manage")
    expect(router.currentRoute.value.path).toBe("/recovery/manage")
  }, 20_000)
})
