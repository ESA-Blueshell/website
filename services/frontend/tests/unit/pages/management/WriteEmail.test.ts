import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import WriteEmail from "@/pages/management/WriteEmail.vue"
import {aUser} from "../../helpers/apiFixtures"
import {mountInApp, settle, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findAudiences: vi.fn(),
  findReplyToOptions: vi.fn(),
  findReach: vi.fn(),
  sendWrittenEmail: vi.fn(),
  sendTestEmail: vi.fn(),
  render: vi.fn(),
  findUsers: vi.fn(),
}))
const {mockPush, mockStore} = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true} as Record<string, unknown>},
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRouter: () => ({push: mockPush}),
}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

describe("writing an email", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(WriteEmail)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  const fill = async (wrapper: VueWrapper) => {
    wrapper.findComponent({name: "ChipPicker"}).vm.$emit("add", ["COHORT:ACTIVE_MEMBERS:4", "PERSON:7", "nonsense"])
    wrapper.findComponent({name: "TextInput"}).vm.$emit("update:modelValue", "LAN night")
    wrapper.findComponent({name: "MarkdownEditor"}).vm.$emit("update:modelValue", "**Hi** 🎮")
    await settle()
  }

  beforeEach(() => {
    vi.useFakeTimers({shouldAdvanceTime: true})
    vi.clearAllMocks()
    api.findAudiences.mockResolvedValue({status: 200, data: [{key: "ACTIVE_MEMBERS:4", label: "Active members 2026-2027"}]})
    api.findReplyToOptions.mockResolvedValue({status: 200, data: ["board@b.nl", "writer@b.nl"]})
    api.findReach.mockResolvedValue({status: 200, data: {recipients: 217, withoutEmail: 3}})
    api.sendWrittenEmail.mockResolvedValue({status: 200, data: {sent: 217}})
    api.sendTestEmail.mockResolvedValue({status: 200, data: {sent: 1}})
    api.render.mockResolvedValue({status: 200, data: {subject: "LAN night", html: "<p>Hi</p>"}})
    api.findUsers.mockResolvedValue({status: 200, data: {content: [aUser({id: 7, fullName: "Ann Vos", username: "ann"})]}})
  })

  afterEach(() => {
    vi.useRealTimers()
    unmountAll(wrappers, "WriteEmailPage")
  })

  it("counts who it reaches and who is left out, previews it as the api renders it, and sends it", async () => {
    const wrapper = await mount()
    await fill(wrapper)

    expect(api.findReach).toHaveBeenLastCalledWith({body: {to: [{kind: "COHORT", id: "ACTIVE_MEMBERS:4"}, {kind: "PERSON", id: "7"}]}})
    expect(wrapper.get('[data-testid="write-reach"]').text()).toContain("Reaches 217 people; 3 without an email address are left out")
    expect(wrapper.get('[data-testid="write-send"]').text()).toBe("Send to 217 people")

    await vi.advanceTimersByTimeAsync(600)
    await settle()
    expect(api.render).toHaveBeenCalledWith({body: {subject: "LAN night", message: "**Hi** 🎮", recipientName: "Member"}})
    expect(wrapper.get('[data-testid="write-preview"]').attributes("srcdoc")).toContain("Hi")

    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "writer@b.nl")
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(api.sendTestEmail).toHaveBeenCalledWith({body: expect.objectContaining({replyTo: "writer@b.nl"})})
    expect(wrapper.get('[data-testid="write-said"]').text()).toContain("on its way to you")

    await wrapper.get("form").trigger("submit")
    await settle()
    expect(api.sendWrittenEmail).toHaveBeenCalledWith({body: {
      to: [{kind: "COHORT", id: "ACTIVE_MEMBERS:4"}, {kind: "PERSON", id: "7"}], subject: "LAN night", message: "**Hi** 🎮", replyTo: "writer@b.nl",
    }})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Queued 217 emails.")
    expect(mockPush).toHaveBeenCalledWith("/management/mail/sent")
  })

  it("says why a send was refused, takes an addressee off, and keeps quiet without a subject", async () => {
    api.sendWrittenEmail.mockResolvedValue({status: 400, error: {code: "NobodyToWrite"}})
    api.findReach.mockResolvedValue({status: 200, data: {recipients: 1, withoutEmail: 0}})
    const wrapper = await mount()
    await fill(wrapper)

    expect(wrapper.get('[data-testid="write-reach"]').text()).toBe("Reaches 1 person.")
    await wrapper.get("form").trigger("submit")
    await settle()
    expect(wrapper.get('[data-testid="write-failure"]').text()).toContain("Nobody it is addressed to has an email address")

    wrapper.findComponent({name: "ChipPicker"}).vm.$emit("remove", "PERSON:7")
    wrapper.findComponent({name: "ChipPicker"}).vm.$emit("remove", "COHORT:ACTIVE_MEMBERS:4")
    await settle()
    expect(wrapper.find('[data-testid="write-reach"]').exists()).toBe(false)
    wrapper.findComponent({name: "TextInput"}).vm.$emit("update:modelValue", " ")
    await vi.advanceTimersByTimeAsync(600)
    await settle()
    expect(wrapper.find('[data-testid="write-preview-empty"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="write-test"]').attributes("disabled")).toBeDefined()
  })

  it("says why a test was refused, and still writes when the people list cannot be read", async () => {
    api.findUsers.mockResolvedValue({status: 500, error: {}})
    api.sendTestEmail.mockResolvedValue({status: 400, error: {code: "MessageMissing"}})
    const wrapper = await mount()
    await fill(wrapper)
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="write-failure"]').text()).toBe("Write a message.")

    api.sendTestEmail.mockResolvedValue({status: 400, error: {code: "SubjectMissing"}})
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="write-failure"]').text()).toBe("Give the email a subject.")
  })
})
