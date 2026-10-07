import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, type VueWrapper} from "@vue/test-utils"
import SendingAddresses from "@/pages/management/SendingAddresses.vue"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  listSendingAddresses: vi.fn(),
  addSendingAddress: vi.fn(),
  setSendingAddress: vi.fn(),
  removeSendingAddress: vi.fn(),
  checkSendingAddress: vi.fn(),
}))
const mockStore = vi.hoisted(() => ({commit: vi.fn(), getters: {isAdmin: false} as Record<string, unknown>}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const events = {
  id: 5, address: "events@b.nl", displayName: "Events", host: "smtp.b.nl", port: 587, security: "STARTTLS",
  imapHost: "imap.b.nl", imapPort: 993, imapSecurity: "SSL", isDefault: true, loginKept: true,
  canSend: true, canRead: false, sendFailure: null, readFailure: "NO [AUTHENTICATIONFAILED]", checkedAt: "2026-10-07T05:30:00Z",
}
const board = {
  ...events, id: 3, address: "board@b.nl", displayName: "Board", host: "mail.b.nl", port: 465, security: "SSL",
  imapHost: null, imapPort: null, imapSecurity: null, isDefault: false, loginKept: false,
  canSend: null, canRead: null, readFailure: null, checkedAt: null,
}

const inBody = (testid: string) => new DOMWrapper(document.body.querySelector(`[data-testid="${testid}"]`)!)
const type = async (testid: string, value: string) => {
  await inBody(testid).setValue(value)
}

describe("the Addresses page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(SendingAddresses, {attachTo: document.body})
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.listSendingAddresses.mockResolvedValue({status: 200, data: [board, events]})
    api.addSendingAddress.mockResolvedValue({status: 201, data: {...events, id: 9, address: "lan@b.nl"}})
    api.setSendingAddress.mockResolvedValue({status: 200, data: events})
    api.removeSendingAddress.mockResolvedValue({status: 204, data: undefined})
    api.checkSendingAddress.mockResolvedValue({status: 200, data: {...events, canRead: true, readFailure: null}})
  })

  afterEach(() => {
    unmountAll(wrappers, "SendingAddressesPage")
    document.body.innerHTML = ""
  })

  it("says per address whether it sends and is read, why not, when it was checked and which is the default", async () => {
    const wrapper = await mount()

    const row = wrapper.get('[data-testid="sending-address-5"]').text()
    expect(row).toContain("Events · Default")
    expect(row).toContain("Sends")
    expect(row).toContain("smtp.b.nl:587 · STARTTLS")
    expect(row).toContain("Cannot read")
    expect(row).toContain("imap.b.nl:993 · SSL/TLS")
    expect(row).toContain("NO [AUTHENTICATIONFAILED]")
    const other = wrapper.get('[data-testid="sending-address-3"]').text()
    expect(other).toContain("Not checked")
    expect(other).toContain("Not read")
    expect(other).toContain("Never")
    // A board member who is not an admin keeps the addresses too.
    expect(wrapper.find('[data-testid="sending-addresses-add"]').exists()).toBe(true)
    expect(await sortByEveryHead(wrapper)).toBe(4)
  })

  it("tests an address now, redraws its row with what the servers said, and says why a test could not run", async () => {
    const wrapper = await mount()

    await wrapper.get('[data-testid="sending-address-test-5"]').trigger("click")
    await settle()
    expect(api.checkSendingAddress).toHaveBeenCalledWith({path: {id: 5}})
    expect(wrapper.get('[data-testid="sending-address-5"]').text()).toContain("Read")
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "events@b.nl sends and is read.")

    api.checkSendingAddress.mockResolvedValueOnce({status: 200, data: {...board, canSend: true, checkedAt: "2026-10-07T06:00:00Z"}})
    await wrapper.get('[data-testid="sending-address-test-3"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "board@b.nl sends.")

    api.checkSendingAddress.mockResolvedValueOnce({status: 200, data: {...board, canSend: false, sendFailure: "535 no"}})
    await wrapper.get('[data-testid="sending-address-test-3"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "board@b.nl has a problem; see its row.")

    api.checkSendingAddress.mockResolvedValueOnce({status: 404, error: {code: "SendingAddressNotFound"}})
    await wrapper.get('[data-testid="sending-address-test-3"]').trigger("click")
    await settle()
    expect(mockStore.commit).toHaveBeenLastCalledWith("setStatusSnackbarMessage", "That sending address is no longer there.")
  })

  it("adds an address with both servers and one login, the ports following their security, and says why a server refused", async () => {
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
    await type("sending-address-imap-host-input", "imap.b.nl")
    await inBody("sending-address-imap-security-STARTTLS").setValue(true)
    expect((inBody("sending-address-imap-port-input").element as HTMLInputElement).value).toBe("143")
    await type("sending-address-imap-port-input", "1143")
    wrapper.findAllComponents({name: "RadioGroup"})[0]!.vm.$emit("update:modelValue", null)

    api.addSendingAddress.mockResolvedValueOnce({status: 400, error: {code: "MailLoginRefused", protocol: "IMAP", reason: "NO [AUTHENTICATIONFAILED]"}})
    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(inBody("sending-address-failure").text()).toBe("The IMAP server refused the login: NO [AUTHENTICATIONFAILED]")
    for (const [code, said] of [
      ["ImapServerIncomplete", "Fill in the IMAP server's port and security too, or leave its server empty."],
      ["SendingAddressTaken", "lan@b.nl is a sending address already."],
    ]) {
      api.addSendingAddress.mockResolvedValueOnce({status: 400, error: {code, address: "lan@b.nl"}})
      await inBody("sending-address-save").trigger("click")
      await settle()
      expect(inBody("sending-address-failure").text()).toBe(said)
    }

    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(api.addSendingAddress).toHaveBeenLastCalledWith({body: {
      address: "lan@b.nl", displayName: "LanCie", host: "smtp.b.nl", port: 2465, security: "SSL", isDefault: true,
      imapHost: "imap.b.nl", imapPort: 1143, imapSecurity: "STARTTLS", username: "lan", password: "secret",
    }})
    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "lan@b.nl is saved.")
    expect(api.listSendingAddresses).toHaveBeenCalledTimes(2)
  })

  it("edits an address keeping its login, and asks for the login again once a server moves or the IMAP server goes", async () => {
    const wrapper = await mount()
    await wrapper.get('[data-testid="sending-address-edit-5"]').trigger("click")
    await settle()

    expect(document.body.textContent).toContain("Leave the username and password empty to keep the login there is")
    await type("sending-address-name-input", "Events committee")
    await inBody("sending-address-default").setValue(false)
    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(api.setSendingAddress).toHaveBeenLastCalledWith({path: {id: 5}, body: expect.objectContaining({
      displayName: "Events committee", imapHost: "imap.b.nl", imapPort: 993, imapSecurity: "SSL", isDefault: false,
      username: undefined, password: undefined,
    })})

    await wrapper.get('[data-testid="sending-address-edit-5"]').trigger("click")
    await settle()
    await type("sending-address-imap-host-input", "")
    expect(document.body.textContent).toContain("A server changed, so fill in the login for it")
    expect(inBody("sending-address-save").attributes("disabled")).toBeDefined()
    await type("sending-address-username-input", "events")
    await type("sending-address-password-input", "new")
    api.setSendingAddress.mockResolvedValueOnce({status: 400, error: {code: "MailNeedsEncryption", protocol: "SMTP", host: "smtp.b.nl"}})
    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(inBody("sending-address-failure").text()).toContain("pick STARTTLS or SSL/TLS for SMTP")
    for (const [code, said] of [["SendingAddressNeedsLogin", "Fill in the username and password."], ["SendingAddressNotFound", "That sending address is no longer there."]]) {
      api.setSendingAddress.mockResolvedValueOnce({status: 400, error: {code}})
      await inBody("sending-address-save").trigger("click")
      await settle()
      expect(inBody("sending-address-failure").text()).toBe(said)
    }
    await inBody("sending-address-save").trigger("click")
    await settle()
    expect(api.setSendingAddress).toHaveBeenLastCalledWith({path: {id: 5}, body: expect.objectContaining({
      imapHost: undefined, imapPort: undefined, imapSecurity: undefined,
    })})
  })

  it("starts the edit of an address with no IMAP server on the usual IMAP port, and asks for the login when its SMTP server moves", async () => {
    const wrapper = await mount()
    await wrapper.get('[data-testid="sending-address-edit-3"]').trigger("click")
    await settle()

    expect((inBody("sending-address-imap-port-input").element as HTMLInputElement).value).toBe("993")
    expect(document.body.textContent).toContain("Leave the username and password empty")
    await type("sending-address-host-input", "relay.b.nl")
    expect(document.body.textContent).toContain("A server changed, so fill in the login for it")
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

  it("draws each address as a row on a phone, with a test and an edit", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()})))
    const wrapper = await mount()
    vi.unstubAllGlobals()

    const rows = wrapper.findAllComponents({name: "ManagementRow"})
    expect(rows.map((row) => row.props("name"))).toEqual(["board@b.nl", "events@b.nl"])
    expect(rows[1]!.props("meta")).toBe("Events · Sends · Cannot read")
    const [testIt, edit] = rows[1]!.findAllComponents({name: "CutButton"})
    await testIt!.trigger("click")
    await settle()
    expect(api.checkSendingAddress).toHaveBeenCalledWith({path: {id: 5}})
    await edit!.trigger("click")
    await settle()
    expect(document.body.textContent).toContain("Edit events@b.nl")
  })
})
