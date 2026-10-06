import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import SendingAddresses from "@/pages/management/SendingAddresses.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  listSendingAddresses: vi.fn(),
  addSendingAddress: vi.fn(),
  setSendingAddress: vi.fn(),
  removeSendingAddress: vi.fn(),
}))
const mockStore = vi.hoisted(() => ({commit: vi.fn(), getters: {isAdmin: true} as Record<string, unknown>}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const events = {
  id: 5, address: "events@b.nl", displayName: "Events", host: "smtp.b.nl", port: 587, security: "STARTTLS", isDefault: true, loginKept: true,
}
const board = {...events, id: 3, address: "board@b.nl", displayName: "Board", host: "mail.b.nl", port: 465, security: "SSL", isDefault: false, loginKept: false}

const inBody = (testid: string) => new DOMWrapper(document.body.querySelector(`[data-testid="${testid}"]`)!)
const type = async (testid: string, value: string) => {
  await inBody(testid).setValue(value)
}

describe("the sending addresses page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(SendingAddresses, {attachTo: document.body})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isAdmin = true
    api.listSendingAddresses.mockResolvedValue({status: 200, data: [board, events]})
    api.addSendingAddress.mockResolvedValue({status: 201, data: {...events, id: 9, address: "lan@b.nl"}})
    api.setSendingAddress.mockResolvedValue({status: 200, data: events})
    api.removeSendingAddress.mockResolvedValue({status: 204, data: undefined})
  })

  afterEach(() => {
    unmountAll(wrappers, "SendingAddressesPage")
    document.body.innerHTML = ""
  })

  it("lists each address with its server, the default and one without a login, and lets the board only read", async () => {
    mockStore.getters.isAdmin = false
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="sending-address-5"]').text()).toContain("smtp.b.nl:587 · STARTTLS")
    expect(wrapper.get('[data-testid="sending-address-5"]').text()).toContain("Default")
    expect(wrapper.get('[data-testid="sending-address-3"]').text()).toContain("mail.b.nl:465 · SSL/TLS")
    expect(wrapper.get('[data-testid="sending-address-3"]').text()).toContain("No login is kept")
    expect(wrapper.find('[data-testid="sending-addresses-add"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="sending-address-edit-5"]').exists()).toBe(false)
    expect(await sortByEveryHead(wrapper)).toBe(3)
  })

  it("adds an address with its login, the port following the security, and says why the server refused", async () => {
    api.listSendingAddresses.mockResolvedValue({status: 200, data: []})
    const wrapper = await mount()
    expect(wrapper.get('[data-testid="sending-addresses-empty"]').text()).toContain("No addresses are added")

    await wrapper.get('[data-testid="sending-addresses-add"]').trigger("click")
    await settle()
    await type("sending-address-address-input", "lan@b.nl")
    await type("sending-address-name-input", "LanCie")
    await type("sending-address-host-input", "smtp.b.nl")
    expect(inBody("sending-address-save").attributes("disabled")).toBeDefined()
    await type("sending-address-username-input", "lan")
    await type("sending-address-password-input", "secret")
    await inBody("sending-address-security-SSL").setValue(true)
    expect((inBody("sending-address-port-input").element as HTMLInputElement).value).toBe("465")
    await type("sending-address-port-input", "2465")
    wrapper.findComponent({name: "RadioGroup"}).vm.$emit("update:modelValue", null)

    api.addSendingAddress.mockResolvedValueOnce({status: 409, error: {code: "SendingAddressTaken", address: "lan@b.nl"}})
    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(inBody("sending-address-failure").text()).toBe("lan@b.nl is a sending address already.")
    api.addSendingAddress.mockResolvedValueOnce({status: 400, error: {code: "SmtpLoginRefused", reason: "535 Authentication failed"}})
    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(inBody("sending-address-failure").text()).toBe("The SMTP server refused the login: 535 Authentication failed")

    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(api.addSendingAddress).toHaveBeenLastCalledWith({body: {
      address: "lan@b.nl", displayName: "LanCie", host: "smtp.b.nl", port: 2465, security: "SSL", isDefault: true, username: "lan", password: "secret",
    }})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "lan@b.nl is saved.")
    expect(api.listSendingAddresses).toHaveBeenCalledTimes(2)
  })

  it("edits an address keeping its login, and asks for the login again once the server moves", async () => {
    const wrapper = await mount()
    await wrapper.get('[data-testid="sending-address-edit-5"]').trigger("click")
    await settle()

    expect(document.body.textContent).toContain("Leave the username and password empty to keep the login there is")
    await type("sending-address-name-input", "Events committee")
    await inBody("sending-address-default").setValue(false)
    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(api.setSendingAddress).toHaveBeenLastCalledWith({path: {id: 5}, body: expect.objectContaining({
      displayName: "Events committee", host: "smtp.b.nl", isDefault: false, username: undefined, password: undefined,
    })})

    await wrapper.get('[data-testid="sending-address-edit-5"]').trigger("click")
    await settle()
    await type("sending-address-host-input", "relay.example.nl")
    expect(document.body.textContent).toContain("The server changed, so fill in the login for it")
    expect(inBody("sending-address-save").attributes("disabled")).toBeDefined()
    await type("sending-address-username-input", "events")
    await type("sending-address-password-input", "new")
    api.setSendingAddress.mockResolvedValueOnce({status: 400, error: {code: "SmtpNeedsEncryption", host: "relay.example.nl"}})
    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(inBody("sending-address-failure").text()).toContain("relay.example.nl is not on the site's own network")
    for (const [code, said] of [["SendingAddressNeedsLogin", "Fill in the SMTP username and password."], ["SendingAddressNotFound", "That sending address is no longer there."]]) {
      api.setSendingAddress.mockResolvedValueOnce({status: 400, error: {code}})
      await inBody("sending-address-save").trigger("click")
      await settle()
      expect(inBody("sending-address-failure").text()).toBe(said)
    }
  })

  it("removes an address once asked, and says why a removal was refused", async () => {
    const wrapper = await mount()
    await wrapper.get('[data-testid="sending-address-edit-3"]').trigger("click")
    await settle()
    await inBody("sending-address-remove").trigger("click")
    await settle()
    expect(document.body.textContent).toContain("Remove board@b.nl? Its login is deleted from Vault")

    api.removeSendingAddress.mockResolvedValueOnce({status: 503, error: {code: "SendingLoginsUnavailable"}})
    wrapper.findComponent({name: "ConfirmDialog"}).vm.$emit("confirm")
    await settle()
    expect(wrapper.findComponent({name: "ConfirmDialog"}).props("failure")).toContain("Vault, where the logins are kept, cannot be reached now")

    wrapper.findComponent({name: "ConfirmDialog"}).vm.$emit("confirm")
    await settle()
    expect(api.removeSendingAddress).toHaveBeenLastCalledWith({path: {id: 3}})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "board@b.nl is removed.")
    wrapper.findComponent({name: "ConfirmDialog"}).vm.$emit("update:open", false)
    wrapper.findComponent({name: "ModalDialog"}).vm.$emit("update:open", false)
  })

  it("draws each address as a row on a phone, with an edit for an admin", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    const rows = wrapper.findAllComponents({name: "ManagementRow"})
    expect(rows.map((row) => row.props("name"))).toEqual(["board@b.nl", "events@b.nl"])
    await rows[1]!.findComponent({name: "CutButton"}).trigger("click")
    await settle()
    expect(document.body.textContent).toContain("Edit events@b.nl")
  })
})
