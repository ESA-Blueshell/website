import {beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import UserForm from "@/components/form/UserForm.vue"

const {
  mockStore,
  mockFindMemberProfileByUserId,
  mockSignUp,
  mockCreateUser,
  mockFindUserById,
  mockUpdateDetails,
  mockUpdateUser,
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
  mockUpdateUser: vi.fn(),
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

vi.mock("@/domains/user", () => ({
  saveNewUser: mockCreateUser,
  saveUser: mockUpdateUser,
  startSignup: mockSignUp,
  saveSignupDetails: mockUpdateDetails,
  readUser: mockFindUserById,
  readMemberProfile: mockFindMemberProfileByUserId,
}))

/** The server's member picker reads Discord as it mounts; here it only has to carry a value and an error. */
const DiscordMemberPicker = {
  name: "DiscordMemberPicker",
  props: ["modelValue", "discordId", "label", "errorMessages"],
  emits: ["update:modelValue", "update:discordId"],
  template: "<div><span v-if='errorMessages?.length' role='alert'>{{ errorMessages[0] }}</span></div>",
}
const stubs = {DiscordMemberPicker}

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

/** Everything a new applicant must give, given. */
function filled(overrides: Record<string, unknown> = {}) {
  return baseModel({
    initials: "A.",
    firstName: "Ann",
    lastName: "Vos",
    username: "ann",
    discord: "ann",
    email: "ann@example.com",
    phoneNumber: "0612345678",
    password: "Secret12!",
    consentPrivacy: true,
    memberProfile: {dateOfBirth: "2000-01-02", nationality: "NL", gender: "", studentNumber: "", bhv: false, ehbo: false, nameOnRosters: false},
    ...overrides,
  })
}

const box = (wrapper: ReturnType<typeof mount>, name: string) => wrapper.find(`[data-testid="user-form-${name}-field"]`)

/** Types into the field inside the box of that name. */
async function type(wrapper: ReturnType<typeof mount>, name: string, value: unknown) {
  box(wrapper, name).findComponent({name: "FormControl"}).vm.$emit("update:modelValue", value)
  await nextTick()
}

/** What each field shown refuses once a save is tried, by the name in its box's testid. */
async function failures(wrapper: ReturnType<typeof mount>): Promise<Record<string, string>> {
  await (wrapper.vm as any).validate()
  await nextTick()
  const said: Record<string, string> = {}
  for (const one of wrapper.findAll('[data-testid^="user-form-"][data-testid$="-field"]')) {
    const name = one.attributes("data-testid")!.replace(/^user-form-|-field$/g, "")
    said[name] = one.find(".island-field__said--wrong, [role=alert]").exists()
      ? one.find(".island-field__said--wrong, [role=alert]").text()
      : ""
  }
  return said
}

describe("UserForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isLoggedIn = false
    mockStore.getters.isBoard = false
    mockFindMemberProfileByUserId.mockResolvedValue(null)
  })

  describe("registering a new applicant", () => {
    const session = {
      userId: 4242,
      email: "applicant@example.com",
      signupToken: "sel.ver",
      expiresAt: "2099-01-01T00:00:00.000Z",
    }

    async function mountForRegistration() {
      const wrapper = mount(UserForm, {
        props: {
          showPassword: true,
          modelValue: filled({email: "applicant@example.com"}),
          options: {includeMemberProfile: true, createVia: "signup"},
        },
        global: {stubs},
      })
      await type(wrapper, "password-repeat", "Secret12!")
      return wrapper
    }

    it("registers through the public signup route and keeps the session", async () => {
      mockSignUp.mockResolvedValue(session)
      const wrapper = await mountForRegistration()

      const saved = await (wrapper.vm as any).save()

      expect(mockSignUp).toHaveBeenCalled()
      expect(mockCreateUser).not.toHaveBeenCalled()
      expect(saved.id).toBe(session.userId)
      expect(saved.password).toBe("")
      expect((wrapper.vm as any).signupSession).toMatchObject({signupToken: "sel.ver"})
    })

    it("never reads the account back, because nothing authorises that yet", async () => {
      mockSignUp.mockResolvedValue(session)
      const wrapper = await mountForRegistration()

      await (wrapper.vm as any).save()

      expect(mockFindUserById).not.toHaveBeenCalled()
    })

    it("reports a refused registration as a failed submit", async () => {
      mockSignUp.mockRejectedValue(new Error("taken"))
      const wrapper = await mountForRegistration()

      expect(await (wrapper.vm as any).save()).toBeNull()
      expect(wrapper.emitted("submitted")).toEqual([[false]])
    })

    it("puts the api's refusal on the field it names, the member profile's flattened onto its own field", async () => {
      mockSignUp.mockRejectedValue({response: {status: 400, data: {errors: [
        {field: "username", message: "is taken"},
        {field: "memberProfile.nationality", message: "is not a country"},
      ]}}})
      const wrapper = await mountForRegistration()

      await (wrapper.vm as any).save()
      await nextTick()

      expect(box(wrapper, "username").text()).toContain("is taken")
      expect(box(wrapper, "nationality").text()).toContain("is not a country")
    })

    // A tab that reloaded holds the token and nothing else. Keying on the account id
    // meant registering again, and the api answered that the applicant's own name was
    // taken — a wall no amount of retyping got them past.
    it("corrects the account the token names rather than registering a second one", async () => {
      mockUpdateDetails.mockResolvedValue(undefined)
      const wrapper = mount(UserForm, {
        props: {
          showPassword: true,
          modelValue: filled({id: 4242, email: "applicant@example.com"}),
          options: {includeMemberProfile: true, createVia: "signup"},
          signupToken: "sel.ver",
        },
        global: {stubs},
      })

      await (wrapper.vm as any).save()

      expect(mockUpdateDetails).toHaveBeenCalled()
      expect(mockSignUp).not.toHaveBeenCalled()
    })

    it("uses the board route when the form is opened by the board", async () => {
      mockCreateUser.mockResolvedValue({id: 7, email: "b@example.com", roles: [], version: 0})
      const wrapper = mount(UserForm, {
        props: {
          showPassword: true,
          modelValue: filled(),
          options: {includeMemberProfile: false, createVia: "board"},
        },
        global: {stubs},
      })
      await type(wrapper, "password-repeat", "Secret12!")

      await (wrapper.vm as any).save()

      expect(mockCreateUser).toHaveBeenCalled()
      expect(mockSignUp).not.toHaveBeenCalled()
    })

    // The profile is read back after the account is written, and an account created without
    // one has nothing to merge.
    it("keeps what was typed when the account it created has no profile yet", async () => {
      mockCreateUser.mockResolvedValue({id: 7, email: "b@example.com", roles: [], version: 0})
      const wrapper = mount(UserForm, {
        props: {
          showPassword: true,
          modelValue: filled(),
          options: {includeMemberProfile: true, createVia: "board"},
        },
        global: {stubs},
      })
      await type(wrapper, "password-repeat", "Secret12!")

      const saved = await (wrapper.vm as any).save()

      expect(mockFindMemberProfileByUserId).toHaveBeenCalledWith(7)
      expect(saved.id).toBe(7)
    })
  })

  it("hands back the account as saved, so a second save carries its new version", async () => {
    mockUpdateUser.mockResolvedValue({id: 12, email: "a@example.com", roles: [], version: 3})
    const wrapper = mount(UserForm, {
      props: {
        modelValue: filled({id: 12, version: 2}),
        "onUpdate:modelValue": vi.fn(),
        options: {includeMemberProfile: false},
      },
      global: {stubs},
    })

    expect(await (wrapper.vm as any).save()).toMatchObject({id: 12, version: 3})
  })

  it("requires the member fields of a member only, and sends no date where none is given", async () => {
    const mountWith = (required?: boolean) => mount(UserForm, {
      props: {
        modelValue: {...filled(), memberProfile: undefined, id: 5, version: 1},
        options: {includeMemberProfile: true, updateKind: "user", ...(required === undefined ? {} : {memberProfileRequired: required})},
      },
      global: {stubs},
    })

    expect(await failures(mountWith())).toMatchObject({"date-of-birth": "Date is required"})
    const guest = mountWith(false)
    expect(await failures(guest)).toMatchObject({"date-of-birth": "", "nationality": ""})

    mockUpdateUser.mockResolvedValue({id: 5, email: "a@example.com", roles: [], version: 2})
    await (guest.vm as any).save()
    expect(mockUpdateUser.mock.calls.at(-1)![1].memberProfile.dateOfBirth).toBeUndefined()
  })

  it("asks a new account for its name, contact, a strong repeated password and the privacy agreement", async () => {
    const wrapper = mount(UserForm, {props: {showPassword: true, modelValue: baseModel()}, global: {stubs}})

    expect(await failures(wrapper)).toMatchObject({
      "initials": "This field is required",
      "first-name": "This field is required",
      "last-name": "This field is required",
      "username": "This field is required",
      "discord": "This field is required",
      "email": "This field is required",
      "phone-number": "This field is required",
      "password": "This field is required",
      "password-repeat": "This field is required",
      "privacy-consent": "You must agree to the privacy policy to create an account.",
      "newsletter": "",
      "prefix": "",
    })

    await type(wrapper, "email", "ann@example")
    await type(wrapper, "phone-number", "0201234567")
    await type(wrapper, "password", "short")
    await type(wrapper, "password-repeat", "other")
    expect(await failures(wrapper)).toMatchObject({
      "email": "Enter a valid e-mail address",
      "phone-number": "Enter a mobile phone number",
      "password": "Must be at least 8 characters",
      "password-repeat": "Values do not match",
    })
  })

  it("leaves a member's own name and address alone when they update themselves, and keeps asking for contact", async () => {
    const wrapper = mount(UserForm, {
      props: {modelValue: baseModel({id: 12}), options: {updateKind: "user", includeMemberProfile: false}},
      global: {stubs},
    })

    expect(await failures(wrapper)).toMatchObject({
      "initials": "",
      "first-name": "",
      "last-name": "",
      "username": "",
      "email": "",
      "discord": "This field is required",
      "phone-number": "This field is required",
    })
    expect(box(wrapper, "privacy-consent").exists()).toBe(false)
  })

  it("does not ask for the privacy agreement when the board creates the account", () => {
    const wrapper = mount(UserForm, {
      props: {modelValue: baseModel(), options: {includeMemberProfile: true, updateKind: "board"}},
      global: {stubs},
    })

    expect(box(wrapper, "privacy-consent").exists()).toBe(false)
  })

  it("asks a new member for their date of birth and nationality, and leaves gender and student number free", async () => {
    const wrapper = mount(UserForm, {
      props: {modelValue: baseModel(), options: {includeMemberProfile: true, updateKind: "auto"}},
      global: {stubs},
    })

    expect(await failures(wrapper)).toMatchObject({
      "date-of-birth": "Date is required",
      "gender": "",
      "student-number": "",
      "privacy-consent": "You must agree to the privacy policy to create an account.",
    })
  })

  it("loads member profile once per user id without refetch loop", async () => {
    mockFindMemberProfileByUserId.mockResolvedValue({
      dateOfBirth: "2000-01-01",
      studentNumber: "s123",
      gender: "X",
      nationality: "NL",
      bhv: false,
      ehbo: false,
      version: 1,
    })

    const wrapper = mount(UserForm, {
      props: {modelValue: baseModel({id: 42}), options: {includeMemberProfile: true, updateKind: "board"}},
      global: {stubs},
    })
    await nextTick()

    expect(mockFindMemberProfileByUserId).toHaveBeenCalledTimes(1)
    expect(mockFindMemberProfileByUserId).toHaveBeenCalledWith(42)

    await wrapper.setProps({modelValue: baseModel({id: 42, discord: "updated"})})
    await nextTick()

    expect(mockFindMemberProfileByUserId).toHaveBeenCalledTimes(1)
  })

  it("merges member profile fields into modelValue on successful load", async () => {
    mockFindMemberProfileByUserId.mockResolvedValue({
      dateOfBirth: "1999-06-15",
      studentNumber: "s456",
      gender: "M",
      nationality: "DE",
      bhv: true,
      ehbo: false,
      version: 3,
    })

    const model = baseModel({id: 10})
    mount(UserForm, {
      props: {
        modelValue: model,
        "onUpdate:modelValue": (val: Record<string, unknown>) => Object.assign(model, val),
        options: {includeMemberProfile: true, updateKind: "board"},
      },
      global: {stubs},
    })
    await nextTick()
    await nextTick()

    expect(mockFindMemberProfileByUserId).toHaveBeenCalledWith(10)
    expect((model as Record<string, unknown>).memberProfile).toMatchObject({
      dateOfBirth: "1999-06-15",
      studentNumber: "s456",
      nationality: "DE",
    })
  })

  it("keeps the default profile when the account has none to read", async () => {
    mockFindMemberProfileByUserId.mockResolvedValue(null)

    const wrapper = mount(UserForm, {
      props: {modelValue: baseModel({id: 99}), options: {includeMemberProfile: true, updateKind: "board"}},
      global: {stubs},
    })
    await nextTick()

    expect(mockFindMemberProfileByUserId).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).user.memberProfile.dateOfBirth).toBe("")
  })

  it("does not show member profile fields when includeMemberProfile is false", () => {
    const wrapper = mount(UserForm, {
      props: {modelValue: baseModel({id: 5}), options: {includeMemberProfile: false, updateKind: "user"}},
      global: {stubs},
    })

    expect(box(wrapper, "date-of-birth").exists()).toBe(false)
    expect(box(wrapper, "nationality").exists()).toBe(false)
    expect(box(wrapper, "student-number").exists()).toBe(false)
  })

  describe("setting a password", () => {
    it("asks for one while the account is being created", () => {
      const wrapper = mount(UserForm, {props: {showPassword: true, modelValue: baseModel()}, global: {stubs}})

      expect(box(wrapper, "password").exists()).toBe(true)
      expect(box(wrapper, "password-repeat").exists()).toBe(true)
    })

    it("never asks for one once the account exists", () => {
      // Every update path leaves the password alone, so an empty required field
      // here would block a form that has nothing wrong with it.
      const wrapper = mount(UserForm, {props: {showPassword: true, modelValue: baseModel({id: 12})}, global: {stubs}})

      expect(box(wrapper, "password").exists()).toBe(false)
      expect(box(wrapper, "password-repeat").exists()).toBe(false)
    })

    it("never asks for one when an applicant returns on a signup token", () => {
      const wrapper = mount(UserForm, {
        props: {showPassword: true, modelValue: baseModel({id: 12}), signupToken: "sel.ver"},
        global: {stubs},
      })

      expect(box(wrapper, "password").exists()).toBe(false)
    })

    it("still lets that applicant fix their own name and username", async () => {
      const wrapper = mount(UserForm, {
        props: {showPassword: true, modelValue: baseModel({id: 12, username: "ann.vos"}), signupToken: "sel.ver"},
        global: {stubs},
      })

      // The api takes any username, dots included, so the form refuses only an empty one. The
      // address moves the confirmation link, so it changes on the confirmation step instead.
      expect(await failures(wrapper)).toMatchObject({"first-name": "This field is required", "username": "", "email": ""})
    })
  })

  // The profile is read after this form mounts. A save pressed in between used to
  // send the blanks that stand in until it lands — the account's own date of birth
  // and gender among them.
  it("saves the profile the account has, not the blanks standing in for it", async () => {
    let landProfile: (() => void) | undefined
    mockFindMemberProfileByUserId.mockReturnValue(
      new Promise((resolve) => {
        landProfile = () =>
          resolve({dateOfBirth: "1999-04-12", gender: "X", studentNumber: "s123", nationality: "NL"})
      }),
    )
    mockUpdateUser.mockResolvedValue({id: 15, version: 2})

    const wrapper = mount(UserForm, {
      props: {modelValue: filled({id: 15, memberProfile: undefined}), options: {includeMemberProfile: true, updateKind: "user"}},
      global: {stubs},
    })

    const saving = (wrapper.vm as any).save()
    landProfile!()
    await saving

    expect(mockUpdateUser).toHaveBeenCalledTimes(1)
    expect(mockUpdateUser.mock.calls[0]![1].memberProfile).toMatchObject({
      dateOfBirth: "1999-04-12",
      gender: "X",
      studentNumber: "s123",
    })
  })

  it("asks a board member moving an address to confirm it is them, and saves again once proved", async () => {
    mockUpdateUser.mockRejectedValueOnce({code: "StepUpRequired"}).mockResolvedValueOnce({id: 15, version: 2})
    const wrapper = mount(UserForm, {
      props: {modelValue: filled({id: 15}), options: {includeMemberProfile: false, updateKind: "board"}},
      global: {stubs},
    })

    await (wrapper.vm as any).save()
    const stepUp = wrapper.findComponent({name: "StepUpDialog"})
    expect(stepUp.props("modelValue")).toBe(true)

    stepUp.vm.$emit("update:modelValue", false)
    stepUp.vm.$emit("proved")
    await flushPromises()
    expect(stepUp.props("modelValue")).toBe(false)
    expect(mockUpdateUser).toHaveBeenCalledTimes(2)
  })

  it("asks the island control for a phone field", () => {
    const wrapper = mount(UserForm, {props: {modelValue: baseModel()}, global: {stubs}})

    expect(box(wrapper, "phone-number").findComponent({name: "FormControl"}).props("kind")).toBe("phone")
  })

  it("picks the Discord account from the server, and sends the member it linked", async () => {
    mockSignUp.mockResolvedValue({userId: 1, email: "a@example.com", signupToken: "t", expiresAt: "2099-01-01T00:00:00.000Z"})
    const wrapper = mount(UserForm, {
      props: {
        showPassword: true,
        modelValue: filled({discord: "Nelly B"}),
        options: {includeMemberProfile: false, createVia: "signup"},
      },
      global: {stubs},
    })
    await type(wrapper, "password-repeat", "Secret12!")

    wrapper.getComponent(DiscordMemberPicker).vm.$emit("update:discordId", "803")
    await (wrapper.vm as any).save()

    expect(mockSignUp).toHaveBeenCalledWith(expect.objectContaining({discord: "Nelly B", discordId: "803"}))
  })

  it("takes what is typed into each of its fields", async () => {
    const wrapper = mount(UserForm, {
      props: {showPassword: true, modelValue: baseModel(), options: {includeMemberProfile: true, createVia: "signup"}},
      global: {stubs},
    })
    const typed: Record<string, string> = {
      "initials": "A.", "first-name": "Ann", "prefix": "de", "last-name": "Vos", "username": "ann", "email": "ann@example.com",
      "phone-number": "+31600000000", "password": "Secret1!", "password-repeat": "Secret1!", "date-of-birth": "2000-01-02",
      "gender": "X", "student-number": "s1", "nationality": "DE",
    }
    for (const [name, value] of Object.entries(typed)) await type(wrapper, name, value)
    wrapper.getComponent(DiscordMemberPicker).vm.$emit("update:modelValue", "ann#1")
    const ticks = ["ehbo", "bhv", "name-on-rosters", "newsletter", "photo-consent", "privacy-consent"]
    for (const name of ticks) box(wrapper, name).findComponent({name: "CheckBox"}).vm.$emit("update:modelValue", name !== "newsletter")
    await nextTick()

    const user = (wrapper.vm as any).user
    expect(user).toMatchObject({
      initials: "A.", firstName: "Ann", prefix: "de", lastName: "Vos", username: "ann", email: "ann@example.com", password: "Secret1!",
      discord: "ann#1", phoneNumber: "+31600000000", newsletter: false, photoConsent: true, consentPrivacy: true,
    })
    expect(user.memberProfile).toMatchObject({
      dateOfBirth: "2000-01-02", gender: "X", studentNumber: "s1", nationality: "DE", ehbo: true, bhv: true, nameOnRosters: true,
    })
    expect((wrapper.vm as any).confirmPassword).toBe("Secret1!")
  })
})
