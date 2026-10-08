import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import MembershipForm from "@/components/form/MembershipForm.vue"
import {MemberType} from "@/services/api"

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {mockStartMembershipAsBoard, mockStartOwnMembership, mockSaveMembership, mockApplyForMembership} =
  vi.hoisted(() => ({
    mockStartMembershipAsBoard: vi.fn(),
    mockStartOwnMembership: vi.fn(),
    mockSaveMembership: vi.fn(),
    mockApplyForMembership: vi.fn(),
  }))

vi.mock("@/domains/user", async () => {
  const {MemberType} = await import("@/services/api")
  return {
    MemberType,
    startMembershipAsBoard: mockStartMembershipAsBoard,
    startOwnMembership: mockStartOwnMembership,
    saveMembership: mockSaveMembership,
    applyForMembership: mockApplyForMembership,
  }
})

// ── Stubs ─────────────────────────────────────────────────────────────────────

const emittingStub = (name: string) => ({
  name,
  props: ["modelValue"],
  emits: ["update:modelValue"],
  template: "<div />",
})
const submitButtonStub = {
  name: "SubmitButton",
  props: ["text", "loading", "disabled"],
  template: "<button :data-testid=\"$attrs['data-testid']\" />",
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makeNewMembership(): import("@/services/api").MembershipResponse {
  return {
    id: 0,
    userId: 42,
    startDate: "2025-06-01",
    memberType: MemberType.REGULAR,
    incasso: false,
    version: 0,
    createdAt: "",
    updatedAt: "",
  } as import("@/services/api").MembershipResponse
}

function makeExistingMembership(): import("@/services/api").MembershipResponse {
  return {
    id: 99,
    userId: 42,
    startDate: "2025-01-01",
    memberType: MemberType.REGULAR,
    incasso: false,
    version: 2,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
  }
}

const tickConditions = async (wrapper: ReturnType<typeof mount>) => {
  await wrapper.findComponent({name: "CheckBox"}).vm.$emit("update:modelValue", true)
}

const fieldLabelled = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAllComponents({name: "FormControl"}).find(field => field.props("label") === label)!

describe("MembershipForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── Self-service mode ──────────────────────────────────────────────────────

  it("refuses to go on until the conditions are accepted", async () => {
    const wrapper = mount(MembershipForm, {props: {showSubmit: true}, global: {stubs: {SubmitButton: submitButtonStub}}})

    expect(wrapper.findComponent({name: "SubmitButton"}).props("disabled")).toBe(true)
    expect(await (wrapper.vm as any).save()).toBeNull()
    await wrapper.vm.$nextTick()

    expect(wrapper.findComponent({name: "CheckBox"}).props("errorMessages"))
      .toEqual(["You must accept the membership conditions to continue."])
    expect(mockStartOwnMembership).not.toHaveBeenCalled()
    expect(wrapper.emitted("submitted")).toEqual([[false]])
  })

  it("accepts the conditions with the island's tick box", () => {
    const wrapper = mount(MembershipForm)

    expect(wrapper.findComponent({name: "CheckBox"}).exists()).toBe(true)
    expect(wrapper.find(".v-checkbox").exists()).toBe(false)
  })

  // ── Board mode ─────────────────────────────────────────────────────────────

  it("board mode asks for a start date and a member type, and no acceptance", async () => {
    const wrapper = mount(MembershipForm, {props: {userId: 42, modelValue: {...makeNewMembership(), startDate: ""}}})
    await wrapper.findComponent({name: "MemberTypeSelect"}).vm.$emit("update:modelValue", "")

    expect(await (wrapper.vm as any).validate()).toBe(false)
    await wrapper.vm.$nextTick()

    expect(fieldLabelled(wrapper, "Start Date").props("errorMessages")).toEqual(["This field is required"])
    expect(fieldLabelled(wrapper, "End Date").props("errorMessages")).toEqual([])
    expect(wrapper.findComponent({name: "MemberTypeSelect"}).props("errorMessages")).toEqual(["This field is required"])
    expect(wrapper.findComponent({name: "CheckBox"}).exists()).toBe(false)
  })

  it("board create: save() calls boardCreateMembership and emits submitted(true)", async () => {
    const membership = makeNewMembership()
    const created = {...membership, id: 5}
    mockStartMembershipAsBoard.mockResolvedValue(created)

    const wrapper = mount(MembershipForm, {
      props: {userId: 42, showSubmit: true},
      attrs: {modelValue: membership, "onUpdate:modelValue": vi.fn()},
      global: {stubs: {SubmitButton: submitButtonStub}},
    })

    await (wrapper.vm as any).save()

    expect(mockStartMembershipAsBoard).toHaveBeenCalledWith(42, expect.any(Object))
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("board update: save() calls updateMembership when membership has an id", async () => {
    const membership = makeExistingMembership()
    mockSaveMembership.mockResolvedValue(membership)

    const wrapper = mount(MembershipForm, {
      props: {userId: 42, showSubmit: true},
      attrs: {modelValue: membership, "onUpdate:modelValue": vi.fn()},
      global: {stubs: {SubmitButton: submitButtonStub}},
    })

    await (wrapper.vm as any).save()

    expect(mockSaveMembership).toHaveBeenCalledWith(99, expect.any(Object))
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("self-service create: save() calls createMembership when no userId prop", async () => {
    mockStartOwnMembership.mockResolvedValue(makeExistingMembership())

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true},
      attrs: {"onUpdate:modelValue": vi.fn()},
      global: {stubs: {SubmitButton: submitButtonStub}},
    })
    await tickConditions(wrapper)

    await (wrapper.vm as any).save()

    expect(mockStartOwnMembership).toHaveBeenCalled()
    expect(mockStartMembershipAsBoard).not.toHaveBeenCalled()
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("signup: save() submits on the token and returns the outcome", async () => {
    mockApplyForMembership.mockResolvedValue({emailConfirmed: false, membershipStarted: false})

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, signupToken: "sel.ver"},
      attrs: {"onUpdate:modelValue": vi.fn()},
      global: {stubs: {SubmitButton: submitButtonStub}},
    })
    await tickConditions(wrapper)

    const outcome = await (wrapper.vm as any).save()

    expect(mockApplyForMembership).toHaveBeenCalledWith("sel.ver", true)
    // A new applicant must not go through the signed-in route.
    expect(mockStartOwnMembership).not.toHaveBeenCalled()
    expect(outcome).toEqual({emailConfirmed: false, membershipStarted: false})
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("signup: a refused application surfaces as a failed submit", async () => {
    mockApplyForMembership.mockRejectedValue(new Error("refused"))

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, signupToken: "sel.ver"},
      attrs: {"onUpdate:modelValue": vi.fn()},
      global: {stubs: {SubmitButton: submitButtonStub}},
    })
    await tickConditions(wrapper)

    expect(await (wrapper.vm as any).save()).toBeNull()
    expect(wrapper.emitted("submitted")).toEqual([[false]])
  })

  it("board mode writes every field edit back to the membership", async () => {
    const membership = makeNewMembership()
    const wrapper = mount(MembershipForm, {
      props: {userId: 42},
      attrs: {modelValue: membership, "onUpdate:modelValue": vi.fn()},
      global: {stubs: {VCheckbox: emittingStub("VCheckbox")}},
    })

    await fieldLabelled(wrapper, "Start Date").vm.$emit("update:modelValue", "2026-03-01")
    await fieldLabelled(wrapper, "End Date").vm.$emit("update:modelValue", "2026-09-01")
    await wrapper.findComponent({name: "MemberTypeSelect"}).vm.$emit("update:modelValue", MemberType.ALUMNI)
    await wrapper.findComponent({name: "VCheckbox"}).vm.$emit("update:modelValue", true)

    expect(membership).toMatchObject({
      startDate: "2026-03-01",
      endDate: "2026-09-01",
      memberType: MemberType.ALUMNI,
      incasso: true,
    })
  })

  it("self-service sends the acceptance and nothing else", async () => {
    const membership = makeNewMembership()
    const wrapper = mount(MembershipForm, {
      attrs: {modelValue: membership, "onUpdate:modelValue": vi.fn()},
    })

    await tickConditions(wrapper)

    mockStartOwnMembership.mockResolvedValue(makeExistingMembership())
    await (wrapper.vm as any).save()

    // The member type is the association's call, not the applicant's.
    expect(mockStartOwnMembership).toHaveBeenCalledWith(true)
  })

  it("a refused field lands on the field the api named", async () => {
    mockSaveMembership.mockRejectedValue({
      response: {
        status: 400,
        data: {
          status: 400,
          errors: [{objectName: "membership", field: "startDate", message: "Pick a start date in the future."}],
        },
      },
    })

    const wrapper = mount(MembershipForm, {
      props: {userId: 42, showSubmit: true},
      attrs: {modelValue: makeExistingMembership(), "onUpdate:modelValue": vi.fn()},
    })

    await (wrapper.vm as any).save()
    await flushPromises()

    expect(fieldLabelled(wrapper, "Start Date").props("errorMessages")).toEqual(["Pick a start date in the future."])
  })

  it("submitTestId is forwarded to SubmitButton as data-testid", () => {
    const wrapper = mount(MembershipForm, {
      props: {userId: 42, showSubmit: true, submitTestId: "manage-membership-create-btn"},
      global: {stubs: {SubmitButton: submitButtonStub}},
    })
    expect(wrapper.findComponent({name: "SubmitButton"}).attributes("data-testid")).toBe("manage-membership-create-btn")
  })
})
