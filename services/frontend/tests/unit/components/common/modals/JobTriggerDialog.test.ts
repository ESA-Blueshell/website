import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import {humanizeJobType} from "@/utils/jobCatalog"
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
  {type: "demo.flag", payloadFields: [{name: "restart", type: "Boolean", kind: "PRIMITIVE", required: true}]},
]

const openDialog = async (renderStubDefaultSlot = false) => {
  const wrapper = shallowMount(JobTriggerDialog, {props: {modelValue: false}, global: {renderStubDefaultSlot}})
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
      {title: humanizeJobType("demo.flag"), value: "demo.flag"},
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

  it("shows a yes/no field as a switch that sends false until it is turned on", async () => {
    const wrapper = await openDialog(true)

    ;(wrapper.vm as any).selectedType = "demo.flag"
    await settle()

    const toggle = wrapper.findComponent({name: "VSwitch"})
    expect(toggle.attributes("data-testid")).toBe("job-trigger-field-restart")
    expect((wrapper.vm as any).requiredMissing).toBe(false)

    await (wrapper.vm as any).submit()
    expect(mockEnqueueJob).toHaveBeenLastCalledWith("demo.flag", {restart: false})
  })

  it("sends true for a yes/no field once its switch is on", async () => {
    const wrapper = await openDialog(true)

    ;(wrapper.vm as any).selectedType = "demo.flag"
    await settle()
    wrapper.findComponent({name: "VSwitch"}).vm.$emit("update:modelValue", true)
    await settle()

    await (wrapper.vm as any).submit()
    expect(mockEnqueueJob).toHaveBeenLastCalledWith("demo.flag", {restart: true})
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
