import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordRoles from "@/pages/management/DiscordRoles.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findTargetOverview: vi.fn(), listCataloguedChannels: vi.fn()}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => ({path: "/management/platforms/discord/roles"}),
}))
vi.mock("@/services/api", async (importOriginal) => ({...(await importOriginal<typeof import("@/services/api")>()), ...api}))

const sitecie = {
  externalId: "900", label: "Sitecie", targetId: 5, cohortId: 7, cohortLabel: "Sitecie", cohortType: "COMMITTEE_MEMBERS",
  missing: 2, extra: 0, lastReconciledAt: "2026-10-01T03:00:00.000Z", enforced: false,
}
const loose = {externalId: "901", label: "Artists", enforced: false}

describe("the Discord roles page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordRoles)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findTargetOverview.mockResolvedValue({status: 200, data: {lists: [sitecie, loose], missing: [], lastReconciledAt: null}})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: [{id: "1", name: "sitecie", kind: "TEXT", category: "Committees", private: true, roleIds: ["900"]}]})
  })

  afterEach(() => unmountAll(wrappers, "DiscordRolesPage"))

  it("lists each role with the cohort it follows, what it opens and its drift, by every head", async () => {
    const wrapper = await mount()

    expect(api.findTargetOverview).toHaveBeenCalledWith({path: {system: "DISCORD"}})
    const linked = wrapper.get('[data-testid="discord-role-900"]').text()
    expect(linked).toContain("@Sitecie")
    expect(linked).toContain("Committee members · Sitecie")
    expect(linked).toContain("#sitecie")
    expect(linked).toContain("2 missing")
    const unlinked = wrapper.get('[data-testid="discord-role-901"]').text()
    expect(unlinked).toContain("Nothing")
    expect(unlinked).toContain("Never")
    expect(await sortByEveryHead(wrapper)).toBe(5)
  })

  it("says when the roles could not be read, and when there are none", async () => {
    api.findTargetOverview.mockResolvedValueOnce({status: 500, error: {}})
    expect((await mount()).find('[data-testid="discord-roles-unread"]').exists()).toBe(true)

    api.findTargetOverview.mockResolvedValueOnce({status: 200, data: {lists: [], missing: [], lastReconciledAt: null}})
    api.listCataloguedChannels.mockResolvedValueOnce({status: 503, error: {}})
    expect((await mount()).find('[data-testid="discord-roles-none"]').exists()).toBe(true)
  })
})
