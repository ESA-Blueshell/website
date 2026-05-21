import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {shallowMount} from "@vue/test-utils"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"

const {
  mockStore,
  mockCreateEventSignup,
  mockUpdateEventSignUp,
  mockDeleteEventSignup,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      isLoggedIn: true,
      getLogin: {userId: 99},
      getGuestData: null as null | {accessToken: string},
    },
    commit: vi.fn(),
  },
  mockCreateEventSignup: vi.fn(),
  mockUpdateEventSignUp: vi.fn(),
  mockDeleteEventSignup: vi.fn(),
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

vi.mock("@/services/api", () => ({
  createEventSignup: mockCreateEventSignup,
  updateEventSignUp: mockUpdateEventSignUp,
  deleteEventSignup: mockDeleteEventSignup,
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
    mockCreateEventSignup.mockResolvedValue({
      data: {id: 1, eventId: 500, answers: []},
      headers: {},
    })
    mockUpdateEventSignUp.mockResolvedValue({
      data: {id: 1, eventId: 500, answers: [], version: 2},
      headers: {},
    })
    mockDeleteEventSignup.mockResolvedValue({})
  })

  it("creates sign-up for logged-in users with userId payload", async () => {
    const wrapper = shallowMount(EventSignUpForm, {
      props: {
        event: event(),
      },
    })

    await (wrapper.vm as unknown as {save: () => Promise<void>}).save()

    expect(mockCreateEventSignup).toHaveBeenCalledWith({
      path: {eventId: 500},
      body: {
        answers: [],
        userId: 99,
      },
      throwOnError: true,
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

    expect(mockUpdateEventSignUp).toHaveBeenCalledWith({
      path: {eventId: 500},
      headers: undefined,
      body: {
        answers: [],
        userId: 99,
        version: 7,
      },
      throwOnError: true,
    })
  })

  it("stores guest access token when guest sign-up succeeds", async () => {
    mockStore.getters.isLoggedIn = false
    mockCreateEventSignup.mockResolvedValue({
      data: {
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
      headers: {
        "x-guest-access-token": "guest-token",
      },
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

    expect(mockDeleteEventSignup).toHaveBeenCalledWith({
      path: {id: 44},
      headers: {"X-Guest-Access-Token": "existing-guest-token"},
      throwOnError: true,
    })
    expect(wrapper.emitted("delete:signUp")?.at(-1)).toEqual([44])
  })
})
