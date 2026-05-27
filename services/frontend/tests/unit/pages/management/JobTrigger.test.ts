import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import JobTrigger from "@/pages/management/JobTrigger.vue"
import {settle} from "../helpers"

const {mockJobTypes, mockEnqueue, mockHandleNetworkError} = vi.hoisted(() => ({
  mockJobTypes: vi.fn(),
  mockEnqueue: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    jobTypes: mockJobTypes,
    enqueue: mockEnqueue,
  }
})

vi.mock("@/plugins/handleNetworkError", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

const descriptors = [
  {type: "contact.sync", payloadFields: [{name: "userId", type: "Long", required: true}]},
  {type: "contact.dispatch-syncs", payloadFields: []},
]

describe("JobTrigger page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockJobTypes.mockResolvedValue({status: 200, data: descriptors})
    mockEnqueue.mockResolvedValue({status: 200, data: {id: 5, status: "QUEUED"}})
  })

  const mountTrigger = () => shallowMount(JobTrigger, {global: {stubs: {transition: false}}})

  it("loads job types from the API on mount", async () => {
    const wrapper = mountTrigger()
    await settle()

    expect(mockJobTypes).toHaveBeenCalled()
    expect((wrapper.vm as any).typeOptions).toEqual([
      {title: "Contact Sync", value: "contact.sync"},
      {title: "Contact Dispatch Syncs", value: "contact.dispatch-syncs"},
    ])
  })

  it("enqueues the selected job with a type-coerced payload", async () => {
    const wrapper = mountTrigger()
    await settle()

    ;(wrapper.vm as any).selectedType = "contact.sync"
    await settle()
    ;(wrapper.vm as any).fieldValues = {userId: "7"}

    await (wrapper.vm as any).submit()

    expect(mockEnqueue).toHaveBeenCalledWith({
      body: {jobType: "contact.sync", payload: {userId: 7}},
    })
    expect((wrapper.vm as any).resultMessage).toContain("#5")
  })

  it("omits blank fields from the payload", async () => {
    const wrapper = mountTrigger()
    await settle()

    ;(wrapper.vm as any).selectedType = "contact.dispatch-syncs"
    await settle()

    await (wrapper.vm as any).submit()

    expect(mockEnqueue).toHaveBeenCalledWith({
      body: {jobType: "contact.dispatch-syncs", payload: {}},
    })
  })
})
