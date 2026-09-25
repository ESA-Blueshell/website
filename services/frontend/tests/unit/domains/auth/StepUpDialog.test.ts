import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import StepUpDialog from "@/domains/auth/components/StepUpDialog.vue"
import {settle} from "../../pages/helpers"

const {mockStepUp} = vi.hoisted(() => ({mockStepUp: vi.fn()}))
vi.mock("@/domains/auth/adapters/auth", () => ({stepUp: mockStepUp}))

const inline = {global: {stubs: {VDialog: {name: "VDialog", template: "<div><slot /></div>"}}}}
const open = (twoFactorOn: boolean) => mount(StepUpDialog, {...inline, props: {modelValue: false, twoFactorOn}})

describe("the step-up dialog", () => {
  beforeEach(() => vi.clearAllMocks())

  it("proves the sign-in with a code, or a backup code, and closes", async () => {
    mockStepUp.mockResolvedValue({ok: true})
    const wrapper = open(true)
    await wrapper.setProps({modelValue: true})
    expect(wrapper.text()).toContain("Enter the code from your authenticator app.")

    await wrapper.find("button.v-btn--variant-text").trigger("click")
    expect(wrapper.text()).toContain("Enter one of your backup codes.")
    await wrapper.find("[data-testid=step-up-field] input").setValue(" abcde-fghjk ")
    await wrapper.find("form").trigger("submit")
    await settle()

    expect(mockStepUp).toHaveBeenCalledWith({code: "abcde-fghjk"})
    expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
    expect(wrapper.emitted("proved")).toHaveLength(1)
  })

  it("asks for the password without two-factor, and shows a refusal", async () => {
    mockStepUp.mockResolvedValue({ok: false, reason: "That password is not right."})
    const wrapper = open(false)
    await wrapper.setProps({modelValue: true})
    expect(wrapper.text()).toContain("Enter your password.")

    await wrapper.find("[data-testid=step-up-field] input").setValue("wrong")
    await wrapper.find("form").trigger("submit")
    await settle()

    expect(mockStepUp).toHaveBeenCalledWith({password: "wrong"})
    expect(wrapper.text()).toContain("That password is not right.")
    expect(wrapper.emitted("proved")).toBeUndefined()

    await wrapper.setProps({modelValue: false})
    await wrapper.setProps({modelValue: true})
    expect(wrapper.text()).not.toContain("That password is not right.")
  })

  it("closes on cancel, or when the dialog asks to", async () => {
    const wrapper = open(false)

    const cancel = wrapper.findAll("button").find(button => button.text() === "Cancel")
    await cancel?.trigger("click")
    wrapper.findComponent({name: "VDialog"}).vm.$emit("update:modelValue", false)

    expect(wrapper.emitted("update:modelValue")).toEqual([[false], [false]])
  })
})
