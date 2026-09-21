import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import EventPicker from "@/components/form/fields/EventPicker.vue"

const {mockFindEvents, mockHandleNetworkError} = vi.hoisted(() => ({
  mockFindEvents: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({findEvents: mockFindEvents}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

const older = {id: 1, title: "Older", startTime: "2026-01-01T18:00:00.000Z"}
const newer = {id: 2, title: "Newer", startTime: "2026-03-01T18:00:00.000Z"}

describe("EventPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindEvents.mockResolvedValue({data: {content: [older, newer]}})
  })

  it("names an event by its title and the day it runs", () => {
    const vm = shallowMount(EventPicker).vm as any

    expect(vm.itemTitle(newer)).toBe(`Newer (${new Date(newer.startTime).toLocaleDateString()})`)
    expect(vm.itemTitle({id: 3, title: "Undated"})).toBe("Undated")
    expect(vm.itemTitle({id: 4})).toBe("Event #4")
    expect(vm.itemTitle(undefined)).toBe("")
  })

  it("puts the most recent event first", async () => {
    const wrapper = shallowMount(EventPicker)
    await new Promise((resolve) => setTimeout(resolve))

    expect((wrapper.vm as any).items.map((e: {id: number}) => e.id)).toEqual([2, 1])
  })

  it("holds an empty list when the api sent no body, and reports a refusal", async () => {
    mockFindEvents.mockResolvedValue({})
    const empty = shallowMount(EventPicker)
    await new Promise((resolve) => setTimeout(resolve))
    expect((empty.vm as any).items).toEqual([])

    mockFindEvents.mockRejectedValue(new Error("500"))
    const refused = shallowMount(EventPicker)
    await new Promise((resolve) => setTimeout(resolve))
    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect((refused.vm as any).loading).toBe(false)
  })
})
