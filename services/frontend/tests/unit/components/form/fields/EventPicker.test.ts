import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import EventPicker from "@/components/form/fields/EventPicker.vue"

const {mockFindEvents, mockHandleNetworkError} = vi.hoisted(() => ({
  mockFindEvents: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({findEvents: mockFindEvents}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

const older = {id: 1, title: "Older", startTime: "2026-01-01T18:00:00.000Z"}
const newer = {id: 2, title: "Newer", startTime: "2026-03-01T18:00:00.000Z"}
const stubs = {FormField: {template: "<div><slot /></div>"}}
const picker = (wrapper: ReturnType<typeof mount>) => wrapper.findComponent({name: "SearchPicker"})
const rows = (wrapper: ReturnType<typeof mount>) =>
  picker(wrapper).props("options") as Array<{key: string; label: string; note?: string}>

describe("EventPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindEvents.mockResolvedValue({data: {content: [older, newer]}})
  })

  it("names an event by its title, with the day it runs beside it", async () => {
    const wrapper = mount(EventPicker, {global: {stubs}})
    await flushPromises()

    expect(rows(wrapper)[0]).toMatchObject({
      key: "2",
      label: "Newer",
      note: new Date(newer.startTime).toLocaleDateString(),
    })
  })

  it("falls back to the number where an event has no title, and says no day where it has none",
    async () => {
      mockFindEvents.mockResolvedValue({data: {content: [{id: 4}]}})
      const wrapper = mount(EventPicker, {global: {stubs}})
      await flushPromises()

      expect(rows(wrapper)[0]).toMatchObject({key: "4", label: "Event #4", note: ""})
    })

  it("puts the most recent event first", async () => {
    const wrapper = mount(EventPicker, {global: {stubs}})
    await flushPromises()

    expect(rows(wrapper).map(one => one.key)).toEqual(["2", "1"])
  })

  it("holds an empty list when the api sent no body, and reports a refusal", async () => {
    mockFindEvents.mockResolvedValue({})
    const empty = mount(EventPicker, {global: {stubs}})
    await flushPromises()
    expect(rows(empty)).toEqual([])

    mockFindEvents.mockRejectedValue(new Error("500"))
    const refused = mount(EventPicker, {global: {stubs}})
    await flushPromises()
    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(picker(refused).props("loading")).toBe(false)
  })
})
