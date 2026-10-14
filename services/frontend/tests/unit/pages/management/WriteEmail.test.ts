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
  listSendingAddresses: vi.fn(),
  findSenderNames: vi.fn(),
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
    api.findReach.mockResolvedValue({status: 200, data: {recipients: 217, withoutEmail: 3, notActivated: 2}})
    api.sendWrittenEmail.mockResolvedValue({status: 200, data: {sent: 215}})
    api.sendTestEmail.mockResolvedValue({status: 200, data: {sent: 1}})
    api.render.mockResolvedValue({status: 200, data: {subject: "LAN night", html: "<p>Hi</p>"}})
    api.findUsers.mockResolvedValue({status: 200, data: {content: [aUser({id: 7, fullName: "Ann Vos", username: "ann"})]}})
    api.listSendingAddresses.mockResolvedValue({status: 200, data: []})
    api.findSenderNames.mockResolvedValue({status: 200, data: ["Blueshell"]})
  })

  afterEach(() => {
    vi.useRealTimers()
    unmountAll(wrappers, "WriteEmailPage")
  })

  it("counts who it reaches and who is left out, previews it as the api renders it, and sends it", async () => {
    const wrapper = await mount()
    await fill(wrapper)

    expect(api.findReach).toHaveBeenLastCalledWith({body: {to: [{kind: "COHORT", id: "ACTIVE_MEMBERS:4"}, {kind: "PERSON", id: "7"}]}})
    expect(wrapper.get('[data-testid="write-reach"]').text())
      .toBe("Reaches 217 people. 3 have no email address and 2 never activated their account, so they get nothing.")
    expect(wrapper.get('[data-testid="write-send"]').text()).toBe("Send to 217 people")

    await vi.advanceTimersByTimeAsync(600)
    await settle()
    expect(api.render).toHaveBeenCalledWith({body: {subject: "LAN night", message: "**Hi** 🎮", recipientName: "Member"}})
    expect(wrapper.get('[data-testid="write-preview"]').attributes("srcdoc")).toContain("Hi")

    wrapper.findComponent({name: "ReplyToPicker"}).findComponent({name: "SearchPicker"}).vm.$emit("pick", "writer@b.nl")
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(api.sendTestEmail).toHaveBeenCalledWith({body: expect.objectContaining({replyTo: "writer@b.nl"})})
    expect(wrapper.get('[data-testid="write-said"]').text()).toContain("on its way to you")

    await wrapper.get('[data-testid="write-send"]').trigger("click")
    await settle()
    expect(api.sendWrittenEmail).toHaveBeenCalledWith({body: {
      to: [{kind: "COHORT", id: "ACTIVE_MEMBERS:4"}, {kind: "PERSON", id: "7"}], subject: "LAN night", message: "**Hi** 🎮", replyTo: "writer@b.nl",
      from: undefined, fromName: "Blueshell",
    }})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Queued 215 emails.")
    expect(mockPush).toHaveBeenCalledWith("/management/mail/sent")
  })

  it("says why a send was refused, takes an addressee off, and keeps quiet without a subject", async () => {
    api.sendWrittenEmail.mockResolvedValue({status: 400, error: {code: "NobodyToWrite"}})
    api.findReach.mockResolvedValue({status: 200, data: {recipients: 1, withoutEmail: 1, notActivated: 0}})
    const wrapper = await mount()
    await fill(wrapper)

    expect(wrapper.get('[data-testid="write-reach"]').text()).toBe("Reaches 1 person. 1 has no email address, so they get nothing.")
    await wrapper.get('[data-testid="write-send"]').trigger("click")
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

  it("sends under the address's own name, the site's, or one the writer types", async () => {
    const events = {id: 5, address: "events@b.nl", displayName: "Events", host: "smtp.b.nl", port: 587, security: "STARTTLS", isDefault: true, loginKept: true}
    api.listSendingAddresses.mockResolvedValue({status: 200, data: [events]})
    const wrapper = await mount()
    await fill(wrapper)

    const names = wrapper.findComponent({name: "SenderNamePicker"})
    expect(names.props("offered")).toEqual(["Events", "Blueshell"])
    expect(names.props("modelValue")).toBe("Events")
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(api.sendTestEmail.mock.lastCall![0].body.fromName).toBeUndefined()

    names.vm.$emit("update:modelValue", "Events team")
    await settle()
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(api.sendTestEmail).toHaveBeenLastCalledWith({body: expect.objectContaining({from: 5, fromName: "Events team"})})
  })

  it("says when the sending addresses could not be loaded, and that the email goes out from the default one", async () => {
    api.listSendingAddresses.mockResolvedValue({status: 500, error: {}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="write-from-unread"]').text())
      .toBe("The sending addresses could not be loaded, so this email goes out from the site's default address.")
    expect(wrapper.find('[data-testid="write-from"]').exists()).toBe(false)
  })

  it("goes out from the default sending address unless another is picked, and says when it is gone", async () => {
    const address = (id: number, displayName: string, isDefault: boolean) =>
      ({id, address: `${displayName.toLowerCase()}@b.nl`, displayName, host: "smtp.b.nl", port: 587, security: "STARTTLS", isDefault, loginKept: true})
    api.listSendingAddresses.mockResolvedValue({status: 200, data: [address(3, "Board", false), address(5, "Events", true)]})
    const wrapper = await mount()
    await fill(wrapper)

    const from = wrapper.get('[data-testid="write-from"]').findComponent({name: "SearchPicker"})
    expect((from.props("options") as Array<{key: string}>).map((one) => one.key)).toEqual(["5", "3"])
    expect(from.props("selectedKey")).toBe("5")
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(api.sendTestEmail).toHaveBeenLastCalledWith({body: expect.objectContaining({from: 5})})

    from.vm.$emit("pick", "3")
    api.sendWrittenEmail.mockResolvedValue({status: 400, error: {code: "SendingAddressGone"}})
    await wrapper.get('[data-testid="write-send"]').trigger("click")
    await settle()
    expect(api.sendWrittenEmail).toHaveBeenLastCalledWith({body: expect.objectContaining({from: 3})})
    expect(wrapper.get('[data-testid="write-failure"]').text()).toBe("The address it was to go out from is removed. Pick another under From.")
  })

  it("says why a test was refused, and says which lists could not be read", async () => {
    api.findUsers.mockRejectedValue(new Error("500"))
    api.findAudiences.mockResolvedValue({status: 500, error: {}})
    api.sendTestEmail.mockResolvedValue({status: 400, error: {code: "MessageMissing"}})
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="write-read-failure"]').text())
      .toBe("The groups and the people could not be loaded, so only roles can be added. Reload the page to try again.")
    await fill(wrapper)
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="write-failure"]').text()).toBe("Write a message.")

    api.sendTestEmail.mockResolvedValue({status: 400, error: {code: "SubjectMissing"}})
    await wrapper.get('[data-testid="write-test"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="write-failure"]').text()).toBe("Give the email a subject.")
  })

  it("names each refusal of the subject, the message and a test", async () => {
    const wrapper = await mount()
    await fill(wrapper)
    const said = async (code: string) => {
      api.sendTestEmail.mockResolvedValue({status: 400, error: {code, max: {SubjectTooLong: 255, SenderNameInvalid: 128}[code] ?? 50000}})
      await wrapper.get('[data-testid="write-test"]').trigger("click")
      await settle()
      return wrapper.get('[data-testid="write-failure"]').text()
    }
    expect(await said("SubjectTooLong")).toBe("Make the subject shorter: at most 255 characters.")
    expect(await said("SubjectNotOneLine")).toBe("Write the subject on one line.")
    expect(await said("MessageTooLong")).toBe("Make the message shorter: at most 50000 characters.")
    expect(await said("SenderNameInvalid")).toBe("Give a sender name of one line, at most 128 characters.")
    expect(await said("WriterWithoutAddress")).toBe("Your account has no email address, so a test has nowhere to go. Add one to your account first.")
  })

  it("does not send when Enter is pressed in the subject", async () => {
    const wrapper = await mount()
    await fill(wrapper)
    await wrapper.get("form").trigger("submit")
    await settle()
    expect(api.sendWrittenEmail).not.toHaveBeenCalled()
  })

  it("shows the count for who is chosen now, whatever order the answers come in", async () => {
    let slow: (value: unknown) => void = () => undefined
    api.findReach.mockReturnValueOnce(new Promise((done) => { slow = done }))
    const wrapper = await mount()
    const picker = wrapper.findComponent({name: "ChipPicker"})
    picker.vm.$emit("add", ["COHORT:ACTIVE_MEMBERS:4"])
    await settle()
    picker.vm.$emit("remove", "COHORT:ACTIVE_MEMBERS:4")
    await settle()
    slow({status: 200, data: {recipients: 217, withoutEmail: 0, notActivated: 0}})
    await settle()
    expect(wrapper.find('[data-testid="write-reach"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="write-send"]').attributes("disabled")).toBeDefined()

    api.findReach.mockReturnValueOnce(new Promise((done) => { slow = done }))
    api.findReach.mockResolvedValueOnce({status: 200, data: {recipients: 218, withoutEmail: 0, notActivated: 0}})
    picker.vm.$emit("add", ["COHORT:ACTIVE_MEMBERS:4"])
    await settle()
    picker.vm.$emit("add", ["PERSON:7"])
    await settle()
    slow({status: 200, data: {recipients: 217, withoutEmail: 0, notActivated: 0}})
    await settle()
    expect(wrapper.get('[data-testid="write-reach"]').text()).toBe("Reaches 218 people.")
  })

  it("says when the count cannot be read, and sends nothing then", async () => {
    api.findReach.mockResolvedValue({status: 500, error: {}})
    const wrapper = await mount()
    await fill(wrapper)
    expect(wrapper.get('[data-testid="write-reach"]').text()).toBe("Could not count who this reaches. Add or remove someone to try again.")
    expect(wrapper.get('[data-testid="write-send"]').attributes("disabled")).toBeDefined()
  })

  it("keeps the preview to what is written now, and says when it cannot be drawn", async () => {
    let slow: (value: unknown) => void = () => undefined
    api.render.mockReturnValueOnce(new Promise((done) => { slow = done }))
    const wrapper = await mount()
    await fill(wrapper)
    await vi.advanceTimersByTimeAsync(600)
    wrapper.findComponent({name: "TextInput"}).vm.$emit("update:modelValue", "")
    await vi.advanceTimersByTimeAsync(600)
    slow({status: 200, data: {subject: "LAN night", html: "<p>old</p>"}})
    await settle()
    expect(wrapper.find('[data-testid="write-preview"]').exists()).toBe(false)

    api.render.mockResolvedValue({status: 500, error: {}})
    wrapper.findComponent({name: "TextInput"}).vm.$emit("update:modelValue", "LAN night")
    await vi.advanceTimersByTimeAsync(600)
    await settle()
    expect(wrapper.get('[data-testid="write-preview-empty"]').text()).toBe("The preview could not be drawn. Change the message to try again.")
  })
})
