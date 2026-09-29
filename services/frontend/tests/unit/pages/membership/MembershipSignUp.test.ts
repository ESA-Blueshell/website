import {beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import MembershipSignUp from "@/pages/membership/MembershipSignUp.vue"
import router from "@/plugins/router.ts"
import store, {type StoredLogin} from "@/plugins/store"
import {
  apply,
  correctEmail,
  findAddressById,
  findCurrentContributionPeriod,
  findMemberProfileByUserId,
  findUserById,
  resumeSignup,
  saveAddress,
  type SignupResumeResponse,
  updateDetails,
  type UserDetailResponse,
} from "@/services/api"
import {answer, emptyAnswer} from "../../helpers/sdkAnswers"
import {mountPage} from "../../helpers/mountPage"
import {settle} from "../helpers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  resumeSignup: vi.fn(),
  updateDetails: vi.fn(),
  saveAddress: vi.fn(),
  apply: vi.fn(),
  correctEmail: vi.fn(),
  findCurrentContributionPeriod: vi.fn(),
  findUserById: vi.fn(),
  findAddressById: vi.fn(),
  findMemberProfileByUserId: vi.fn(),
}))

const SIGNUP_TOKEN_KEY = "signup:continuation:token"

const resumed: SignupResumeResponse = {
  userId: 42,
  email: "lena@example.com",
  username: "lena",
  initials: "L",
  firstName: "Lena",
  prefix: null,
  lastName: "de Vries",
  discord: "lena#1",
  phoneNumber: "0612345678",
  newsletter: true,
  photoConsent: false,
  emailConfirmed: false,
  conditionsAccepted: false,
  memberProfile: {
    dateOfBirth: "1999-02-03", studentNumber: "s123", gender: "X", nationality: "NL", bhv: false, ehbo: true, nameOnRosters: false,
  },
  address: null,
}
const onFile = {country: "NL", city: "Enschede", street: "Straat", houseNumber: "1", zipCode: "7500AA"}

const mount = () => mountPage(MembershipSignUp, {path: "/membership/signup"})

/** The steps the stepper offers, and which one it stands on, marked with a star. */
const steps = (wrapper: VueWrapper<any>) =>
  wrapper.findAll(".v-stepper-item").map((item) => {
    const title = item.find(".v-stepper-item__title").text()
    return item.classes().includes("v-stepper-item--selected") ? `${title}*` : title
  })

// The details form reads the profile of any account it holds, a resumed one included.
beforeEach(() => {
  vi.mocked(findMemberProfileByUserId).mockResolvedValue(emptyAnswer(findMemberProfileByUserId))
  vi.mocked(findCurrentContributionPeriod).mockResolvedValue(emptyAnswer(findCurrentContributionPeriod))
  store.commit("setStatusSnackbarMessage", null)
})

async function press(wrapper: VueWrapper<any>, testid: string) {
  await wrapper.get(`[data-testid="${testid}"]`).trigger("click")
  await settle()
  await settle()
}

/** The control a field's label names; the fields carry no name of their own in the DOM. */
function byLabel(wrapper: VueWrapper<any>, text: string) {
  const label = wrapper.findAll("label").find((candidate) => candidate.text().startsWith(text))
  if (!label) throw new Error(`no field labelled "${text}"`)
  return wrapper.get(`#${label.attributes("for")}`)
}

const field = (wrapper: VueWrapper<any>, testid: string) =>
  wrapper.get(`[data-testid="${testid}"] input`).element as HTMLInputElement

describe("an applicant whose tab reloaded", () => {
  beforeEach(() => {
    sessionStorage.clear()
    sessionStorage.setItem(SIGNUP_TOKEN_KEY, "sel.ver")
  })

  it("reads the signup back on the token it still holds", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, resumed))

    await mount()

    expect(resumeSignup).toHaveBeenCalledWith(expect.objectContaining({headers: {"X-Signup-Token": "sel.ver"}}))
  })

  it("starts on the details step, empty, when the api says nothing about the session", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, undefined as unknown as SignupResumeResponse))

    const wrapper = await mount()

    expect(steps(wrapper)[0]).toBe("Your details*")
    expect(field(wrapper, "user-form-username-field").value).toBe("")
  })

  it("puts the details back and carries on at the address step", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, resumed))

    const wrapper = await mount()

    expect(steps(wrapper)).toEqual(["Your details", "Address*", "Membership", "Confirm email"])
    expect(field(wrapper, "user-form-username-field").value).toBe("lena")
  })

  it("carries on at the membership step once an address is saved", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, address: onFile}))

    const wrapper = await mount()

    expect(steps(wrapper)).toContain("Membership*")
  })

  it("carries on at the confirmation step once the application is in", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, conditionsAccepted: true, address: onFile}))

    const wrapper = await mount()

    expect(steps(wrapper)).toContain("Confirm email*")
  })

  it("asks for the address first even when the conditions were already agreed to", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, conditionsAccepted: true}))

    const wrapper = await mount()

    expect(steps(wrapper)).toContain("Address*")
  })

  it("hands over to login when everything is in and the membership still did not start", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(
      answer(resumeSignup, {...resumed, conditionsAccepted: true, emailConfirmed: true, address: onFile}),
    )

    await mount()

    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe("login"), {timeout: 5000})
    expect(sessionStorage.getItem(SIGNUP_TOKEN_KEY)).toBeNull()
  })

  it("drops the confirmation step when the address was confirmed meanwhile", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, emailConfirmed: true}))

    const wrapper = await mount()

    expect(steps(wrapper).map((step) => step.replace("*", ""))).toEqual(["Your details", "Address", "Membership"])
  })

  it("says so and starts over when the token is no longer good", async () => {
    vi.mocked(resumeSignup).mockRejectedValue(new Error("gone"))

    const wrapper = await mount()

    expect(store.state.statusSnackbarMessage).toBeTruthy()
    expect(steps(wrapper)[0]).toBe("Your details*")
  })

  it("offers nothing to submit while the signup is still coming back", async () => {
    let release: (value: Awaited<ReturnType<typeof resumeSignup>>) => void = () => undefined
    vi.mocked(resumeSignup).mockReturnValue(new Promise((resolve) => {
      release = resolve
    }))

    const wrapper = await mount()
    const next = () => wrapper.get('[data-testid="membership-details-next-btn"]').attributes("disabled")

    expect(next()).toBeDefined()
    release(answer(resumeSignup, resumed))
    await settle()
    expect(wrapper.find('[data-testid="membership-address-next-btn"]').exists()).toBe(true)
  })
})

describe("an applicant who is already signed in", () => {
  const account = {
    id: 7, username: "sam", fullName: "Sam Tester", firstName: "Sam", lastName: "Tester", initials: "S",
    email: "sam@example.com", roles: ["GUEST"], enabled: true, newsletter: false, photoConsent: false,
    createdAt: "2025-01-01T00:00:00.000Z", updatedAt: "2025-01-01T00:00:00.000Z", version: 0,
  } as UserDetailResponse
  const login = {
    userId: 7, username: "sam", roles: ["GUEST"], addressId: 3,
    twoFactor: {backupCodesLeft: 0, mayTurnOff: false, offered: false, on: false, required: false},
  } as StoredLogin

  const signedIn = () => mountPage(MembershipSignUp, {path: "/membership/signup", login})

  beforeEach(() => {
    sessionStorage.clear()
    vi.mocked(findUserById).mockResolvedValue(answer(findUserById, account))
    vi.mocked(findAddressById).mockResolvedValue(answer(findAddressById, {id: 3, userId: 7, ...onFile, version: 0, createdAt: "", updatedAt: ""}))
  })

  it("is shown three steps, with nothing to confirm", async () => {
    const wrapper = await signedIn()

    expect(steps(wrapper).map((step) => step.replace("*", ""))).toEqual(["Your details", "Address", "Membership"])
  })

  it("loads the account on file into the form", async () => {
    const wrapper = await signedIn()

    expect(findUserById).toHaveBeenCalledWith({path: {userId: 7}})
    expect(field(wrapper, "user-form-first-name-field").value).toBe("Sam")
  })

  it("says the account could not be read rather than offering an empty form", async () => {
    vi.mocked(findUserById).mockResolvedValue(emptyAnswer(findUserById))

    await signedIn()

    expect(store.state.statusSnackbarMessage).toContain("could not read your account")
  })

  it("reads no address when the account has none on file", async () => {
    await mountPage(MembershipSignUp, {path: "/membership/signup", login: {...login, addressId: undefined}})

    expect(findAddressById).not.toHaveBeenCalled()
  })

  it("says so when the account cannot be read, and reads no address after it", async () => {
    vi.mocked(findUserById).mockRejectedValue(new Error("down"))

    await signedIn()

    expect(findAddressById).not.toHaveBeenCalled()
    expect(store.state.statusSnackbarMessage).toBeTruthy()
  })

  it("says so when the address on file cannot be read", async () => {
    vi.mocked(findAddressById).mockRejectedValue(new Error("down"))

    await signedIn()

    expect(store.state.statusSnackbarMessage).toBeTruthy()
  })

  it("is sent away when already a member", async () => {
    vi.mocked(findUserById).mockResolvedValue(answer(findUserById, {...account, roles: ["MEMBER"]} as UserDetailResponse))

    await signedIn()

    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe("/"), {timeout: 5000})
  })
})

describe("an applicant carrying on from where they were", () => {
  it("leaves blank what the resumed session has no answer for", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {
      ...resumed,
      discord: null,
      phoneNumber: null,
      memberProfile: {dateOfBirth: null, studentNumber: null, gender: null, nationality: null, bhv: false, ehbo: false, nameOnRosters: false},
    }))
    const wrapper = await mount()

    await press(wrapper, "membership-address-back-btn")

    expect(field(wrapper, "user-form-discord-field").value).toBe("")
    expect(field(wrapper, "user-form-student-number-field").value).toBe("")
  })

  beforeEach(() => {
    sessionStorage.clear()
    sessionStorage.setItem(SIGNUP_TOKEN_KEY, "sel.ver")
    vi.mocked(updateDetails).mockResolvedValue(emptyAnswer(updateDetails))
    vi.mocked(saveAddress).mockResolvedValue(emptyAnswer(saveAddress))
  })

  it("saves the details step on the token and moves on to the address", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, resumed))
    const wrapper = await mount()
    await press(wrapper, "membership-address-back-btn")
    expect(steps(wrapper)[0]).toBe("Your details*")

    await press(wrapper, "membership-details-next-btn")

    expect(updateDetails).toHaveBeenCalledWith(expect.objectContaining({headers: {"X-Signup-Token": "sel.ver"}}))
    expect(steps(wrapper)).toContain("Address*")
  })

  async function backToAddress() {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, address: onFile}))
    const wrapper = await mount()
    await press(wrapper, "membership-conditions-back-btn")
    return wrapper
  }

  it("saves the address on the token and moves on to the membership step", async () => {
    const wrapper = await backToAddress()

    await byLabel(wrapper, "Street").setValue("Hengelosestraat")
    await press(wrapper, "membership-address-next-btn")

    expect(saveAddress).toHaveBeenCalledWith(expect.objectContaining({
      headers: {"X-Signup-Token": "sel.ver"},
      body: expect.objectContaining({street: "Hengelosestraat", city: "Enschede"}),
    }))
    expect(steps(wrapper)).toContain("Membership*")
  })

  it("stays on the address step when saving the address fails", async () => {
    vi.mocked(saveAddress).mockRejectedValue(new Error("down"))
    const wrapper = await backToAddress()

    await byLabel(wrapper, "Street").setValue("Hengelosestraat")
    await press(wrapper, "membership-address-next-btn")

    expect(steps(wrapper)).toContain("Address*")
  })

  it("keeps the saved address when going back to it", async () => {
    const wrapper = await backToAddress()
    await byLabel(wrapper, "Street").setValue("Hengelosestraat")
    await press(wrapper, "membership-address-next-btn")

    await press(wrapper, "membership-conditions-back-btn")

    expect(steps(wrapper)).toContain("Address*")
    expect((byLabel(wrapper, "Street").element as HTMLInputElement).value).toBe("Hengelosestraat")
  })

  describe("on the membership step", () => {
    beforeEach(() => {
      vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, address: onFile}))
    })

    async function agreeAndSubmit(wrapper: VueWrapper<any>) {
      await byLabel(wrapper, "I confirm that I have read").setValue(true)
      await press(wrapper, "membership-conditions-submit-btn")
    }

    it("asks for the address confirmation when the application goes in first", async () => {
      vi.mocked(apply).mockResolvedValue(answer(apply, {emailConfirmed: false, membershipStarted: false}))
      const wrapper = await mount()

      await agreeAndSubmit(wrapper)

      expect(apply).toHaveBeenCalledWith(expect.objectContaining({body: {conditionsAccepted: true}}))
      expect(steps(wrapper)).toContain("Confirm email*")
      expect(wrapper.get('[data-testid="email-confirm-step"]').text()).toContain("lena@example.com")
    })

    it("says the membership started when the address was confirmed already", async () => {
      vi.mocked(apply).mockResolvedValue(answer(apply, {emailConfirmed: true, membershipStarted: true}))
      const wrapper = await mount()

      await agreeAndSubmit(wrapper)

      expect(wrapper.find('[data-testid="membership-complete-panel"]').exists()).toBe(true)
      expect(sessionStorage.getItem(SIGNUP_TOKEN_KEY)).toBeNull()
    })

    it("hands over to sign in when the application goes in on an account that is a member already", async () => {
      vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, emailConfirmed: true, address: onFile}))
      vi.mocked(apply).mockResolvedValue(answer(apply, {emailConfirmed: true, membershipStarted: false}))
      const wrapper = await mount()

      await agreeAndSubmit(wrapper)

      await vi.waitFor(() => expect(router.currentRoute.value.name).toBe("login"))
      expect(store.state.statusSnackbarMessage).toContain("already a member")
    })

    it("stays on the membership step when the application is refused", async () => {
      vi.mocked(apply).mockRejectedValue(new Error("refused"))
      const wrapper = await mount()

      await agreeAndSubmit(wrapper)

      expect(steps(wrapper)).toContain("Membership*")
    })
  })

  it("holds the agreement once the application is in, rather than asking again", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, conditionsAccepted: true, address: onFile}))
    const wrapper = await mount()

    await press(wrapper, "email-confirm-back-btn")

    expect(steps(wrapper)).toContain("Membership*")
    expect(wrapper.find('[data-testid="membership-conditions-accepted"]').exists()).toBe(true)
    await press(wrapper, "membership-conditions-continue-btn")
    expect(steps(wrapper)).toContain("Confirm email*")
  })

  it("drops the confirmation step when another tab confirms the address first", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, resumed))
    const wrapper = await mount()

    window.dispatchEvent(new StorageEvent("storage", {
      key: "account:activation:announced",
      newValue: JSON.stringify({at: Date.now()}),
    }))
    await settle()

    expect(steps(wrapper).map((step) => step.replace("*", ""))).toEqual(["Your details", "Address", "Membership"])
    expect(store.state.statusSnackbarMessage).toContain("confirmed")
  })

  it("gives up the signup and sends the applicant to sign in once the api refuses its token", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, address: onFile}))
    vi.mocked(saveAddress).mockRejectedValue({
      response: {status: 400, data: {code: "RecoveryTokenUnusable"}},
      config: {headers: {"X-Signup-Token": "sel.ver"}},
    })
    const wrapper = await mount()
    await press(wrapper, "membership-conditions-back-btn")

    await press(wrapper, "membership-address-next-btn")

    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe("login"))
    expect(sessionStorage.getItem(SIGNUP_TOKEN_KEY)).toBeNull()
    expect(store.state.statusSnackbarMessage).toContain("signup expired")
  })

  it("sends the applicant to sign in when another tab confirms the address after the application is in", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, conditionsAccepted: true, address: onFile}))
    await mount()

    window.dispatchEvent(new StorageEvent("storage", {
      key: "account:activation:announced",
      newValue: JSON.stringify({at: Date.now()}),
    }))

    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe("login"))
    expect(store.state.statusSnackbarMessage).toContain("membership started")
  })

  it("hands over to sign in when the application is in but the account is a member already", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, emailConfirmed: true, conditionsAccepted: true, address: onFile}))

    await mount()

    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe("login"))
    expect(store.state.statusSnackbarMessage).toContain("already a member")
  })

  it("sends the confirmation to a corrected address and names that address from then on", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, {...resumed, conditionsAccepted: true, address: onFile}))
    vi.mocked(correctEmail).mockResolvedValue(emptyAnswer(correctEmail))
    const wrapper = await mount()

    await press(wrapper, "email-confirm-correct-btn")
    await wrapper.get('[data-testid="email-confirm-address-field"] input').setValue("lena@elsewhere.com")
    await press(wrapper, "email-confirm-address-submit-btn")

    expect(correctEmail).toHaveBeenCalledWith(expect.objectContaining({
      headers: {"X-Signup-Token": "sel.ver"},
      body: {email: "lena@elsewhere.com"},
    }))
    expect(wrapper.get('[data-testid="email-confirm-step"]').text()).toContain("lena@elsewhere.com")
  })
})
