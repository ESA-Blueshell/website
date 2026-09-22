import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {shallowMount} from "@vue/test-utils"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"

const {
  mockStore,
  mockSignUpForEvent,
  mockChangeOwnSignUp,
  mockWithdrawSignUp,
  mockSaveSignUpAsBoard,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockSaveSignUpAsBoard: vi.fn(),
  mockStore: {
    getters: {
      isLoggedIn: true,
      getLogin: {userId: 99},
      getGuestData: null as null | {accessToken: string},
    },
    commit: vi.fn(),
  },
  mockSignUpForEvent: vi.fn(),
  mockChangeOwnSignUp: vi.fn(),
  mockWithdrawSignUp: vi.fn(),
  mockHandleNetworkError: vi.fn(),
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

// Partial: the user domain's door loads its whole adapter surface, so the rest of the client
// has to stay real.
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findUsers: vi.fn().mockResolvedValue({data: {content: []}}),
  Role: {ANONYMOUS: "ANONYMOUS", GUEST: "GUEST", MEMBER: "MEMBER", COMMITTEE: "COMMITTEE", BOARD: "BOARD", TREASURER: "TREASURER", ADMIN: "ADMIN", SYSTEM: "SYSTEM"},
}))

vi.mock("@/domains/events", () => ({
  saveSignUpAsBoard: mockSaveSignUpAsBoard,
  signUpForEvent: mockSignUpForEvent,
  changeOwnSignUp: mockChangeOwnSignUp,
  withdrawSignUp: mockWithdrawSignUp,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

const validatingGuestFormStub = defineComponent({
  name: "GuestForm",
  setup(_, {expose}) {
    expose({validate: async () => true})
    return () => h("div")
  },
})

const validatingAnswersFormStub = defineComponent({
  name: "AnswersForm",
  setup(_, {expose}) {
    expose({validate: async () => true})
    return () => h("div")
  },
})

function event(overrides: Record<string, unknown> = {}) {
  return {
    id: 500,
    title: "Mock Event",
    signUpForm: null,
    ...overrides,
  }
}

describe("EventSignUpForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isLoggedIn = true
    mockStore.getters.getLogin = {userId: 99}
    mockStore.getters.getGuestData = null
    mockSignUpForEvent.mockResolvedValue({
      signUp: {id: 1, eventId: 500, answers: []},
      guestAccessToken: null,
    })
    mockChangeOwnSignUp.mockResolvedValue({
      signUp: {id: 1, eventId: 500, answers: [], version: 2},
      guestAccessToken: null,
    })
    mockWithdrawSignUp.mockResolvedValue(undefined)
  })

  it("creates sign-up for logged-in users with userId payload", async () => {
    const wrapper = shallowMount(EventSignUpForm, {
      props: {
        event: event(),
      },
    })

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockSignUpForEvent).toHaveBeenCalledWith(500, {
      answers: [],
      userId: 99,
    })
    expect(wrapper.emitted("update:signUp")?.length).toBe(1)
  })

  it("updates existing sign-up with versioned payload", async () => {
    const wrapper = shallowMount(EventSignUpForm, {
      props: {
        event: event(),
        initialSignUp: {
          id: 44,
          version: 7,
          answers: [],
        },
      },
    })

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockChangeOwnSignUp).toHaveBeenCalledWith(
      500,
      {answers: [], userId: 99, version: 7},
      null,
    )
  })

  it("stores guest access token when guest sign-up succeeds", async () => {
    mockStore.getters.isLoggedIn = false
    mockSignUpForEvent.mockResolvedValue({
      signUp: {
        id: 55,
        eventId: 500,
        answers: [],
        guest: {
          name: "Guest",
          discord: "guest#1234",
          email: "guest@example.com",
          phoneNumber: "+31612345678",
        },
      },
      guestAccessToken: "guest-token",
    })

    const wrapper = shallowMount(EventSignUpForm, {
      props: {
        event: event({signUpForm: {questions: []}}),
      },
      global: {
        stubs: {
          GuestForm: validatingGuestFormStub,
          AnswersForm: validatingAnswersFormStub,
        },
      },
    })

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockStore.commit).toHaveBeenCalledWith("saveGuestData", {
      name: "Guest",
      discord: "guest#1234",
      email: "guest@example.com",
      phoneNumber: "+31612345678",
      accessToken: "guest-token",
    })
  })

  it("keeps the token it already holds where the change answered with none", async () => {
    mockStore.getters.isLoggedIn = false
    mockStore.getters.getGuestData = {accessToken: "held-token"}
    mockChangeOwnSignUp.mockResolvedValue({
      signUp: {
        id: 55,
        eventId: 500,
        answers: [],
        guest: {
          name: "Guest",
          discord: "guest#1234",
          email: "guest@example.com",
          phoneNumber: "+31612345678",
        },
      },
      guestAccessToken: null,
    })

    const wrapper = shallowMount(EventSignUpForm, {
      props: {
        event: event({signUpForm: {questions: []}}),
        initialSignUp: {id: 55, version: 3, answers: []},
      },
      global: {
        stubs: {
          GuestForm: validatingGuestFormStub,
          AnswersForm: validatingAnswersFormStub,
        },
      },
    })

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockStore.commit).toHaveBeenCalledWith(
      "saveGuestData",
      expect.objectContaining({accessToken: "held-token"}),
    )
  })

  it("deletes existing sign-up and emits delete event", async () => {
    mockStore.getters.getGuestData = {accessToken: "existing-guest-token"}

    const wrapper = shallowMount(EventSignUpForm, {
      props: {
        event: event(),
        initialSignUp: {id: 44, version: 1, answers: []},
      },
    })

    const deleteButton = wrapper.find("[data-testid='event-signup-delete-btn']")
    expect(deleteButton.exists()).toBe(true)
    await deleteButton.trigger("click")

    expect(mockWithdrawSignUp).toHaveBeenCalledWith(44, "existing-guest-token")
    expect(wrapper.emitted("delete:signUp")?.at(-1)).toEqual([44])
  })

  describe("board edit", () => {
    beforeEach(() => {
      mockSaveSignUpAsBoard.mockResolvedValue({id: 44, version: 8, answers: []})
    })

    it("saves an account sign-up by its own id, sending no guest details", async () => {
      const wrapper = shallowMount(EventSignUpForm, {
        props: {
          event: event(),
          boardEdit: true,
          initialSignUp: {id: 44, version: 7, answers: [], user: {id: 3, fullName: "Ada"}},
        },
      })

      await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

      expect(mockSaveSignUpAsBoard).toHaveBeenCalledWith(44, {answers: [], version: 7})
      expect(mockChangeOwnSignUp).not.toHaveBeenCalled()
      expect(wrapper.emitted("update:signUp")?.length).toBe(1)
    })

    it("sends the guest's own details on a guest sign-up", async () => {
      const wrapper = shallowMount(EventSignUpForm, {
        props: {
          event: event(),
          boardEdit: true,
          initialSignUp: {
            id: 45,
            version: 2,
            answers: [],
            guest: {
              name: "Guest Gordon",
              discord: "gordon#0001",
              email: "gordon@example.com",
              phoneNumber: "0611111111",
            },
          },
        },
        global: {
          stubs: {
            GuestForm: validatingGuestFormStub,
            AnswersForm: validatingAnswersFormStub,
          },
        },
      })

      await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

      expect(mockSaveSignUpAsBoard).toHaveBeenCalledWith(45, {
        answers: [],
        version: 2,
        guest: {
          name: "Guest Gordon",
          discord: "gordon#0001",
          email: "gordon@example.com",
          phoneNumber: "0611111111",
        },
      })
    })
  })

  it("moves a guest sign-up onto an account, sending no guest details", async () => {
    const wrapper = shallowMount(EventSignUpForm, {
      props: {
        event: event(),
        boardEdit: true,
        initialSignUp: {
          id: 46,
          version: 1,
          answers: [],
          guest: {name: "Guest", discord: "g#1", email: "g@example.com", phoneNumber: "06"},
        },
      },
      global: {stubs: {AnswersForm: validatingAnswersFormStub}},
    })
    ;(wrapper.vm as unknown as {reassignTo: number | undefined}).reassignTo = 9

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockSaveSignUpAsBoard).toHaveBeenCalledWith(46, {answers: [], version: 1, userId: 9})
  })

  it("reports a board save the api refused", async () => {
    mockSaveSignUpAsBoard.mockRejectedValueOnce(new Error("409"))
    const wrapper = shallowMount(EventSignUpForm, {
      props: {
        event: event(),
        boardEdit: true,
        initialSignUp: {id: 47, version: 1, answers: [], user: {id: 3, fullName: "Ada"}},
      },
    })

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(wrapper.emitted("update:signUp")).toBeUndefined()
  })

  it("does nothing when a board save arrives with no sign-up behind it", async () => {
    const wrapper = shallowMount(EventSignUpForm, {props: {event: event(), boardEdit: true}})

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockSaveSignUpAsBoard).not.toHaveBeenCalled()
  })

  it("offers the picker on a guest sign-up alone", async () => {
    const guest = {name: "Guest", discord: "g#1", email: "g@example.com", phoneNumber: "06"}
    const withGuest = shallowMount(EventSignUpForm, {
      props: {event: event(), boardEdit: true, initialSignUp: {id: 48, version: 1, answers: [], guest}},
    })
    expect(withGuest.findComponent({name: "UserPicker"}).exists()).toBe(true)

    const withAccount = shallowMount(EventSignUpForm, {
      props: {
        event: event(),
        boardEdit: true,
        initialSignUp: {id: 49, version: 1, answers: [], user: {id: 3, fullName: "Ada"}},
      },
    })
    expect(withAccount.findComponent({name: "UserPicker"}).exists()).toBe(false)
  })

  it("takes the account the picker reports and saves the move", async () => {
    const guest = {name: "Guest", discord: "g#1", email: "g@example.com", phoneNumber: "06"}
    const wrapper = shallowMount(EventSignUpForm, {
      props: {event: event(), boardEdit: true, initialSignUp: {id: 50, version: 5, answers: [], guest}},
      global: {stubs: {AnswersForm: validatingAnswersFormStub}},
    })

    await wrapper.findComponent({name: "UserPicker"}).vm.$emit("update:modelValue", 9)
    expect((wrapper.vm as unknown as {reassignTo: number}).reassignTo).toBe(9)

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockSaveSignUpAsBoard).toHaveBeenCalledWith(50, {answers: [], version: 5, userId: 9})
  })
})
