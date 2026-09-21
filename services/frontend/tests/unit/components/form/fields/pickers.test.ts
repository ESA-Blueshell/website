import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import CohortPicker from "@/components/form/fields/CohortPicker.vue"
import ContributionPeriodPicker from "@/components/form/fields/ContributionPeriodPicker.vue"
import EventPicker from "@/components/form/fields/EventPicker.vue"
import NationalitySelect from "@/components/form/fields/NationalitySelect.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"

const {mockCohorts, mockEvents, mockPeriods, mockUsers, mockNetworkError} = vi.hoisted(() => ({
  mockCohorts: vi.fn(),
  mockEvents: vi.fn(),
  mockPeriods: vi.fn(),
  mockUsers: vi.fn(),
  mockNetworkError: vi.fn(),
}))

vi.mock("@/domains/cohorts", () => ({fetchCohortOptions: mockCohorts}))
vi.mock("@/services/api", () => ({findEvents: mockEvents, findContributionPeriods: mockPeriods, findUsers: mockUsers}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError}))

const stubs = {IslandField: {template: "<div><slot /></div>"}}

const picker = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent({name: "IslandPicker"})
const rows = (wrapper: ReturnType<typeof mount>) =>
  picker(wrapper).props("options") as Array<{key: string; label: string; note?: string; terms?: string[]}>

describe("CohortPicker", () => {
  beforeEach(() => mockCohorts.mockReset())

  it("says what a cohort is, and what it can be found by", async () => {
    mockCohorts.mockResolvedValue([{id: 4, label: "Members", system: "BREVO", kind: "LIST", memberCount: 12}])
    const wrapper = mount(CohortPicker, {props: {modelValue: 4}, global: {stubs}})
    await flushPromises()

    expect(rows(wrapper)).toEqual([
      {key: "4", label: "Members", note: "BREVO LIST (12)", terms: ["BREVO", "LIST"]},
    ])
    expect(picker(wrapper).props("selectedKey")).toBe("4")
    expect(picker(wrapper).props("loading")).toBe(false)
  })

  it("reports a number, not the text of a key", async () => {
    mockCohorts.mockResolvedValue([])
    const wrapper = mount(CohortPicker, {props: {modelValue: undefined}, global: {stubs}})
    await flushPromises()

    picker(wrapper).vm.$emit("pick", "9")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe(9)
    expect(picker(wrapper).props("selectedKey")).toBeNull()
  })


  it("shows what is wrong with it under the field", () => {
    mockCohorts.mockResolvedValue([])
    const wrapper = mount(CohortPicker, {props: {errorMessages: ["Pick one."]}})

    expect(wrapper.find(".island-field__said").text()).toBe("Pick one.")
  })
})

describe("EventPicker", () => {
  beforeEach(() => mockEvents.mockReset())

  it("puts the newest event first, and finds one by where and when", async () => {
    mockEvents.mockResolvedValue({data: {content: [
      {id: 1, title: "LAN", startTime: "2026-01-02T20:00:00Z", location: "Zilverling"},
      {id: 2, title: "Drink", startTime: "2026-03-04T20:00:00Z", location: "Abscint"},
    ]}})
    const wrapper = mount(EventPicker, {props: {modelValue: 2}, global: {stubs}})
    await flushPromises()

    const said = rows(wrapper)
    expect(said[0]?.label).toBe("Drink")
    expect(said[0]?.terms).toContain("Abscint")
    expect(said[1]?.label).toBe("LAN")
  })

  it("names an event that has neither title nor day", async () => {
    mockEvents.mockResolvedValue({data: {content: [{id: 7}]}})
    const wrapper = mount(EventPicker, {props: {}, global: {stubs}})
    await flushPromises()

    expect(rows(wrapper)[0]).toEqual({key: "7", label: "Event #7", note: "", terms: []})
  })


  it("reports the event that was picked", async () => {
    mockEvents.mockResolvedValue({data: {}})
    const wrapper = mount(EventPicker, {props: {errorMessages: "Say which."}, global: {stubs}})
    await flushPromises()

    picker(wrapper).vm.$emit("pick", "3")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe(3)
  })
})

describe("ContributionPeriodPicker", () => {
  beforeEach(() => mockPeriods.mockReset())

  it("says a period as the years it runs over", async () => {
    mockPeriods.mockResolvedValue({data: [
      {id: 1, startDate: "2024-09-01", endDate: "2025-08-31"},
      {id: 2, startDate: "2025-09-01", endDate: "2026-08-31"},
    ]})
    const wrapper = mount(ContributionPeriodPicker, {props: {modelValue: 1}, global: {stubs}})
    await flushPromises()

    expect(rows(wrapper).map(one => one.label)).toEqual(["2025–2026", "2024–2025"])
    expect(rows(wrapper)[0]?.terms).toEqual(["2025-09-01", "2026-08-31"])
  })

  it("says a period with only a start, and one with neither end of it", async () => {
    mockPeriods.mockResolvedValue({data: [{id: 3, startDate: "2027-09-01"}, {id: 4}]})
    const wrapper = mount(ContributionPeriodPicker, {props: {}, global: {stubs}})
    await flushPromises()

    expect(rows(wrapper).map(one => one.label)).toEqual(["2027", "Period #4"])
  })

})

describe("UserPicker", () => {
  beforeEach(() => mockUsers.mockReset())

  it("fetches everybody once the list comes down, and not before", async () => {
    mockUsers.mockResolvedValue({data: {content: [
      {id: 2, fullName: "Bea", email: "bea@example.com"},
      {id: 1, fullName: "Ada", email: "ada@example.com"},
    ]}})
    const wrapper = mount(UserPicker, {props: {modelValue: 1}, global: {stubs}})

    expect(mockUsers).not.toHaveBeenCalled()

    picker(wrapper).vm.$emit("opened")
    await flushPromises()

    expect(rows(wrapper).map(one => one.label)).toEqual(["Ada", "Bea"])

    picker(wrapper).vm.$emit("opened")
    await flushPromises()

    expect(mockUsers).toHaveBeenCalledTimes(1)
  })

  it("names somebody with no name at all by their address, then by their id", async () => {
    mockUsers.mockResolvedValue({data: {content: [{id: 5, email: "five@example.com"}, {id: 6}]}})
    const wrapper = mount(UserPicker, {props: {}, global: {stubs}})

    picker(wrapper).vm.$emit("opened")
    await flushPromises()

    expect(rows(wrapper).map(one => one.label)).toEqual(["five@example.com", "User #6"])
  })

})

describe("NationalitySelect", () => {
  it("keeps a cca2 code as it is", () => {
    const wrapper = mount(NationalitySelect, {props: {modelValue: "nl"}, global: {stubs}})

    expect(wrapper.findComponent({name: "IslandControl"}).props("modelValue")).toBe("NL")
  })

  it("matches a name an older record holds to the country it names", () => {
    const wrapper = mount(NationalitySelect, {props: {modelValue: "Dutch"}, global: {stubs}})

    expect(wrapper.findComponent({name: "IslandControl"}).props("modelValue")).toBe("NL")
  })

  it("holds nothing where the record holds nothing, or nothing it knows", () => {
    for (const said of ["", "   ", undefined, "Atlantis"]) {
      const wrapper = mount(NationalitySelect, {props: {modelValue: said}, global: {stubs}})
      expect(wrapper.findComponent({name: "IslandControl"}).props("modelValue")).toBeNull()
    }
  })

  it("reports the code that was chosen", () => {
    const wrapper = mount(NationalitySelect, {props: {modelValue: null}, global: {stubs}})

    wrapper.findComponent({name: "IslandControl"}).vm.$emit("update:modelValue", "DE")

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("DE")
  })
})
