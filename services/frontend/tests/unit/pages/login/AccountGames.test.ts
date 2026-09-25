import {describe, expect, it, vi} from "vitest"
import AccountGames from "@/pages/login/AccountGames.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore} = vi.hoisted(() => ({
  mockStore: {getters: {getLogin: {userId: 42}}},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: ["heading", "crumb", "islandContent", "tabs", "eyebrow", "body"], template: "<div><slot /><slot name=\"actions\" /></div>"},
}))

describe("Games page", () => {
  it("shows the signed-in person's game handles", async () => {
    const wrapper = mountInApp(AccountGames, {global: {stubs: {GameHandles: true}}})

    await settle()

    expect(wrapper.getComponent({name: "AccountFrame"}).props("heading")).toBe("Games")
    expect(wrapper.getComponent({name: "GameHandles"}).props("userId")).toBe(42)
  })
})
