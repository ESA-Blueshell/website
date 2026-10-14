import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import CommitteeList from "@/pages/management/CommitteeList.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({findCommittees: vi.fn(), findCohorts: vi.fn()}))

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
      cohort(1, [{system: "BREVO", label: "Sitecie list", made: true}]),
      cohort(2, [{system: "BREVO", label: "Nintenco list", made: false}]),
    ]})
  })

  afterEach(() => unmountAll(wrappers, "CommitteeListPage"))

  it("lists each committee with its seats and list, and makes one missing its list stand out", async () => {
    const wrapper = await mount()

    const sitecie = wrapper.get('[data-testid="committee-row-1"]')
    expect(sitecie.text()).toContain("2 seats")
    expect(wrapper.get('[data-testid="committee-brevo-1"]').text()).toBe("Sitecie list")
    expect(sitecie.get("a").attributes("to")).toBe("/management/committees/sitecie")
    expect(wrapper.get('[data-testid="committee-row-2"]').text()).toContain("1 seat")
    expect(wrapper.get('[data-testid="committee-brevo-2"]').text()).toBe("No list")
    expect(wrapper.get('[data-testid="committee-list-missing"]').text()).toContain("1 committee is missing a list")
    expect(wrapper.get('[data-testid="committee-list-missing"]').text()).toContain("Nintenco (no list)")
    const archived = wrapper.get('[data-testid="committee-row-3"]')
    expect(archived.text()).toContain("0 seats")
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
    expect(wrapper.get('[data-testid="committee-row-2"]').text()).toContain("Missing: no list")
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

  it("leaves out the Brevo lists where they cannot be read, as for a board member who is no admin, rather than call them missing", async () => {
    api.findCohorts.mockResolvedValue({status: 403, error: {}, data: undefined})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="committee-row-2"]').text()).toContain("1 seat")
    expect(wrapper.find('[data-testid="committee-brevo-2"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="committee-list-missing"]').exists()).toBe(false)
    expect(wrapper.findComponent({name: "FactList"}).text()).not.toContain("Needs a look")
    expect(wrapper.get('[data-testid="committee-row-2"]').text()).not.toContain("Missing")
  })

  it("says when the committees cannot be read, and counts more than one missing", async () => {
    api.findCohorts.mockResolvedValue({status: 200, data: []})
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="committee-list-missing"]').text()).toContain("2 committees are missing a list")

    api.findCommittees.mockRejectedValue(new Error("500"))
    const failed = await mount()
    expect(failed.get('[data-testid="committee-list-unreadable"]').text()).toContain("could not be read")
  })
})
