import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import BulkDialogScaffold from "@/components/common/modals/bulk/BulkDialogScaffold.vue"
import type {BulkRow} from "@/utils/bulkRow"

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRouter: () => ({push: vi.fn()}),
}))

const stubs = {RouterLink: {props: ["to"], template: '<a :to="to"><slot /></a>'}}

const rows: BulkRow[] = [
  {userId: 1, name: "Cas", disposition: "SKIPPED", memberSince: "2024-09-01"},
  {userId: 2, name: "Bo", disposition: "WARNING", memberSince: "2023-02-01"},
  {userId: 3, name: "Ada", disposition: "INCLUDED"},
  {userId: 4, name: "Dre", disposition: "EXCLUDED", memberSince: "2025-01-10"},
]
const counts = {selected: 4, willApply: 1, skipped: 1, excluded: 1, warned: 1}

const scaffold = (overrides: Record<number, boolean> = {}) => mount(BulkDialogScaffold, {
  props: {confirmLabel: "End memberships", rows, counts, includedCount: 1, reincludeOverrides: overrides},
  global: {stubs},
  attachTo: document.body,
})

describe("what a bulk task shows before it changes anything", () => {
  afterEach(() => vi.unstubAllGlobals())

  it("counts who is warned and excluded, sorts by any column, and lets a warned member be included anyway", async () => {
    const wrapper = scaffold()
    const order = () => wrapper.findAll("tr[data-row]").map((row) => row.attributes("data-testid"))
    const sort = (label: string) => wrapper.findAll("th button").find((one) => one.text().includes(label))!.trigger("click")

    expect(wrapper.get('[data-testid="bulk-action-counts"]').text()).toContain("1 with warnings")
    expect(wrapper.get('[data-testid="bulk-action-counts"]').text()).toContain("1 excluded")
    expect(order()).toEqual([1, 2, 3, 4].map((id) => `bulk-preview-row-${id}`))

    await sort("What happens")
    expect(order()).toEqual([3, 2, 4, 1].map((id) => `bulk-preview-row-${id}`))
    await sort("What happens")
    expect(order()).toEqual([1, 4, 2, 3].map((id) => `bulk-preview-row-${id}`))
    await sort("Member since")
    expect(order()[0]).toBe("bulk-preview-row-3")

    expect(wrapper.find('[data-testid="bulk-preview-reinclude-1"]').exists()).toBe(false)
    await wrapper.get('[data-testid="bulk-preview-reinclude-2"]').trigger("change")
    expect(wrapper.emitted("update:reincludeOverrides")?.[0]?.[0]).toEqual({2: true})

    await wrapper.get('[data-testid="bulk-action-cancel-btn"]').trigger("click")
    expect(wrapper.emitted("cancel")).toHaveLength(1)
    wrapper.unmount()
  })

  it("draws each member as a row on a phone, with the same tick for a warned member", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = scaffold({2: true})

    expect(wrapper.get('[data-testid="bulk-preview-row-2"]').text()).toContain("Bo")
    expect(wrapper.get('[data-testid="bulk-preview-disposition-2"]').text()).toBe("Included anyway")
    expect(wrapper.get('[data-testid="bulk-preview-disposition-3"]').text()).toBe("Included")
    expect(wrapper.find('[data-testid="bulk-preview-reinclude-3"]').exists()).toBe(false)
    await wrapper.get('[data-testid="bulk-preview-reinclude-2"]').trigger("change")
    expect(wrapper.emitted("update:reincludeOverrides")?.[0]?.[0]).toEqual({2: false})
    wrapper.unmount()
  })
})
