import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import DiscordRole from "@/pages/management/DiscordRole.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findListedTarget: vi.fn(),
  findCohortById: vi.fn(),
  listCataloguedChannels: vi.fn(),
  reconcileTarget: vi.fn(),
  pushDrift: vi.fn(),
  removeDrift: vi.fn(),
}))
const {mockStore, route} = vi.hoisted(() => ({
  mockStore: {commit: vi.fn(), getters: {isAdmin: true} as Record<string, unknown>},
  route: {params: {externalId: "900"}},
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => route,
}))
vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("@/services/api", async (importOriginal) => ({...(await importOriginal<typeof import("@/services/api")>()), ...api}))

const role = (fields: Record<string, unknown> = {}) => ({
  externalId: "900", label: "Sitecie", targetId: 50, cohortId: 7, cohortLabel: "Sitecie", cohortType: "COMMITTEE_MEMBERS",
  missing: 2, extra: 1, lastReconciledAt: "2026-10-01T03:00:00Z", enforced: false, ...fields,
})
const ledger = (targetMemberId: number, state: string, fields: Record<string, unknown> = {}) => ({
  targetMemberId, state, isUserDeleted: false, joinedAt: "2025-09-22T10:00:00Z", system: "DISCORD", unreachable: false, ...fields,
})
const cohort = {
  id: 7, label: "Sitecie", category: "COMMITTEES", type: "COMMITTEE_MEMBERS", orphaned: false,
  mappings: [{targetId: 50, system: "DISCORD", kind: "ROLE", externalId: "900", label: "Sitecie", path: [], folderKnown: true,
    enforced: false, runs: [{startedAt: "2026-10-01T03:00:00Z", inSync: 4, oursOnly: 1, theirsOnly: 1, unreachable: 1}]}],
  members: [
    ledger(1, "DESIRED", {userId: 11, userFullName: "Sanne Jansen"}),
    ledger(2, "DESIRED", {userId: 12, userFullName: "Eva de Boer", unreachable: true}),
    ledger(3, "STRANGER", {externalUserId: "d3", externalLabel: "kim"}),
    ledger(4, "VERIFIED", {userId: 14, userFullName: "In Sync", externalUserId: "d4"}),
    ledger(5, "STRANGER", {userId: 15, userFullName: "Left Cie", externalUserId: "d5"}),
  ],
  resolutions: [],
}

describe("one Discord role", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(DiscordRole)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    route.params.externalId = "900"
    api.findListedTarget.mockResolvedValue({status: 200, data: role()})
    api.findCohortById.mockResolvedValue({status: 200, data: cohort})
    api.listCataloguedChannels.mockResolvedValue({status: 200, data: [
      {id: "1", name: "sitecie", kind: "TEXT", category: "Committees", private: true, roleIds: ["900"], roleNames: {}},
      {id: "2", name: "Committees", kind: "CATEGORY", category: null, private: true, roleIds: ["900"], roleNames: {}},
      {id: "3", name: "general", kind: "TEXT", category: null, private: false, roleIds: [], roleNames: {}},
    ]})
    api.reconcileTarget.mockResolvedValue({status: 202})
  })

  afterEach(() => unmountAll(wrappers, "DiscordRolePage"))

  it("shows the cohort it follows, its people and who has no Discord linked, and the channels it can access", async () => {
    const wrapper = await mount()

    expect(api.findListedTarget).toHaveBeenCalledWith({path: {system: "DISCORD", externalId: "900"}})
    expect(wrapper.text()).toContain("Everyone in Sitecie, committee members, holds this role.")
    expect(wrapper.get('[data-testid="discord-role-people"]').text()).toContain("1 in sync")
    expect(wrapper.get('[data-testid="discord-role-unreachable"]').text()).toContain("1")
    expect(wrapper.get('[data-testid="discord-role-channels"]').text()).toContain("sitecie")
    expect(wrapper.get('[data-testid="discord-role-channels"]').text()).not.toContain("general")
  })

  it("offers to add or remove, never to link or take in, and nothing for somebody with no Discord linked", async () => {
    const wrapper = await mount()

    const unlinked = wrapper.get('[data-testid="discord-role-row-2"]').text()
    expect(unlinked).toContain("Has not linked a Discord account")
    expect(unlinked).not.toContain("Add to the role")
    expect(wrapper.get('[data-testid="discord-role-row-1"]').text()).toContain("Add to the role")
    const stranger = wrapper.get('[data-testid="discord-role-row-3"]').text()
    expect(stranger).toContain("Remove from the role")
    expect(stranger).not.toContain("Link to an account")
    expect(wrapper.get('[data-testid="discord-role-row-5"]').text()).toContain("Holds the role, and not in Sitecie")
  })

  it("queues a comparison with Discord", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="discord-role-reconcile"]').trigger("click")
    await settle()

    expect(api.reconcileTarget).toHaveBeenCalled()
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "A comparison is queued.")
  })

  it("says when the role is gone, could not be read, follows nothing or its channels could not be read", async () => {
    api.findListedTarget.mockResolvedValueOnce({status: 404, error: {}, response: {status: 404}})
    expect((await mount()).get('[data-testid="discord-role-unread"]').text()).toContain("Discord has no such role")
    api.findListedTarget.mockResolvedValueOnce({status: 500, error: {}, response: {status: 500}})
    expect((await mount()).get('[data-testid="discord-role-unread"]').text()).toContain("could not be read")

    api.findListedTarget.mockResolvedValueOnce({status: 200, data: role({cohortId: null, targetId: null})})
    api.listCataloguedChannels.mockResolvedValueOnce({status: 500, error: {}})
    const loose = await mount()
    expect(loose.text()).toContain("not linked to a cohort")
    expect(loose.find('[data-testid="discord-role-channels-unread"]').exists()).toBe(true)

    api.findListedTarget.mockResolvedValueOnce({status: 200, data: role()})
    api.findCohortById.mockResolvedValueOnce({status: 500, error: {}})
    api.listCataloguedChannels.mockResolvedValueOnce({status: 200, data: []})
    const unread = await mount()
    expect(unread.text()).toContain("could not be read. Try again later.")
    expect(unread.find('[data-testid="discord-role-channels-none"]').exists()).toBe(true)
  })
})
