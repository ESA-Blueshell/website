import {beforeEach, describe, expect, it, vi} from "vitest"
import AccountGames from "@/pages/login/AccountGames.vue"
import {mountInApp, settle} from "../helpers"

const {mockStore, mockUser} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {getLogin: {userId: 42} as {userId: number} | null}},
  mockUser: {readUser: vi.fn(), saveNameOnRosters: vi.fn()},
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
  const wrapper = mountInApp(AccountGames, {global: {stubs: {GameHandles: true, PlayedRosters: true}}})
  await settle()
  return wrapper
}
type Page = Awaited<ReturnType<typeof mountPage>>
const toggle = async (wrapper: Page) => {
  await wrapper.get("[data-testid=games-name-toggle-btn]").trigger("click")
  await settle()
}

describe("Games page", () => {
  beforeEach(() => {
    mockStore.getters.getLogin = {userId: 42}
    mockUser.readUser.mockResolvedValue({id: 42, fullName: "Alice Doe", nameOnRosters: false})
  })

  it("shows the handles and the rosters of the signed-in person", async () => {
    const wrapper = await mountPage()

    expect(wrapper.getComponent({name: "AccountFrame"}).props("heading")).toBe("Esports Teams")
    expect(wrapper.getComponent({name: "GameHandles"}).props("userId")).toBe(42)
    expect(wrapper.getComponent({name: "PlayedRosters"}).props("userId")).toBe(42)
  })

  it("lets anybody show their name beside their handle, and hide it again", async () => {
    mockUser.saveNameOnRosters.mockResolvedValueOnce(true).mockResolvedValueOnce(false)
    const wrapper = await mountPage()
    expect(wrapper.get("[data-testid=games-name]").text()).toContain("Your name is hidden on the esports pages")

    await toggle(wrapper)
    expect(mockUser.saveNameOnRosters).toHaveBeenLastCalledWith(42, true)
    expect(wrapper.get("[data-testid=games-name]").text()).toContain("Your name, Alice Doe, shows beside your handle")
    expect(wrapper.get("[data-testid=games-name-toggle-btn]").text()).toBe("Hide my name")
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Your name now shows beside your handle.")

    await toggle(wrapper)
    expect(mockUser.saveNameOnRosters).toHaveBeenLastCalledWith(42, false)
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Only your handle shows now.")
  })

  it("says so when the choice could not be saved", async () => {
    mockUser.saveNameOnRosters.mockResolvedValue(null)
    const wrapper = await mountPage()

    await toggle(wrapper)

    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "That could not be saved. Try again.")
    expect(wrapper.get("[data-testid=games-name-toggle-btn]").text()).toBe("Show my name")
  })

  it("reads nothing when signed out", async () => {
    mockStore.getters.getLogin = null
    const wrapper = await mountPage()

    expect(wrapper.findComponent({name: "GameHandles"}).exists()).toBe(false)
    expect(wrapper.find("[data-testid=games-name]").exists()).toBe(false)
    expect(mockUser.readUser).not.toHaveBeenCalled()
  })
})
