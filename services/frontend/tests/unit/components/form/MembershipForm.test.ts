import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import {validate} from "vee-validate"
import MembershipForm from "@/components/form/MembershipForm.vue"
import {MemberType} from "@/services/api"

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {mockBoardCreateMembership, mockCreateMembership, mockUpdateMembership} = vi.hoisted(() => ({
  mockBoardCreateMembership: vi.fn(),
  mockCreateMembership: vi.fn(),
  mockUpdateMembership: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    boardCreateMembership: mockBoardCreateMembership,
    createMembership: mockCreateMembership,
    updateMembership: mockUpdateMembership,
  }
})

// Mock useVeeForm so validate() always passes in tests
vi.mock("@/composables/formUtils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/composables/formUtils")>()
  return {
    ...actual,
    useVeeForm: () => ({
      formRef: {value: {validate: vi.fn().mockResolvedValue({valid: true})}},
      validate: vi.fn().mockResolvedValue(true),
    }),
  }
})

// ── Stubs ─────────────────────────────────────────────────────────────────────

const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules", "modelValue"],
  emits: ["update:modelValue"],
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' />",
}
const formStub = {template: "<div><slot v-bind='{ meta: { valid: true } }' /></div>"}
const submitButtonStub = {
  name: "SubmitButton",
  props: ["text", "loading", "disabled"],
  template: "<button :data-testid=\"$attrs['data-testid']\" />",
}

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

function rulesByName(wrapper: ReturnType<typeof shallowMount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("MembershipForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── Self-service mode ──────────────────────────────────────────────────────

  it("requires explicit terms acceptance in self-service mode", () => {
    const wrapper = shallowMount(MembershipForm, {
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    expect(rulesByName(wrapper)).toMatchObject({
      consented: "accepted",
    })
  })

  it("returns the intended acceptance validation message", async () => {
    shallowMount(MembershipForm)
    const result = await validate(false, "accepted")

    expect(result.valid).toBe(false)
    expect(result.errors[0]).toBe("You must accept the membership conditions to continue.")
  })

  // ── Board mode ─────────────────────────────────────────────────────────────

  it("board mode shows startDate and memberType fields with required rules", () => {
    const wrapper = shallowMount(MembershipForm, {
      props: {userId: 42},
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    const rules = rulesByName(wrapper)
    expect(rules).toMatchObject({
      startDate: "required",
      memberType: "required",
    })
    // No consent field in board mode
    expect(rules["consented"]).toBeUndefined()
  })

  it("board create: save() calls boardCreateMembership and emits submitted(true)", async () => {
    const membership = makeNewMembership()
    const created = {...membership, id: 5}
    mockBoardCreateMembership.mockResolvedValue({data: created})

    const wrapper = shallowMount(MembershipForm, {
      props: {userId: 42, showSubmit: true},
      attrs: {modelValue: membership, "onUpdate:modelValue": vi.fn()},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    await (wrapper.vm as any).save()

    expect(mockBoardCreateMembership).toHaveBeenCalledWith(
      expect.objectContaining({path: {userId: 42}, throwOnError: true}),
    )
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("board update: save() calls updateMembership when membership has an id", async () => {
    const membership = makeExistingMembership()
    mockUpdateMembership.mockResolvedValue({data: membership})

    const wrapper = shallowMount(MembershipForm, {
      props: {userId: 42, showSubmit: true},
      attrs: {modelValue: membership, "onUpdate:modelValue": vi.fn()},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    await (wrapper.vm as any).save()

    expect(mockUpdateMembership).toHaveBeenCalledWith(
      expect.objectContaining({path: {id: 99}, throwOnError: true}),
    )
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("self-service create: save() calls createMembership when no userId prop", async () => {
    mockCreateMembership.mockResolvedValue({data: makeExistingMembership()})

    const wrapper = shallowMount(MembershipForm, {
      props: {showSubmit: true},
      attrs: {"onUpdate:modelValue": vi.fn()},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    await (wrapper.vm as any).save()

    expect(mockCreateMembership).toHaveBeenCalled()
    expect(mockBoardCreateMembership).not.toHaveBeenCalled()
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("submitTestId is forwarded to SubmitButton as data-testid", () => {
    const wrapper = shallowMount(MembershipForm, {
      props: {userId: 42, showSubmit: true, submitTestId: "manage-membership-create-btn"},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })
    const btn = wrapper.find("button")
    expect(btn.attributes("data-testid")).toBe("manage-membership-create-btn")
  })
})
