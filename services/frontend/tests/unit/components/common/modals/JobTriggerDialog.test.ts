import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import JobTriggerDialog from "@/components/common/modals/JobTriggerDialog.vue"
import {settle} from "../../../helpers/testUtils"

const {mockListJobTypes, mockEnqueueJob, mockHandleNetworkError} = vi.hoisted(() => ({
  mockListJobTypes: vi.fn(),
  mockEnqueueJob: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/jobs", () => ({
  listJobTypes: mockListJobTypes,
  enqueueJob: mockEnqueueJob,
}))

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
    mockListJobTypes.mockResolvedValue(descriptors)
    mockEnqueueJob.mockResolvedValue({ok: true})
  })

  it("loads job types from the API when opened, sorted", async () => {
    const wrapper = await openDialog()

    expect(mockListJobTypes).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).typeOptions).toEqual([
      {title: "Sync contact", value: "contact.sync"},
      {title: "Sync all contacts", value: "contact.sync-all"},
    ])
  })

  it("enqueues the selected job with a type-coerced payload and emits enqueued", async () => {
    const wrapper = await openDialog()

    ;(wrapper.vm as any).selectedType = "contact.sync"
    await settle()
    ;(wrapper.vm as any).fieldValues = {userId: "7"}

    await (wrapper.vm as any).submit()

    expect(mockEnqueueJob).toHaveBeenCalledWith("contact.sync", {userId: 7})
    expect(wrapper.emitted("enqueued")).toBeTruthy()
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false])
  })

  it("blocks submit until required fields are provided", async () => {
    const wrapper = await openDialog()

    ;(wrapper.vm as any).selectedType = "contact.sync"
    await settle()

    expect((wrapper.vm as any).requiredMissing).toBe(true)
    await (wrapper.vm as any).submit()
    expect(mockEnqueueJob).not.toHaveBeenCalled()
  })

  it("surfaces an error when the enqueue fails", async () => {
    mockEnqueueJob.mockResolvedValue({ok: false, reason: "That job could not be triggered."})
    const wrapper = await openDialog()

    ;(wrapper.vm as any).selectedType = "contact.sync-all"
    await settle()
    await (wrapper.vm as any).submit()

    expect((wrapper.vm as any).errorMessage).toBe("That job could not be triggered.")
    expect(wrapper.emitted("enqueued")).toBeFalsy()
  })
})
