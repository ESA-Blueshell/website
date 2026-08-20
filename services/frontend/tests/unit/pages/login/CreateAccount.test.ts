import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import CreateAccount from "@/pages/login/CreateAccount.vue"

const session = {
  userId: 7,
  email: "new@example.com",
  signupToken: "sel.ver",
  expiresAt: "2099-01-01T00:00:00.000Z",
}

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {
    name: "UserForm",
    props: {signupToken: String, submitText: String},
    template: "<button data-test='submit' @click=\"$emit('submitted', true)\">{{ submitText }}</button>",
    setup: () => ({signupSession: session}),
  },
}))

vi.mock("@/components/form/EmailConfirmationPanel.vue", () => ({
  default: {
    name: "EmailConfirmationPanel",
    props: {email: String, username: String, continuationToken: String, canChangeAddress: Boolean},
    emits: ["email-corrected", "change-details", "change-address"],
    template: "<div data-test='panel' />",
  },
}))

vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {name: "TopBanner", template: "<div />"},
}))

const mountPage = () => mount(CreateAccount, {global: {stubs: {TopBanner: true}}})

const submit = async (wrapper: ReturnType<typeof mountPage>) => {
  await wrapper.get("[data-test='submit']").trigger("click")
}

describe("CreateAccount page", () => {
  it("asks for confirmation once the account exists", async () => {
    const wrapper = mountPage()

    await submit(wrapper)

    expect(wrapper.find("[data-test='panel']").exists()).toBe(true)
    expect(wrapper.find("[data-testid='create-account-form-state']").exists()).toBe(false)
  })

  it("hands the panel the continuation token so a correction can be made", async () => {
    const wrapper = mountPage()

    await submit(wrapper)

    expect(wrapper.findComponent({name: "EmailConfirmationPanel"}).props("continuationToken")).toBe("sel.ver")
  })

  it("has no address to offer, so it does not advertise one", async () => {
    const wrapper = mountPage()

    await submit(wrapper)

    expect(wrapper.findComponent({name: "EmailConfirmationPanel"}).props("canChangeAddress")).toBe(false)
  })

  it("returns to the form when the applicant wants to change their details", async () => {
    const wrapper = mountPage()
    await submit(wrapper)

    await wrapper.findComponent({name: "EmailConfirmationPanel"}).vm.$emit("change-details")

    expect(wrapper.find("[data-testid='create-account-form-state']").exists()).toBe(true)
    // The account already exists, so the form is now an edit, and it says so.
    expect(wrapper.get("[data-test='submit']").text()).toBe("Save Changes")
  })

  it("carries the token into the form so the edit is authorised", async () => {
    const wrapper = mountPage()
    await submit(wrapper)
    await wrapper.findComponent({name: "EmailConfirmationPanel"}).vm.$emit("change-details")

    expect(wrapper.findComponent({name: "UserForm"}).props("signupToken")).toBe("sel.ver")
  })

  it("goes back to the confirmation once the edit is saved", async () => {
    const wrapper = mountPage()
    await submit(wrapper)
    await wrapper.findComponent({name: "EmailConfirmationPanel"}).vm.$emit("change-details")

    await submit(wrapper)

    expect(wrapper.find("[data-test='panel']").exists()).toBe(true)
  })

  it("keeps the corrected address for the panel to show", async () => {
    const wrapper = mountPage()
    await submit(wrapper)
    const vm = wrapper.vm as unknown as {user: {email: string} | undefined}
    vm.user = {email: "typo@example.com"}

    await wrapper.findComponent({name: "EmailConfirmationPanel"})
      .vm.$emit("email-corrected", "fixed@example.com")

    expect(vm.user?.email).toBe("fixed@example.com")
  })

  it("ignores a reported correction when there is no model yet", async () => {
    const wrapper = mountPage()
    await submit(wrapper)

    await wrapper.findComponent({name: "EmailConfirmationPanel"})
      .vm.$emit("email-corrected", "fixed@example.com")

    expect(wrapper.find("[data-test='panel']").exists()).toBe(true)
  })

  it("stays on the form while nothing has been created", async () => {
    const wrapper = mountPage()

    expect(wrapper.find("[data-testid='create-account-form-state']").exists()).toBe(true)
    expect(wrapper.find("[data-test='panel']").exists()).toBe(false)
    expect(wrapper.get("[data-test='submit']").text()).toBe("Create Account")
  })
})
