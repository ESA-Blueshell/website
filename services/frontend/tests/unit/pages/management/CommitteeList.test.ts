import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import CommitteeList from "@/pages/management/CommitteeList.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findCommittees: vi.fn(), findCohorts: vi.fn(), setCommitteeDiscord: vi.fn(), findTargetOverview: vi.fn(), createMissingTargets: vi.fn()}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const committee = (id: number, name: string, fields: Record<string, unknown> = {}) => ({
  id, name, slug: name.toLowerCase(), archived: false, description: "", gameCodes: [], createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z", version: 0, members: [], ...fields,
})
const cohort = (committeeId: number, targets: unknown[]) => ({
  id: 100 + committeeId, type: "COMMITTEE_MEMBERS", category: "COMMITTEES", label: "x", memberCount: 0, mappingCount: targets.length,
  definitionKey: `COMMITTEE_MEMBERS:${committeeId}`, targets,
})

describe("the committees in Management", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(CommitteeList)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.findCommittees.mockResolvedValue({status: 200, data: [
      committee(1, "Sitecie", {members: [{id: 1}, {id: 2}]}),
      committee(2, "Nintenco", {members: [{id: 3}]}),
      committee(3, "Oldcie", {archived: true}),
    ]})
    api.findCohorts.mockResolvedValue({status: 200, data: [
      cohort(1, [{system: "DISCORD", label: "Sitecie", made: true, externalId: "900"}, {system: "BREVO", label: "Sitecie list", made: true, externalId: "31"}]),
      cohort(2, [{system: "BREVO", label: "Nintenco list", made: false}]),
    ]})
  })

  afterEach(() => unmountAll(wrappers, "CommitteeListPage"))

  it("lists each committee with its members, its role and list as links, and makes one missing them stand out", async () => {
    const wrapper = await mount()

    const sitecie = wrapper.get('[data-testid="committee-row-1"]')
    expect(sitecie.text()).toContain("2 members")
    expect(wrapper.get('[data-testid="committee-discord-1"]').text()).toBe("@Sitecie")
    expect(wrapper.get('[data-testid="committee-brevo-1"]').text()).toBe("Sitecie list")
    expect(wrapper.get('[data-testid="committee-discord-1"]').attributes("to")).toBe("/management/platforms/discord/roles/900")
    expect(wrapper.get('[data-testid="committee-brevo-1"]').attributes("to")).toBe("/management/platforms/brevo/lists/31")
    expect(wrapper.get("thead").text()).toContain("Members")
    expect(sitecie.get("a").attributes("to")).toBe("/management/committees/sitecie")
    expect(wrapper.get('[data-testid="committee-row-2"]').text()).toContain("1 member")
    expect(wrapper.get('[data-testid="committee-discord-2"]').text()).toBe("No role")
    expect(wrapper.get('[data-testid="committee-brevo-2"]').text()).toBe("No list")
    expect(wrapper.get('[data-testid="committee-list-missing"]').text()).toContain("1 committee is missing a role or a list")
    expect(wrapper.get('[data-testid="committee-list-missing"]').text()).toContain("Nintenco (no role, no list)")
    const archived = wrapper.get('[data-testid="committee-row-3"]')
    expect(archived.text()).toContain("0 members")
    expect(archived.text()).toContain("Archived")
    expect(wrapper.findAll('[data-testid^="committee-row-"]').at(-1)!.attributes("data-testid")).toBe("committee-row-3")
    expect(wrapper.findComponent({name: "FactList"}).text()).toContain("1 archived")
    expect(wrapper.findComponent({name: "FactList"}).text()).toContain("Needs a look1")
  })

  it("draws each committee as a row on a phone, saying what one is missing", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    expect(wrapper.get('[data-testid="committee-row-1"]').text()).toContain("Active")
    expect(wrapper.get('[data-testid="committee-row-2"]').text()).toContain("Missing: no role, no list")
    expect(wrapper.get('[data-testid="committee-row-3"]').text()).toContain("Archived")
  })

  it("narrows the list by search, and says when nothing matches", async () => {
    const wrapper = await mount()

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "ninten")
    await settle()
    expect(wrapper.find('[data-testid="committee-row-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="committee-row-2"]').exists()).toBe(true)

    wrapper.findComponent({name: "SearchBox"}).vm.$emit("update:modelValue", "nothing")
    await settle()
    expect(wrapper.get('[data-testid="committee-list-empty"]').text()).toBe("No committee matches.")
  })

  it("says when the committees cannot be read, and counts more than one missing", async () => {
    api.findCohorts.mockResolvedValue({status: 200, data: []})
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="committee-list-missing"]').text()).toContain("2 committees are missing a role or a list")

    api.findCommittees.mockRejectedValue(new Error("500"))
    const failed = await mount()
    expect(failed.get('[data-testid="committee-list-unreadable"]').text()).toContain("could not be read")
  })

  it("gives the ticked committees a role and channel or a list together, leaving out the ones that have one or are archived", async () => {
    api.setCommitteeDiscord.mockResolvedValue({status: 200, data: {available: true}})
    api.findTargetOverview.mockResolvedValue({status: 200, data: {lists: [], missing: [{targetId: 55, cohortId: 102, cohortLabel: "Nintenco", cohortType: "COMMITTEE_MEMBERS", memberCount: 1, creating: false}]}})
    api.createMissingTargets.mockResolvedValue({status: 200, data: {queued: 1}})
    const wrapper = await mount()
    const bulk = () => wrapper.findComponent({name: "BulkAdd"})

    // The head's own offer to take every row, shown or not, and to let go again.
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("selectAll")
    await settle()
    expect(wrapper.get('[data-testid="committee-list-selection"]').exists()).toBe(true)
    wrapper.findComponent({name: "ManagementTable"}).vm.$emit("clearSelection")
    await settle()
    expect(wrapper.get('[data-testid="committee-list-selection"]').text()).toContain("Tick rows to use these")
    for (const id of [1, 2, 3]) await wrapper.get(`[data-testid="committee-check-${id}"]`).setValue(true)
    await wrapper.get('[data-testid="committee-add-roles"]').trigger("click")
    await settle()

    expect(bulk().props("open")).toBe(true)
    expect(bulk().props("items").map((one: {name: string; note: string}) => [one.name, one.note])).toEqual([["Nintenco", "@Nintenco and #nintenco"]])
    expect(bulk().props("skipped")).toEqual([{name: "Sitecie", why: "Has a role already"}, {name: "Oldcie", why: "Archived"}])
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: true})
    expect(api.setCommitteeDiscord).toHaveBeenCalledWith({path: {id: 2}, body: {createRole: true, channelIds: [], createChannel: "nintenco"}})
    api.setCommitteeDiscord.mockResolvedValue({status: 503, error: {message: "Discord is away."}})
    expect((await bulk().props("run")(bulk().props("items")[0])).ok).toBe(false)
    bulk().vm.$emit("update:open", false)
    await settle()
    expect(bulk().props("open")).toBe(false)

    await wrapper.get('[data-testid="committee-add-lists"]').trigger("click")
    await settle()
    expect(bulk().props("title")).toBe("Add Brevo lists")
    expect(bulk().props("skipped")).toEqual([{name: "Sitecie", why: "Has a list already"}, {name: "Oldcie", why: "Archived"}])
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: true})
    expect(api.createMissingTargets).toHaveBeenCalledWith({path: {system: "BREVO"}, body: {targetIds: [55]}})

    // A committee the site expects no list for cannot be given one from here.
    api.findTargetOverview.mockResolvedValue({status: 200, data: {lists: [], missing: []}})
    await wrapper.get('[data-testid="committee-add-lists"]').trigger("click")
    await settle()
    expect(await bulk().props("run")(bulk().props("items")[0])).toEqual({ok: false, reason: "The site expects no list for this committee yet."})

    api.findCohorts.mockClear()
    bulk().vm.$emit("done")
    await settle()
    expect(api.findCohorts).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="committee-list-selection"]').text()).toContain("Tick rows to use these")
  })
})
