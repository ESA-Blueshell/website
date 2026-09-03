import {beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {shallowMount} from "@vue/test-utils"
import UserForm from "@/components/form/UserForm.vue"

const {
  mockStore,
  mockFindMemberProfileByUserId,
  mockSignUp,
  mockCreateUser,
  mockFindUserById,
  mockUpdateDetails,
  mockValidate,
} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      isLoggedIn: false,
      isBoard: false,
    },
  },
  mockFindMemberProfileByUserId: vi.fn(),
  mockSignUp: vi.fn(),
  mockCreateUser: vi.fn(),
  mockFindUserById: vi.fn(),
  mockUpdateDetails: vi.fn(),
  mockValidate: vi.fn(),
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
vi.mock("v-phone-input", () => ({}))

vi.mock("@/services/api", () => ({
  createUser: mockCreateUser,
  updateUser: vi.fn(),
  signUp: mockSignUp,
  updateDetails: mockUpdateDetails,
  findUserById: mockFindUserById,
  findMemberProfileByUserId: mockFindMemberProfileByUserId,
}))

vi.mock("@/composables/formUtils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/composables/formUtils")>()
  return {
    ...actual,
    useVeeForm: () => ({
      formRef: {value: {validate: vi.fn().mockResolvedValue({valid: true})}},
      validate: mockValidate,
    }),
  }
})

const capturedProps: Record<string, unknown>[] = []
const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules", "component", "componentProps"],
  setup(props: Record<string, unknown>) {
    capturedProps.push({...props})
  },
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
    photoConsent: false,
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
    capturedProps.length = 0
    mockStore.getters.isLoggedIn = false
    mockStore.getters.isBoard = false
    mockFindMemberProfileByUserId.mockResolvedValue({status: 404, data: null})
    mockValidate.mockResolvedValue(true)
  })

  describe("registering a new applicant", () => {
    const session = {
      userId: 4242,
      email: "applicant@example.com",
      signupToken: "sel.ver",
      expiresAt: "2099-01-01T00:00:00.000Z",
    }

    function mountForRegistration() {
      return shallowMount(UserForm, {
        props: {
          showPassword: true,
          modelValue: baseModel({email: "applicant@example.com"}),
          options: {includeMemberProfile: true, createVia: "signup"},
        },
        global: {stubs: {Form: formStub, VvField: vvFieldStub}},
      })
    }

    it("registers through the public signup route and keeps the session", async () => {
      mockSignUp.mockResolvedValue({data: session})
      const wrapper = mountForRegistration()

      const saved = await (wrapper.vm as any).save()

      expect(mockSignUp).toHaveBeenCalled()
      expect(mockCreateUser).not.toHaveBeenCalled()
      expect(saved.id).toBe(session.userId)
      expect((wrapper.vm as any).signupSession).toMatchObject({signupToken: "sel.ver"})
    })

    it("never reads the account back, because nothing authorises that yet", async () => {
      mockSignUp.mockResolvedValue({data: session})
      const wrapper = mountForRegistration()

      await (wrapper.vm as any).save()

      expect(mockFindUserById).not.toHaveBeenCalled()
    })

    it("reports a refused registration as a failed submit", async () => {
      mockSignUp.mockRejectedValue(new Error("taken"))
      const wrapper = mountForRegistration()

      expect(await (wrapper.vm as any).save()).toBeNull()
      expect(wrapper.emitted("submitted")).toEqual([[false]])
    })

    // A tab that reloaded holds the token and nothing else. Keying on the account id
    // meant registering again, and the api answered that the applicant's own name was
    // taken — a wall no amount of retyping got them past.
    it("corrects the account the token names rather than registering a second one", async () => {
      mockUpdateDetails.mockResolvedValue({data: undefined})
      const wrapper = shallowMount(UserForm, {
        props: {
          showPassword: true,
          modelValue: baseModel({email: "applicant@example.com"}),
          options: {includeMemberProfile: true, createVia: "signup"},
          signupToken: "sel.ver",
        },
        global: {stubs: {Form: formStub, VvField: vvFieldStub}},
      })

      await (wrapper.vm as any).save()

      expect(mockUpdateDetails).toHaveBeenCalled()
      expect(mockSignUp).not.toHaveBeenCalled()
    })

    it("uses the board route when the form is opened by the board", async () => {
      mockCreateUser.mockResolvedValue({data: {id: 7, email: "b@example.com", roles: [], version: 0}})
      const wrapper = shallowMount(UserForm, {
        props: {
          showPassword: true,
          modelValue: baseModel(),
          options: {includeMemberProfile: false, createVia: "board"},
        },
        global: {stubs: {Form: formStub, VvField: vvFieldStub}},
      })

      await (wrapper.vm as any).save()

      expect(mockCreateUser).toHaveBeenCalled()
      expect(mockSignUp).not.toHaveBeenCalled()
    })
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
      studentNumber: "",
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

  it("merges member profile fields into modelValue on successful load", async () => {
    mockFindMemberProfileByUserId.mockResolvedValue({
      status: 200,
      data: {
        dateOfBirth: "1999-06-15",
        studentNumber: "s456",
        gender: "M",
        nationality: "DE",
        bhv: true,
        ehbo: false,
        version: 3,
      },
    })

    const model = baseModel({id: 10})
    shallowMount(UserForm, {
      props: {
        modelValue: model,
        "onUpdate:modelValue": (val: Record<string, unknown>) => Object.assign(model, val),
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
    await nextTick()

    expect(mockFindMemberProfileByUserId).toHaveBeenCalledWith({path: {userId: 10}})
    // The member profile should have been merged via the model update
    const profile = (model as Record<string, unknown>).memberProfile as Record<string, unknown> | undefined
    expect(profile).toBeDefined()
    if (profile) {
      expect(profile.dateOfBirth).toBe("1999-06-15")
      expect(profile.studentNumber).toBe("s456")
      expect(profile.nationality).toBe("DE")
    }
  })

  it("handles findMemberProfileByUserId returning non-200 gracefully", async () => {
    mockFindMemberProfileByUserId.mockResolvedValue({
      status: 500,
      data: null,
    })

    const wrapper = shallowMount(UserForm, {
      props: {
        modelValue: baseModel({id: 99}),
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
    // Should not crash; memberProfile should remain the default
    const emitted = wrapper.emitted("update:modelValue")
    if (emitted) {
      const lastEmit = emitted[emitted.length - 1][0] as Record<string, unknown>
      const profile = lastEmit.memberProfile as Record<string, unknown> | undefined
      if (profile) {
        expect(profile.dateOfBirth).toBe("")
      }
    }
  })

  it("does not show member profile fields when includeMemberProfile is false", () => {
    const wrapper = shallowMount(UserForm, {
      props: {
        modelValue: baseModel({id: 5}),
        options: {
          includeMemberProfile: false,
          updateKind: "user",
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

    expect(rules.dateOfBirth).toBeUndefined()
    expect(rules.nationality).toBeUndefined()
    expect(rules.studentNumber).toBeUndefined()
  })

  describe("setting a password", () => {
    function fieldNames(wrapper: ReturnType<typeof shallowMount>) {
      return wrapper.findAll(".vv-field-stub").map((f) => String(f.attributes("data-name")))
    }

    it("asks for one while the account is being created", () => {
      const wrapper = shallowMount(UserForm, {
        props: {showPassword: true, modelValue: baseModel()},
        global: {stubs: {Form: formStub, VvField: vvFieldStub}},
      })

      expect(fieldNames(wrapper)).toContain("password")
      expect(fieldNames(wrapper)).toContain("confirmPassword")
    })

    it("never asks for one once the account exists", () => {
      // Every update path leaves the password alone, so an empty required field
      // here would block a form that has nothing wrong with it.
      const wrapper = shallowMount(UserForm, {
        props: {showPassword: true, modelValue: baseModel({id: 12})},
        global: {stubs: {Form: formStub, VvField: vvFieldStub}},
      })

      expect(fieldNames(wrapper)).not.toContain("password")
      expect(fieldNames(wrapper)).not.toContain("confirmPassword")
    })

    it("never asks for one when an applicant returns on a signup token", () => {
      const wrapper = shallowMount(UserForm, {
        props: {showPassword: true, modelValue: baseModel({id: 12}), signupToken: "sel.ver"},
        global: {stubs: {Form: formStub, VvField: vvFieldStub}},
      })

      expect(fieldNames(wrapper)).not.toContain("password")
    })

    it("still lets that applicant fix their own name and username", () => {
      const wrapper = shallowMount(UserForm, {
        props: {showPassword: true, modelValue: baseModel({id: 12}), signupToken: "sel.ver"},
        global: {stubs: {Form: formStub, VvField: vvFieldStub}},
      })
      const rules = rulesByName(wrapper)

      expect(rules.firstName).toBe("required")
      expect(rules.username).toBe("required|alphaNum")
      // The address is the exception: it moves the confirmation link, so it
      // changes on the confirmation step instead.
      expect(rules.email).toBe("")
    })
  })

  it("uses the globally registered VPhoneInput component for the phone field", () => {
    shallowMount(UserForm, {
      props: {
        modelValue: baseModel(),
        options: {includeMemberProfile: false, updateKind: "create"},
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    const phoneField = capturedProps.find((p) => p.name === "phoneNumber")
    expect(phoneField).toBeDefined()
    expect(phoneField!.component).toBe("VPhoneInput")
  })
})
