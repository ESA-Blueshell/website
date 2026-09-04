import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {validate} from "vee-validate"
import MembershipForm from "@/components/form/MembershipForm.vue"
import {MemberType} from "@/services/api"

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {mockBoardCreateMembership, mockCreateMembership, mockUpdateMembership, mockApply, mockValidate} =
  vi.hoisted(() => ({
    mockBoardCreateMembership: vi.fn(),
    mockCreateMembership: vi.fn(),
    mockUpdateMembership: vi.fn(),
    mockApply: vi.fn(),
    mockValidate: vi.fn(),
  }))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    boardCreateMembership: mockBoardCreateMembership,
    createMembership: mockCreateMembership,
    updateMembership: mockUpdateMembership,
    apply: mockApply,
  }
})

// validate() is driven per test: it passes by default, and one test flips it.
// formRef stays the composable's own ref, because a template ref bound to a plain
// object never populates and the form context backend errors land on is then absent.
vi.mock("@/composables/formUtils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/composables/formUtils")>()
  return {
    ...actual,
    useVeeForm: () => ({...actual.useVeeForm(), validate: mockValidate}),
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

function rulesByName(wrapper: ReturnType<typeof mount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("MembershipForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockValidate.mockResolvedValue(true)
  })

  function fieldNamed(wrapper: ReturnType<typeof mount>, name: string) {
    const field = wrapper
      .findAllComponents({name: "VvField"})
      .find((candidate) => candidate.props("name") === name)
    if (!field) throw new Error(`No VvField named ${name}`)
    return field
  }

  // ── Self-service mode ──────────────────────────────────────────────────────

  it("requires explicit terms acceptance in self-service mode", () => {
    const wrapper = mount(MembershipForm, {
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
    mount(MembershipForm)
    const result = await validate(false, "accepted")

    expect(result.valid).toBe(false)
    expect(result.errors[0]).toBe("You must accept the membership conditions to continue.")
  })

  // ── Board mode ─────────────────────────────────────────────────────────────

  it("board mode shows startDate and memberType fields with required rules", () => {
    const wrapper = mount(MembershipForm, {
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

    const wrapper = mount(MembershipForm, {
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

    const wrapper = mount(MembershipForm, {
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

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true},
      attrs: {"onUpdate:modelValue": vi.fn()},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    await (wrapper.vm as any).save()

    expect(mockCreateMembership).toHaveBeenCalled()
    expect(mockBoardCreateMembership).not.toHaveBeenCalled()
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("signup: save() submits on the token and returns the outcome", async () => {
    mockApply.mockResolvedValue({data: {emailConfirmed: false, membershipStarted: false}})

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, signupToken: "sel.ver"},
      attrs: {"onUpdate:modelValue": vi.fn()},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    const outcome = await (wrapper.vm as any).save()

    expect(mockApply).toHaveBeenCalledWith({
      headers: {"X-Signup-Token": "sel.ver"},
      body: {conditionsAccepted: false},
      throwOnError: true,
    })
    // A new applicant must not go through the signed-in route.
    expect(mockCreateMembership).not.toHaveBeenCalled()
    expect(outcome).toEqual({emailConfirmed: false, membershipStarted: false})
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("signup: a refused application surfaces as a failed submit", async () => {
    mockApply.mockRejectedValue(new Error("refused"))

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, signupToken: "sel.ver"},
      attrs: {"onUpdate:modelValue": vi.fn()},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    expect(await (wrapper.vm as any).save()).toBeNull()
    expect(wrapper.emitted("submitted")).toEqual([[false]])
  })

  it("an invalid form is not submitted anywhere", async () => {
    mockValidate.mockResolvedValue(false)

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, signupToken: "sel.ver"},
      attrs: {"onUpdate:modelValue": vi.fn()},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    expect(await (wrapper.vm as any).save()).toBeNull()
    expect(mockApply).not.toHaveBeenCalled()
    expect(mockCreateMembership).not.toHaveBeenCalled()
    expect(wrapper.emitted("submitted")).toEqual([[false]])
  })

  it("board mode writes every field edit back to the membership", async () => {
    const membership = makeNewMembership()
    const wrapper = mount(MembershipForm, {
      props: {userId: 42},
      attrs: {modelValue: membership, "onUpdate:modelValue": vi.fn()},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, VCheckbox: emittingStub("VCheckbox")}},
    })

    await fieldNamed(wrapper, "startDate").vm.$emit("update:modelValue", "2026-03-01")
    await fieldNamed(wrapper, "endDate").vm.$emit("update:modelValue", "2026-09-01")
    await fieldNamed(wrapper, "memberType").vm.$emit("update:modelValue", MemberType.ALUMNI)
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
      global: {stubs: {Form: formStub, VvField: vvFieldStub}},
    })

    await fieldNamed(wrapper, "consented").vm.$emit("update:modelValue", true)

    mockCreateMembership.mockResolvedValue({data: makeExistingMembership()})
    await (wrapper.vm as any).save()

    // The member type is the association's call, not the applicant's.
    expect(mockCreateMembership).toHaveBeenCalledWith(
      expect.objectContaining({body: {conditionsAccepted: true}}),
    )
  })

  // The whole point of the template ref (ADR-004): a refusal the api pins on a field
  // has to arrive on that field. Runs against the real <Form> and real VvField, so it
  // fails if formRef never populates.
  it("a refused field lands on the field the api named", async () => {
    mockUpdateMembership.mockRejectedValue({
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

    expect(wrapper.text()).toContain("Pick a start date in the future.")
  })

  it("submitTestId is forwarded to SubmitButton as data-testid", () => {
    const wrapper = mount(MembershipForm, {
      props: {userId: 42, showSubmit: true, submitTestId: "manage-membership-create-btn"},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })
    const btn = wrapper.find("button")
    expect(btn.attributes("data-testid")).toBe("manage-membership-create-btn")
  })
})
