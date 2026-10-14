import {beforeEach, describe, expect, it, vi} from "vitest"
import Help from "@/pages/login/Help.vue"
import {mountInApp} from "../helpers"

const {mockStore} = vi.hoisted(() => ({
  mockStore: {getters: {isMember: false, isActive: false, isBoard: false, isAdmin: false}},
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: {heading: String, body: String, islandContent: Boolean}, template: "<div><slot /></div>"},
}))

vi.mock("@/domains/discord", () => ({discordChannel: (door: string) => `https://discord.test/${door}`}))

const signedInAs = (roles: Partial<typeof mockStore.getters>) => {
  Object.assign(mockStore.getters, {isMember: false, isActive: false, isBoard: false, isAdmin: false}, roles)
  return mountInApp(Help)
}
/* v-show writes the style; the page is not attached to a document, so nothing is "visible" to ask about. */
const shut = (text: {attributes: (name: string) => string | undefined}) => (text.attributes("style") ?? "").includes("display: none")
const topicIds = (wrapper: ReturnType<typeof signedInAs>) =>
  wrapper.findAll("[data-testid^=help-topic-]").map(topic => topic.attributes("data-testid")!.replace("help-topic-", ""))

describe("the Help page", () => {
  beforeEach(() => vi.clearAllMocks())

  it("is an account page of its own, drawn in the island", () => {
    const frame = signedInAs({}).getComponent({name: "AccountFrame"})

    expect(frame.props("heading")).toBe("Help")
    expect(frame.props("body")).toBeUndefined()
    expect(frame.props("islandContent")).toBe(true)
  })

  it("shows somebody with no role only what they can do, and a way to every section", () => {
    const wrapper = signedInAs({})

    expect(topicIds(wrapper)).toContain("events-sign-up")
    expect(topicIds(wrapper)).not.toContain("events-create")
    expect(topicIds(wrapper)).not.toContain("recovery-board-manage")
    expect(wrapper.find("[data-testid=help-section-people]").exists()).toBe(false)
    expect(wrapper.get("[data-testid=help-jump-events]").attributes("href")).toBe("#help-events")
    expect(wrapper.get("[data-testid=help-section-events]").attributes("id")).toBe("help-events")
  })

  it("adds a committee member's, the board's and an admin's topics, each marked with who it is for", () => {
    const committee = signedInAs({isMember: true, isActive: true})
    const board = signedInAs({isMember: true, isActive: true, isBoard: true})
    const admin = signedInAs({isMember: true, isActive: true, isBoard: true, isAdmin: true})

    expect(topicIds(committee)).toContain("events-create")
    expect(topicIds(committee)).not.toContain("events-approve")
    expect(committee.get("[data-testid=help-topic-events-create] h3").text()).toContain("Committees")
    expect(board.get("[data-testid=help-topic-recovery-board-manage] h3").text()).toContain("Board")
    expect(board.get("[data-testid=help-topic-contributions-paid] h3").text()).toContain("Treasurer")
    expect(topicIds(board)).not.toContain("site-roles")
    expect(admin.get("[data-testid=help-topic-site-roles] h3").text()).toContain("Admins")
    expect(committee.get("[data-testid=help-topic-events-sign-up] h3").text()).toBe("Sign up for an event")
  })

  it("reads an admin as the board too, and the board as a committee member and a member", () => {
    const admin = signedInAs({isAdmin: true})

    expect(topicIds(admin)).toEqual(expect.arrayContaining(["site-roles", "events-approve", "contributions-paid", "events-create", "contributions-pay"]))
  })

  it("keeps a topic shut until its row is pressed, and shuts it again", async () => {
    const wrapper = signedInAs({})
    const row = wrapper.get("[data-testid=help-open-security-two-factor]")
    const text = wrapper.get("#help-security-two-factor-text")

    expect(row.attributes("aria-expanded")).toBe("false")
    expect(shut(text)).toBe(true)

    await row.trigger("click")
    expect(row.attributes("aria-expanded")).toBe("true")
    expect(shut(text)).toBe(false)
    expect(text.findAll("ol li")).toHaveLength(5)
    expect(text.get("a").attributes("to")).toBe("/account/security/two-factor")

    await row.trigger("click")
    expect(shut(text)).toBe(true)
  })

  it("shuts an answer a search opened when its row is pressed, until the next search", async () => {
    const wrapper = signedInAs({})
    const search = wrapper.get("[data-testid=help-search] input")
    await search.setValue("backup codes")
    const row = wrapper.get("[data-testid=help-open-security-backup-codes]")

    await row.trigger("click")
    expect(row.attributes("aria-expanded")).toBe("false")
    expect(shut(wrapper.get("#help-security-backup-codes-text"))).toBe(true)

    await search.setValue("backup")
    expect(wrapper.get("[data-testid=help-open-security-backup-codes]").attributes("aria-expanded")).toBe("true")

    await search.setValue("")
    expect(wrapper.get("[data-testid=help-open-security-backup-codes]").attributes("aria-expanded")).toBe("false")
  })

  it("sets a role's name apart where a paragraph opens with it", () => {
    const roles = signedInAs({}).get("[data-testid=help-topic-roles-what]")

    expect(roles.findAll("strong").map(lead => lead.text())).toEqual(["Account.", "Member.", "Active member.", "Board.", "Treasurer.", "Admin."])
  })

  it("searches what the reader may see, counts the results, lists them beside the text and opens each", async () => {
    const wrapper = signedInAs({})

    await wrapper.get("[data-testid=help-search] input").setValue("backup codes")

    expect(topicIds(wrapper)).toEqual(["security-two-factor", "security-backup-codes", "recovery-lost-phone"])
    expect(wrapper.get("[data-testid=help-count]").text()).toBe("3 results")
    expect(wrapper.findAll("[data-testid^=help-jump-]").map(jump => jump.text())).toEqual([
      "Signing in and security", "Turn on two-factor authentication", "Get new backup codes or move to a new phone",
      "Account recovery", "Get back in if you lost your phone",
    ])
    expect(wrapper.get("[data-testid=help-jump-recovery-lost-phone]").attributes("href")).toBe("#help-recovery-lost-phone")
    expect(shut(wrapper.get("#help-recovery-lost-phone-text"))).toBe(false)

    await wrapper.get("[data-testid=help-search] input").setValue("trigger job")
    expect(topicIds(wrapper)).toEqual([])
  })

  it("lists only the sections, and counts nothing, while nothing narrows the page", () => {
    const wrapper = signedInAs({})

    expect(wrapper.find("[data-testid=help-count]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=help-jump-security-two-factor]").exists()).toBe(false)
    expect(wrapper.get("[data-testid=help-jump-security]").text()).toBe("Signing in and security")
  })

  it("offers a role to narrow to only where the reader has more than one, and only their own", async () => {
    const plain = signedInAs({})
    const board = signedInAs({isMember: true, isActive: true, isBoard: true})
    const picker = board.getComponent({name: "SearchPicker"})

    expect(plain.findComponent({name: "SearchPicker"}).exists()).toBe(false)
    expect(picker.props("options").map((option: {label: string}) => option.label)).toEqual(["All", "Everyone", "Members", "Committees", "Board", "Treasurer"])
    expect(picker.props("selectedKey")).toBe("all")

    picker.vm.$emit("pick", "treasurer")
    await board.vm.$nextTick()
    expect(topicIds(board)).toEqual(["contributions-periods", "contributions-paid", "contributions-emails"])
    expect(board.get("[data-testid=help-count]").text()).toBe("3 results")

    picker.vm.$emit("pick", "all")
    await board.vm.$nextTick()
    expect(topicIds(board)).toContain("events-sign-up")
    expect(board.find("[data-testid=help-count]").exists()).toBe(false)
  })

  it("says so when nothing matches, and always offers the board and the sitecie", async () => {
    const wrapper = signedInAs({})

    await wrapper.get("[data-testid=help-search] input").setValue("zzz")

    expect(wrapper.get("[data-testid=help-nothing]").text()).toBe("Nothing found. Try another word, or ask the board.")
    expect(wrapper.get("[data-testid=help-count]").text()).toBe("0 results")
    expect(wrapper.get("[data-testid=help-ask]").findAll("a").map(way => way.attributes("href"))).toEqual([
      "mailto:board@blueshell.utwente.nl", "https://discord.test/board", "https://discord.test/suggestions",
    ])
  })
})
