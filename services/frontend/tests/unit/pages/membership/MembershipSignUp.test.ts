import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import MembershipSignUp from "@/pages/membership/MembershipSignUp.vue"
import {settle} from "../helpers"

const {
  mockRouterReplace,
  mockStore,
  mockFindUserById,
  mockFindAddressById,
  mockCorrectEmail,
  mockHandleNetworkError,
  mockGoto,
} = vi.hoisted(() => ({
  mockRouterReplace: vi.fn(),
  mockStore: {
    getters: {isLoggedIn: false, getLogin: null as null | Record<string, unknown>},
    commit: vi.fn(),
  },
  mockFindUserById: vi.fn(),
  mockFindAddressById: vi.fn(),
  mockCorrectEmail: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockGoto: vi.fn(),
}))

// Filled by the page's own subscriptions, so a test can speak as the other tab.
const activationHandlers: Array<(activation: {at: number}) => void> = []
const rejectionHandlers: Array<() => void> = []

vi.mock("@/plugins/router.ts", () => ({
  default: {push: vi.fn(), replace: mockRouterReplace},
}))
vi.mock("@/plugins/store", () => ({default: mockStore}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))
vi.mock("@/plugins/goto", () => ({$goto: mockGoto}))

vi.mock("@/plugins/signupContinuation", () => ({
  readSignupToken: () => sessionStorage.getItem("signup:continuation:token") ?? undefined,
  rememberSignupToken: (token: string) =>
    sessionStorage.setItem("signup:continuation:token", token),
  forgetSignupToken: () => sessionStorage.removeItem("signup:continuation:token"),
  onAccountActivated: (handler: (activation: {at: number}) => void) => {
    activationHandlers.push(handler)
    return () => activationHandlers.splice(activationHandlers.indexOf(handler), 1)
  },
  onSignupTokenRejected: (handler: () => void) => {
    rejectionHandlers.push(handler)
    return () => rejectionHandlers.splice(rejectionHandlers.indexOf(handler), 1)
  },
}))

vi.mock("@/services/api", () => ({
  findUserById: mockFindUserById,
  findAddressById: mockFindAddressById,
  correctEmail: mockCorrectEmail,
  Role: {MEMBER: "MEMBER"},
}))

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {name: "UserForm", template: "<div />"},
}))
vi.mock("@/components/form/AddressForm.vue", () => ({
  default: {name: "AddressForm", template: "<div />"},
}))
vi.mock("@/components/form/MembershipForm.vue", () => ({
  default: {name: "MembershipForm", template: "<div />"},
}))
vi.mock("@/components/form/EmailConfirmationPanel.vue", () => ({
  default: {
    name: "EmailConfirmationPanel",
    props: {
      email: String,
      username: String,
      continuationToken: String,
      confirmationConsequence: String,
    },
    emits: ["email-corrected", "back"],
    template: "<div data-testid='email-confirm-step' />",
  },
}))
vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {name: "TopBanner", template: "<div />"},
}))

const SIGNUP_TOKEN_KEY = "signup:continuation:token"

const mountPage = async () => {
  const wrapper = shallowMount(MembershipSignUp, {
    global: {stubs: {UserForm: true, AddressForm: true, MembershipForm: true, TopBanner: true}},
  })
  await settle()
  return wrapper
}

// VStepper renders only the slot for the active step, so a stub that forwards every
// item slot is what makes the step bodies assertable at all.
const allStepsStub = {
  name: "VStepper",
  template: `<div>
    <slot name="item.1" /><slot name="item.2" /><slot name="item.3" /><slot name="item.4" />
  </div>`,
}

const mountWithStepBodies = async () => {
  const wrapper = shallowMount(MembershipSignUp, {
    global: {
      stubs: {
        VStepper: allStepsStub,
        UserForm: true,
        AddressForm: true,
        MembershipForm: true,
        TopBanner: true,
      },
    },
  })
  await settle()
  return wrapper
}

/** Reaches into the component to install stub refs, since the forms are stubbed out. */
const installRefs = (
  wrapper: Awaited<ReturnType<typeof mountPage>>,
  refs: {
    userSave?: unknown
    signupSession?: {signupToken: string} | undefined
    addressSave?: unknown
    membershipSave?: unknown
  },
) => {
  const vm = wrapper.vm as unknown as {
    userRef: unknown
    addressRef: unknown
    membershipRef: unknown
  }
  // `in` rather than ?? so an explicit null means "saving failed" instead of
  // silently falling back to a successful save.
  vm.userRef = {
    save: vi.fn().mockResolvedValue(
      "userSave" in refs ? refs.userSave : {id: 1, email: "lena@example.com"},
    ),
    signupSession: refs.signupSession,
  }
  vm.addressRef = {
    save: vi.fn().mockResolvedValue("addressSave" in refs ? refs.addressSave : {id: 2}),
  }
  vm.membershipRef = {
    save: vi.fn().mockResolvedValue("membershipSave" in refs ? refs.membershipSave : null),
  }
}

describe("MembershipSignUp page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    activationHandlers.length = 0
    rejectionHandlers.length = 0
    mockStore.getters.isLoggedIn = false
    mockStore.getters.getLogin = null
    mockFindUserById.mockResolvedValue({data: null})
    mockFindAddressById.mockResolvedValue({data: null})
    mockCorrectEmail.mockResolvedValue({data: undefined})
  })

  describe("a new applicant", () => {
    it("is shown four steps", async () => {
      const wrapper = await mountPage()

      const items = (wrapper.vm as unknown as {stepItems: Array<{title: string}>}).stepItems
      expect(items.map((i) => i.title)).toEqual([
        "Your details",
        "Address",
        "Membership",
        "Confirm email",
      ])
    })

    it("keeps the signup token handed back by the details step", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {signupSession: {signupToken: "sel.ver"}})

      await (wrapper.vm as unknown as {saveDetails: () => Promise<void>}).saveDetails()

      expect(sessionStorage.getItem(SIGNUP_TOKEN_KEY)).toBe("sel.ver")
      expect((wrapper.vm as unknown as {currentStep: number}).currentStep).toBe(2)
    })

    it("stays on the details step when saving fails", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {userSave: null})

      await (wrapper.vm as unknown as {saveDetails: () => Promise<void>}).saveDetails()

      expect((wrapper.vm as unknown as {currentStep: number}).currentStep).toBe(1)
    })

    it("stays on the address step when saving the address fails", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {addressSave: null})
      const vm = wrapper.vm as unknown as {
        currentStep: number
        saveAddressStep: () => Promise<void>
      }
      vm.currentStep = 2

      await vm.saveAddressStep()

      expect(vm.currentStep).toBe(2)
    })

    it("keeps what the address step saved, so going back does not lose it", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {addressSave: {id: 2, city: "Enschede", street: "Drienerlolaan"}})
      const vm = wrapper.vm as unknown as {
        currentStep: number
        saveAddressStep: () => Promise<void>
        address: {city?: string} | undefined
      }
      vm.currentStep = 2

      await vm.saveAddressStep()

      // The stepper unmounts the step it leaves, so the page has to hold this.
      expect(vm.address?.city).toBe("Enschede")
    })

    it("keeps what the details step saved", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {userSave: {id: 1, email: "lena@example.com", firstName: "Lena"}})
      const vm = wrapper.vm as unknown as {
        saveDetails: () => Promise<void>
        user: {firstName?: string} | undefined
      }

      await vm.saveDetails()

      expect(vm.user?.firstName).toBe("Lena")
    })

    it("advances to the membership step once the address is saved", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {})
      const vm = wrapper.vm as unknown as {
        currentStep: number
        saveAddressStep: () => Promise<void>
      }
      vm.currentStep = 2

      await vm.saveAddressStep()

      expect(vm.currentStep).toBe(3)
    })

    it("asks for confirmation when the application is submitted first", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {
        membershipSave: {emailConfirmed: false, membershipStarted: false},
      })

      await (wrapper.vm as unknown as {submitApplication: () => Promise<void>}).submitApplication()

      const vm = wrapper.vm as unknown as {currentStep: number; finished: boolean}
      expect(vm.currentStep).toBe(4)
      expect(vm.finished).toBe(false)
    })

    it("says the membership started when confirmation already happened", async () => {
      const wrapper = await mountPage()
      sessionStorage.setItem(SIGNUP_TOKEN_KEY, "sel.ver")
      installRefs(wrapper, {
        membershipSave: {emailConfirmed: true, membershipStarted: true},
      })

      await (wrapper.vm as unknown as {submitApplication: () => Promise<void>}).submitApplication()

      expect((wrapper.vm as unknown as {finished: boolean}).finished).toBe(true)
      // The session is spent, so it must not linger in storage.
      expect(sessionStorage.getItem(SIGNUP_TOKEN_KEY)).toBeNull()
    })

    it("stays on the membership step when the application is refused", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {membershipSave: null})
      const vm = wrapper.vm as unknown as {
        currentStep: number
        finished: boolean
        submitApplication: () => Promise<void>
      }
      vm.currentStep = 3

      await vm.submitApplication()

      expect(vm.currentStep).toBe(3)
      expect(vm.finished).toBe(false)
    })

    /** Puts the page in the state it reaches once the application has been sent. */
    const afterSubmitting = async () => {
      const wrapper = await mountWithStepBodies()
      const vm = wrapper.vm as unknown as {
        applicationSubmitted: boolean
        signupToken: string | undefined
        currentStep: number
        user: {email: string; username: string} | undefined
      }
      vm.applicationSubmitted = true
      vm.signupToken = "sel.ver"
      vm.user = {email: "lena@example.com", username: "lena"}
      await settle()
      return {wrapper, vm}
    }

    it("hands the confirmation panel the address and the token it must use", async () => {
      const {wrapper} = await afterSubmitting()

      const panel = wrapper.findComponent({name: "EmailConfirmationPanel"})
      expect(panel.props("continuationToken")).toBe("sel.ver")
      expect(panel.props("email")).toBe("lena@example.com")
      expect(panel.props("username")).toBe("lena")
    })

    it("keeps the corrected address the panel reports", async () => {
      const {wrapper, vm} = await afterSubmitting()

      await wrapper.findComponent({name: "EmailConfirmationPanel"})
        .vm.$emit("email-corrected", "corrected@example.com")

      expect(vm.user?.email).toBe("corrected@example.com")
    })

    it("locks the agreement once the application is in", async () => {
      const {wrapper} = await afterSubmitting()

      // The applicant may still edit, but not un-agree: the form gives way to a
      // record of the agreement, and the submit button to a way onward.
      expect(wrapper.find('[data-testid="membership-conditions-accepted"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="membership-conditions-submit-btn"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="membership-conditions-continue-btn"]').exists()).toBe(true)
    })

    it("steps back from the confirmation step like any other step", async () => {
      const {wrapper, vm} = await afterSubmitting()

      await wrapper.findComponent({name: "EmailConfirmationPanel"}).vm.$emit("back")

      // One step back, to the agreement, from where Previous reaches the address
      // and the details in turn.
      expect(vm.currentStep).toBe(3)
    })

    it("still offers the conditions form before anything is submitted", async () => {
      const wrapper = await mountWithStepBodies()

      expect(wrapper.find('[data-testid="membership-conditions-submit-btn"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="membership-conditions-accepted"]').exists()).toBe(false)
    })
  })

  describe("an applicant who is already signed in", () => {
    beforeEach(() => {
      mockStore.getters.isLoggedIn = true
      mockStore.getters.getLogin = {userId: 5, addressId: 9}
      mockFindUserById.mockResolvedValue({
        data: {id: 5, email: "lena@example.com", roles: [], version: 0},
      })
      mockFindAddressById.mockResolvedValue({data: {id: 9, city: "Enschede"}})
    })

    it("is shown three steps, with nothing to confirm", async () => {
      const wrapper = await mountPage()

      const items = (wrapper.vm as unknown as {stepItems: Array<{title: string}>}).stepItems
      expect(items.map((i) => i.title)).toEqual(["Your details", "Address", "Membership"])
    })

    it("loads the account and the address already on file", async () => {
      await mountPage()

      expect(mockFindUserById).toHaveBeenCalledWith({path: {userId: 5}, throwOnError: true})
      expect(mockFindAddressById).toHaveBeenCalledWith({path: {id: 9}})
    })

    it("finishes on the membership step because the address is already confirmed", async () => {
      const wrapper = await mountPage()
      installRefs(wrapper, {
        membershipSave: {emailConfirmed: true, membershipStarted: true},
      })

      await (wrapper.vm as unknown as {submitApplication: () => Promise<void>}).submitApplication()

      expect((wrapper.vm as unknown as {finished: boolean}).finished).toBe(true)
    })

    it("is redirected away when they are already a member", async () => {
      mockFindUserById.mockResolvedValue({
        data: {id: 5, email: "lena@example.com", roles: ["MEMBER"], version: 0},
      })

      await mountPage()
      await settle()

      expect(mockRouterReplace).toHaveBeenCalledWith("/")
      expect(mockStore.commit).toHaveBeenCalledWith(
        "setStatusSnackbarMessage",
        "you are already a member",
      )
    })
  })

  describe("what the applicant can see and press", () => {
    it("shows the confirmation panel on the last step", async () => {
      const wrapper = await mountWithStepBodies()

      expect(wrapper.findComponent({name: "EmailConfirmationPanel"}).exists()).toBe(true)
    })

    it("shows the step navigation buttons for the address and membership steps", async () => {
      const wrapper = await mountWithStepBodies()

      expect(wrapper.find('[data-testid="membership-details-next-btn"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="membership-address-back-btn"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="membership-address-next-btn"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="membership-conditions-back-btn"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="membership-conditions-submit-btn"]').exists()).toBe(true)
    })

    it("steps back from the membership step to the address step", async () => {
      const wrapper = await mountWithStepBodies()
      const vm = wrapper.vm as unknown as {currentStep: number}
      vm.currentStep = 3
      await settle()

      await wrapper.find('[data-testid="membership-conditions-back-btn"]').trigger("click")

      expect(vm.currentStep).toBe(2)
    })

    it("steps back from the address step to the details step", async () => {
      const wrapper = await mountWithStepBodies()
      const vm = wrapper.vm as unknown as {currentStep: number}
      vm.currentStep = 2
      await settle()

      await wrapper.find('[data-testid="membership-address-back-btn"]').trigger("click")

      expect(vm.currentStep).toBe(1)
    })

    it("shows the completed panel once the membership has started", async () => {
      const wrapper = await mountWithStepBodies()
      const vm = wrapper.vm as unknown as {finished: boolean}
      vm.finished = true
      await settle()

      expect(wrapper.find('[data-testid="membership-complete-panel"]').exists()).toBe(true)
      expect(wrapper.text()).toContain("You're a member")
      expect(wrapper.find('[data-testid="membership-signup-stepper"]').exists()).toBe(false)
    })
  })

  describe("when loading the signed-in applicant goes wrong", () => {
    beforeEach(() => {
      mockStore.getters.isLoggedIn = true
      mockStore.getters.getLogin = {userId: 5, addressId: 9}
    })

    it("surfaces a failure to load the account and stops", async () => {
      mockFindUserById.mockRejectedValue(new Error("boom"))

      await mountPage()

      expect(mockHandleNetworkError).toHaveBeenCalled()
      expect(mockFindAddressById).not.toHaveBeenCalled()
    })

    it("surfaces a failure to load the address", async () => {
      mockFindUserById.mockResolvedValue({data: {id: 5, email: "a@b.c", roles: [], version: 0}})
      mockFindAddressById.mockRejectedValue(new Error("boom"))

      await mountPage()

      expect(mockHandleNetworkError).toHaveBeenCalled()
    })

    it("does not look for an address when none is on file", async () => {
      mockStore.getters.getLogin = {userId: 5}
      mockFindUserById.mockResolvedValue({data: {id: 5, email: "a@b.c", roles: [], version: 0}})

      await mountPage()

      expect(mockFindAddressById).not.toHaveBeenCalled()
    })
  })

  describe("another tab finishes the signup", () => {
    // The page subscribes on mount, so the handlers it registered are what a test
    // has to fire to stand in for the other tab.
    const fireActivation = () => activationHandlers.forEach((handler) => handler({at: 1}))
    const fireRejection = () => rejectionHandlers.forEach((handler) => handler())

    it("retires the confirmation step and lets an applicant who has not applied carry on", async () => {
      const wrapper = await mountPage()

      fireActivation()
      await settle()

      const vm = wrapper.vm as unknown as {stepItems: Array<{title: string}>}
      expect(vm.stepItems.map((i) => i.title)).toEqual(["Your details", "Address", "Membership"])
      expect(mockRouterReplace).not.toHaveBeenCalledWith({name: "login"})
    })

    it("brings the applicant back off a step that no longer exists", async () => {
      const wrapper = await mountPage()
      const vm = wrapper.vm as unknown as {currentStep: number}
      vm.currentStep = 4

      fireActivation()
      await settle()

      expect(vm.currentStep).toBe(3)
    })

    it("hands over to login once the application is in, since the token is retired", async () => {
      const wrapper = await mountPage()
      sessionStorage.setItem(SIGNUP_TOKEN_KEY, "sel.ver")
      ;(wrapper.vm as unknown as {applicationSubmitted: boolean}).applicationSubmitted = true

      fireActivation()
      await settle()

      expect(mockRouterReplace).toHaveBeenCalledWith({name: "login"})
      expect(sessionStorage.getItem(SIGNUP_TOKEN_KEY)).toBeNull()
    })

    /**
     * Confirming mid-form retires the confirmation step, and the application then
     * going in is what used to reveal a button pointing straight at it. The step
     * list and that button read one condition so they cannot disagree.
     */
    it("offers no way to the confirmation step it has retired", async () => {
      const wrapper = await mountWithStepBodies()
      const vm = wrapper.vm as unknown as {applicationSubmitted: boolean}

      fireActivation()
      await settle()
      vm.applicationSubmitted = true
      await settle()

      expect(wrapper.find('[data-testid="membership-conditions-continue-btn"]').exists()).toBe(false)
    })

    it("offers that way while the confirmation step is still there", async () => {
      const wrapper = await mountWithStepBodies()
      ;(wrapper.vm as unknown as {applicationSubmitted: boolean}).applicationSubmitted = true
      await settle()

      expect(wrapper.find('[data-testid="membership-conditions-continue-btn"]').exists()).toBe(true)
    })

    it("says nothing more once the membership is finished here", async () => {
      const wrapper = await mountPage()
      ;(wrapper.vm as unknown as {finished: boolean}).finished = true

      fireActivation()
      await settle()

      expect(mockRouterReplace).not.toHaveBeenCalledWith({name: "login"})
    })

    it("gives up the token and the page when the api refuses it", async () => {
      await mountPage()
      sessionStorage.setItem(SIGNUP_TOKEN_KEY, "sel.ver")

      fireRejection()
      await settle()

      expect(sessionStorage.getItem(SIGNUP_TOKEN_KEY)).toBeNull()
      expect(mockRouterReplace).toHaveBeenCalledWith({name: "login"})
    })

    it("stops listening once the page is gone", async () => {
      const wrapper = await mountPage()
      expect(activationHandlers.length).toBe(1)

      wrapper.unmount()

      expect(activationHandlers.length).toBe(0)
      expect(rejectionHandlers.length).toBe(0)
    })
  })

  describe("leaving the page", () => {
    it("sends a new member to the homepage", async () => {
      const wrapper = await mountWithStepBodies()
      ;(wrapper.vm as unknown as {finished: boolean}).finished = true
      await settle()

      await wrapper.find('[data-testid="membership-home-btn"]').trigger("click")

      expect(mockGoto).toHaveBeenCalledWith("/")
    })

  })
})
