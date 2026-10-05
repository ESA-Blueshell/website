import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {validate} from "vee-validate"
import MembershipForm from "@/components/form/MembershipForm.vue"

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {mockStartOwnMembership, mockApplyForMembership, mockValidate} = vi.hoisted(() => ({
  mockStartOwnMembership: vi.fn(),
  mockApplyForMembership: vi.fn(),
  mockValidate: vi.fn(),
}))

vi.mock("@/domains/user", async () => {
  const {MemberType} = await import("@/services/api")
  return {
    MemberType,
    startOwnMembership: mockStartOwnMembership,
    applyForMembership: mockApplyForMembership,
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
const submitButtonStub = {
  name: "SubmitButton",
  props: ["text", "loading", "disabled"],
  template: "<button :data-testid=\"$attrs['data-testid']\" />",
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

  it("a signed-in account applies through its own route", async () => {
    mockStartOwnMembership.mockResolvedValue({emailConfirmed: true, membershipStarted: true})

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    await (wrapper.vm as any).save()

    expect(mockStartOwnMembership).toHaveBeenCalled()
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("signup: save() submits on the token and returns the outcome", async () => {
    mockApplyForMembership.mockResolvedValue({emailConfirmed: false, membershipStarted: false})

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, signupToken: "sel.ver"},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    const outcome = await (wrapper.vm as any).save()

    expect(mockApplyForMembership).toHaveBeenCalledWith("sel.ver", false)
    // A new applicant must not go through the signed-in route.
    expect(mockStartOwnMembership).not.toHaveBeenCalled()
    expect(outcome).toEqual({emailConfirmed: false, membershipStarted: false})
    expect(wrapper.emitted("submitted")).toEqual([[true]])
  })

  it("signup: a refused application surfaces as a failed submit", async () => {
    mockApplyForMembership.mockRejectedValue(new Error("refused"))

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, signupToken: "sel.ver"},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    expect(await (wrapper.vm as any).save()).toBeNull()
    expect(wrapper.emitted("submitted")).toEqual([[false]])
  })

  it("an invalid form is not submitted anywhere", async () => {
    mockValidate.mockResolvedValue(false)

    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, signupToken: "sel.ver"},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })

    expect(await (wrapper.vm as any).save()).toBeNull()
    expect(mockApplyForMembership).not.toHaveBeenCalled()
    expect(mockStartOwnMembership).not.toHaveBeenCalled()
    expect(wrapper.emitted("submitted")).toEqual([[false]])
  })

  it("self-service sends the acceptance and nothing else", async () => {
    const wrapper = mount(MembershipForm, {global: {stubs: {Form: formStub, VvField: vvFieldStub}}})

    await fieldNamed(wrapper, "consented").vm.$emit("update:modelValue", true)

    mockStartOwnMembership.mockResolvedValue({emailConfirmed: true, membershipStarted: true})
    await (wrapper.vm as any).save()

    // The member type is the association's call, not the applicant's.
    expect(mockStartOwnMembership).toHaveBeenCalledWith(true)
  })

  // The whole point of the template ref (ADR-004): a refusal the api pins on a field
  // has to arrive on that field. Runs against the real <Form> and real VvField, so it
  // fails if formRef never populates.
  it("a refused field lands on the field the api named", async () => {
    mockStartOwnMembership.mockRejectedValue({
      response: {
        status: 400,
        data: {
          status: 400,
          errors: [{objectName: "membership", field: "conditionsAccepted", message: "Accept the conditions first."}],
        },
      },
    })

    const wrapper = mount(MembershipForm, {props: {showSubmit: true}})

    await (wrapper.vm as any).save()
    await flushPromises()

    expect(wrapper.text()).toContain("Accept the conditions first.")
  })

  it("submitTestId is forwarded to SubmitButton as data-testid", () => {
    const wrapper = mount(MembershipForm, {
      props: {showSubmit: true, submitTestId: "membership-apply-btn"},
      global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: submitButtonStub}},
    })
    const btn = wrapper.find("button")
    expect(btn.attributes("data-testid")).toBe("membership-apply-btn")
  })
})
