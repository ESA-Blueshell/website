import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import JobTriggerDialog from "@/components/common/modals/JobTriggerDialog.vue"
import {settle} from "../../../helpers/testUtils"

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
  {type: "contact.sync-all", payloadFields: []},
]

const openDialog = async () => {
  const wrapper = shallowMount(JobTriggerDialog, {props: {modelValue: false}})
  await wrapper.setProps({modelValue: true})
  await settle()
  return wrapper
}

describe("JobTriggerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockJobTypes.mockResolvedValue({status: 200, data: descriptors})
    mockEnqueue.mockResolvedValue({status: 200, data: {id: 5, status: "QUEUED"}})
  })

  it("loads job types from the API when opened, sorted", async () => {
    const wrapper = await openDialog()

    expect(mockJobTypes).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).typeOptions).toEqual([
      {title: "Contact Sync", value: "contact.sync"},
      {title: "Contact Sync All", value: "contact.sync-all"},
    ])
  })

  it("enqueues the selected job with a type-coerced payload and emits enqueued", async () => {
    const wrapper = await openDialog()

    ;(wrapper.vm as any).selectedType = "contact.sync"
    await settle()
    ;(wrapper.vm as any).fieldValues = {userId: "7"}

    await (wrapper.vm as any).submit()

    expect(mockEnqueue).toHaveBeenCalledWith({
      body: {jobType: "contact.sync", payload: {userId: 7}},
    })
    expect(wrapper.emitted("enqueued")).toBeTruthy()
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false])
  })

  it("blocks submit until required fields are provided", async () => {
    const wrapper = await openDialog()

    ;(wrapper.vm as any).selectedType = "contact.sync"
    await settle()

    expect((wrapper.vm as any).requiredMissing).toBe(true)
    await (wrapper.vm as any).submit()
    expect(mockEnqueue).not.toHaveBeenCalled()
  })

  it("surfaces an error when the enqueue fails", async () => {
    mockEnqueue.mockResolvedValue({status: 400})
    const wrapper = await openDialog()

    ;(wrapper.vm as any).selectedType = "contact.sync-all"
    await settle()
    await (wrapper.vm as any).submit()

    expect((wrapper.vm as any).errorMessage).toBeTruthy()
    expect(wrapper.emitted("enqueued")).toBeFalsy()
  })
})
