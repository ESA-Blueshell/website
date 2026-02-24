import {beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {shallowMount} from "@vue/test-utils"
import UserForm from "@/components/form/UserForm.vue"

const {
  mockStore,
  mockFindMemberProfileByUserId,
} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      isLoggedIn: false,
      isBoard: false,
    },
  },
  mockFindMemberProfileByUserId: vi.fn(),
}))

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...actual,
    useStore: () => mockStore,
  }
})
vi.mock("flag-icons/css/flag-icons.min.css", () => ({}))
vi.mock("v-phone-input/styles", () => ({}))
vi.mock("v-phone-input", () => ({
  VPhoneInput: {
    name: "VPhoneInput",
    template: "<v-phone-input-stub />",
  },
}))

vi.mock("@/services/api", () => ({
  createUser: vi.fn(),
  updateUser: vi.fn(),
  findMemberProfileByUserId: mockFindMemberProfileByUserId,
}))

const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules"],
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' />",
}
const formStub = {template: "<div><slot /></div>"}

function baseModel(overrides: Record<string, unknown> = {}) {
  return {
    id: undefined,
    initials: "",
    firstName: "",
    prefix: "",
    lastName: "",
    username: "",
    discord: "",
    email: "",
    phoneNumber: "",
    newsletter: true,
    consentPrivacy: false,
    password: "",
    ...overrides,
  }
}

function rulesByName(wrapper: ReturnType<typeof shallowMount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("UserForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isLoggedIn = false
    mockStore.getters.isBoard = false
    mockFindMemberProfileByUserId.mockResolvedValue({status: 404, data: null})
  })

  it("requires identity/contact fields for create flow and includes password rules", () => {
    const wrapper = shallowMount(UserForm, {
      props: {
        showPassword: true,
        modelValue: baseModel(),
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    const rules = rulesByName(wrapper)

    expect(rules).toMatchObject({
      initials: "required",
      firstName: "required",
      lastName: "required",
      username: "required|alphaNum",
      discord: "required",
      email: "required|email|noStudentEmail",
      phoneNumber: "required|phoneMobile:NL",
      password: "required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial",
      confirmPassword: "required|match:@password",
      consentPrivacy: "acceptedPrivacyPolicy",
      newsletter: "",
    })
  })

  it("relaxes identity validation for user self-update while keeping contact rules", () => {
    const wrapper = shallowMount(UserForm, {
      props: {
        modelValue: baseModel({id: 12}),
        options: {
          updateKind: "user",
          includeMemberProfile: false,
        },
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    const rules = rulesByName(wrapper)

    expect(rules).toMatchObject({
      initials: "",
      firstName: "",
      lastName: "",
      username: "",
      email: "",
      discord: "required",
      phoneNumber: "required|phoneMobile:NL",
    })
    expect(rules.consentPrivacy).toBeUndefined()
  })

  it("does not require privacy agreement in board create mode", () => {
    const wrapper = shallowMount(UserForm, {
      props: {
        modelValue: baseModel(),
        options: {
          includeMemberProfile: true,
          updateKind: "board",
        },
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    const rules = rulesByName(wrapper)

    expect(rules.consentPrivacy).toBeUndefined()
  })

  it("adds member profile validations when profile mode is enabled", () => {
    const wrapper = shallowMount(UserForm, {
      props: {
        modelValue: baseModel(),
        options: {
          includeMemberProfile: true,
          updateKind: "auto",
        },
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    const rules = rulesByName(wrapper)

    expect(rules).toMatchObject({
      dateOfBirth: "dateRequired",
      nationality: "required",
      gender: "",
      studentNumber: "required",
      consentPrivacy: "acceptedPrivacyPolicy",
    })
  })

  it("loads member profile once per user id without refetch loop", async () => {
    mockFindMemberProfileByUserId.mockResolvedValue({
      status: 200,
      data: {
        dateOfBirth: "2000-01-01",
        studentNumber: "s123",
        gender: "X",
        photoConsent: false,
        nationality: "NL",
        bhv: false,
        ehbo: false,
        version: 1,
      },
    })

    const wrapper = shallowMount(UserForm, {
      props: {
        modelValue: baseModel({id: 42}),
        options: {
          includeMemberProfile: true,
          updateKind: "board",
        },
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    await nextTick()

    expect(mockFindMemberProfileByUserId).toHaveBeenCalledTimes(1)
    expect(mockFindMemberProfileByUserId).toHaveBeenCalledWith({path: {userId: 42}})

    await wrapper.setProps({
      modelValue: baseModel({id: 42, discord: "updated"}),
    })
    await nextTick()

    expect(mockFindMemberProfileByUserId).toHaveBeenCalledTimes(1)
  })
})
