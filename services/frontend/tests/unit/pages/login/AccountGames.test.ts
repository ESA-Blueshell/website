import {describe, expect, it, vi} from "vitest"
import AccountGames from "@/pages/login/AccountGames.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore, mockUser} = vi.hoisted(() => ({
  mockStore: {getters: {getLogin: {userId: 42} as {userId: number} | null}},
  mockUser: {readMemberProfile: vi.fn()},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/domains/user", () => mockUser)

vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: ["heading", "islandContent"], template: "<div><slot /></div>"},
}))

const mountPage = async () => {
  const wrapper = mountInApp(AccountGames, {global: {stubs: {GameHandles: true}}})
  await settle()
  return wrapper
}

describe("Games page", () => {
  it("shows the signed-in person's game handles, and whether their name shows beside them", async () => {
    mockUser.readMemberProfile.mockResolvedValue({nameOnRosters: true})
    const wrapper = await mountPage()

    expect(wrapper.getComponent({name: "AccountFrame"}).props("heading")).toBe("Games")
    expect(wrapper.getComponent({name: "GameHandles"}).props()).toMatchObject({userId: 42, nameShown: true})
    expect(mockUser.readMemberProfile).toHaveBeenCalledWith(42)
  })

  it("says nothing of the name for somebody with no member profile, and reads nothing when signed out", async () => {
    mockUser.readMemberProfile.mockResolvedValue(null)
    expect((await mountPage()).getComponent({name: "GameHandles"}).props("nameShown")).toBeNull()

    mockStore.getters.getLogin = null
    mockUser.readMemberProfile.mockClear()
    const signedOut = await mountPage()
    mockStore.getters.getLogin = {userId: 42}

    expect(signedOut.findComponent({name: "GameHandles"}).exists()).toBe(false)
    expect(mockUser.readMemberProfile).not.toHaveBeenCalled()
  })
})
