import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import JobRunForm from "@/components/management/JobRunForm.vue"
import {settle} from "../../helpers/testUtils"

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
  {
    type: "cohort.reconcile",
    payloadFields: [
      {name: "targetId", type: "Long", required: true},
      {name: "limit", type: "Int", required: false},
      {name: "dryRun", type: "Boolean", required: false},
      {name: "intent", type: "Intent", required: false, kind: "ENUM", enumValues: ["ADD", "REMOVE"]},
    ],
  },
]

const mountForm = async (preset: {type: string; payload: Record<string, unknown>} | null = null) => {
  const wrapper = shallowMount(JobRunForm, {props: {preset}})
  await settle()
  return wrapper
}

describe("JobRunForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListJobTypes.mockResolvedValue(descriptors)
    mockEnqueueJob.mockResolvedValue({ok: true})
  })

  it("loads the job types once it is drawn, sorted", async () => {
    const wrapper = await mountForm()

    expect(mockListJobTypes).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).typeOptions.map((option: {key: string}) => option.key))
      .toEqual(["cohort.reconcile", "contact.sync", "contact.sync-all"])
  })

  it("queues the chosen job with a type-coerced payload and says it is queued", async () => {
    const wrapper = await mountForm()

    ;(wrapper.vm as any).selectedType = "contact.sync"
    await settle()
    ;(wrapper.vm as any).fieldValues = {userId: "7"}

    await (wrapper.vm as any).submit()
    await settle()

    expect(mockEnqueueJob).toHaveBeenCalledWith("contact.sync", {userId: 7})
    expect(wrapper.emitted("queued")?.[0]).toEqual(["contact.sync"])
    expect(wrapper.find('[data-testid="job-run-queued"]').text()).toBe("Sync contact is queued.")
  })

  it("holds Queue the job until the required fields are given", async () => {
    const wrapper = await mountForm()

    ;(wrapper.vm as any).selectedType = "contact.sync"
    await settle()

    expect((wrapper.vm as any).requiredMissing).toBe(true)
    await (wrapper.vm as any).submit()
    expect(mockEnqueueJob).not.toHaveBeenCalled()
  })

  it("says why the api refused the job", async () => {
    mockEnqueueJob.mockResolvedValue({ok: false, reason: "That job could not be triggered."})
    const wrapper = await mountForm()

    ;(wrapper.vm as any).selectedType = "contact.sync-all"
    await settle()
    await (wrapper.vm as any).submit()

    expect((wrapper.vm as any).errorMessage).toBe("That job could not be triggered.")
    expect(wrapper.emitted("queued")).toBeFalsy()
  })

  it("says what went wrong when the request itself fails", async () => {
    mockEnqueueJob.mockRejectedValue(new Error("offline"))
    const wrapper = await mountForm()

    ;(wrapper.vm as any).selectedType = "contact.sync-all"
    await settle()
    await (wrapper.vm as any).submit()

    expect((wrapper.vm as any).errorMessage).toBe("offline")
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })

  it("fills in a preset from a job already run, and queues nothing until pressed", async () => {
    const wrapper = await mountForm({type: "cohort.reconcile", payload: {targetId: 4, limit: 20, dryRun: true, intent: "ADD", stray: 1}})

    expect((wrapper.vm as any).selectedType).toBe("cohort.reconcile")
    // Pickers keep the id or enum value; text fields hold what would have been typed.
    expect((wrapper.vm as any).fieldValues).toEqual({targetId: 4, limit: "20", dryRun: "true", intent: "ADD"})
    expect(mockEnqueueJob).not.toHaveBeenCalled()

    await (wrapper.vm as any).submit()
    expect(mockEnqueueJob).toHaveBeenCalledWith("cohort.reconcile", {targetId: 4, limit: 20, dryRun: true, intent: "ADD"})
  })

  it("takes a later preset in place of the fields it had", async () => {
    const wrapper = await mountForm()

    await wrapper.setProps({preset: {type: "contact.sync", payload: {userId: 3}}})
    await settle()
    expect((wrapper.vm as any).fieldValues).toEqual({userId: 3})

    await wrapper.setProps({preset: null})
    await settle()
    expect((wrapper.vm as any).selectedType).toBe("contact.sync")
  })

  it("draws each field with the picker its name asks for", async () => {
    const wrapper = await mountForm({type: "cohort.reconcile", payload: {}})

    expect(wrapper.findComponent({name: "TargetPicker"}).exists()).toBe(true)
    expect(wrapper.findComponent({name: "EnumPicker"}).exists()).toBe(true)
    expect((wrapper.vm as any).pickerForField({name: "periodId", type: "Long"})).toBe("contributionPeriod")
    expect((wrapper.vm as any).pickerForField({name: "eventId", type: "Long"})).toBe("event")
    expect((wrapper.vm as any).pickerForField({name: "name", type: "String"})).toBeNull()
  })

  it("keeps what each field and the type picker hand back", async () => {
    mockListJobTypes.mockResolvedValue([
      ...descriptors,
      {
        type: "everything",
        payloadFields: [
          {name: "userId", type: "Long", required: false},
          {name: "cohortId", type: "Long", required: false},
          {name: "eventId", type: "Long", required: false},
          {name: "periodId", type: "Long", required: false},
          {name: "intent", type: "Intent", required: false, kind: "ENUM", enumValues: ["ADD"]},
          {name: "note", type: "String", required: false},
        ],
      },
    ])
    const wrapper = await mountForm()

    ;(wrapper.vm as any).selectedType = "everything"
    await settle()
    for (const [name, value] of [["UserPicker", 1], ["TargetPicker", 2], ["EventPicker", 3], ["ContributionPeriodPicker", 4], ["EnumPicker", "ADD"]] as const) {
      await wrapper.findComponent({name}).vm.$emit("update:modelValue", value)
    }
    ;(wrapper.vm as any).fieldValues.note = "hello"

    expect((wrapper.vm as any).fieldValues).toEqual({userId: 1, cohortId: 2, eventId: 3, periodId: 4, intent: "ADD", note: "hello"})
  })

  it("draws the refusal it was given", async () => {
    mockEnqueueJob.mockResolvedValue({ok: false, reason: "No."})
    const wrapper = shallowMount(JobRunForm, {props: {preset: {type: "contact.sync-all", payload: {}}}, global: {renderStubDefaultSlot: true}})
    await settle()

    await (wrapper.vm as any).submit()
    await settle()

    expect(wrapper.findComponent({name: "NoticeBox"}).text()).toBe("No.")
  })

  it("keeps an empty list of types when they cannot be read", async () => {
    mockListJobTypes.mockRejectedValue(new Error("offline"))
    const wrapper = await mountForm()

    expect((wrapper.vm as any).typeOptions).toEqual([])
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })
})
