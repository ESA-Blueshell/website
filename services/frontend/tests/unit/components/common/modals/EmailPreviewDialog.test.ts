import {afterEach, describe, expect, it} from "vitest"
import {DOMWrapper, flushPromises, mount as mountInPage, type VueWrapper} from "@vue/test-utils"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"

const preview = {
  subject: "Activate your Account",
  html: "<p>Dear Alice Regular</p>",
  recipientEmail: "alice@example.com",
  recipientName: "Alice Regular",
}

// The dialog is drawn in the page's body, outside the component that opens it.
const opened: VueWrapper[] = []
const mount = async (...args: Parameters<typeof mountInPage>) => {
  const wrapper = mountInPage(args[0], {...args[1], attachTo: document.body})
  opened.push(wrapper)
  await flushPromises()
  return Object.assign(wrapper, {find: (selector: string) => new DOMWrapper(document.body).find(selector)})
}

afterEach(() => {
  for (const wrapper of opened.splice(0)) wrapper.unmount()
  document.body.innerHTML = ""
})

describe("EmailPreviewDialog", () => {
  it("shows the subject and who the email would go to", async () => {
    const wrapper = await mount(EmailPreviewDialog, {props: {modelValue: true, preview}})

    expect(wrapper.find('[data-testid="email-preview-subject"]').text()).toBe("Activate your Account")
    expect(wrapper.find('[data-testid="email-preview-recipient"]').text())
      .toContain("Alice Regular <alice@example.com>")
  })

  it("renders the email inside a sandboxed frame", async () => {
    const wrapper = await mount(EmailPreviewDialog, {props: {modelValue: true, preview}})
    const frame = wrapper.find('[data-testid="email-preview-frame"]')

    expect(frame.attributes("srcdoc")).toBe("<p>Dear Alice Regular</p>")
    // Empty sandbox: the email's styles cannot reach the app and nothing in it runs.
    expect(frame.attributes("sandbox")).toBe("")
  })

  it("says the links are inert when a placeholder stands in for a token", async () => {
    const wrapper = await mount(EmailPreviewDialog, {
      props: {modelValue: true, preview: {...preview, linkPlaceholder: "PREVIEW-ONLY-NO-TOKEN-ISSUED"}},
    })

    expect(wrapper.find('[data-testid="email-preview-placeholder-notice"]').text())
      .toContain("do not work")
  })

  it("stays quiet about links when the email carries no credential", async () => {
    const wrapper = await mount(EmailPreviewDialog, {props: {modelValue: true, preview}})

    expect(wrapper.find('[data-testid="email-preview-placeholder-notice"]').exists()).toBe(false)
  })

  it("shows progress instead of an empty frame while rendering", async () => {
    const wrapper = await mount(EmailPreviewDialog, {props: {modelValue: true, loading: true}})

    expect(wrapper.find('[data-testid="email-preview-loading"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="email-preview-frame"]').exists()).toBe(false)
  })

  it("shows the error instead of the email when rendering failed", async () => {
    const wrapper = await mount(EmailPreviewDialog, {
      props: {modelValue: true, preview, error: "The preview could not be rendered."},
    })

    expect(wrapper.find('[data-testid="email-preview-error"]').text()).toContain("could not be rendered")
    expect(wrapper.find('[data-testid="email-preview-frame"]').exists()).toBe(false)
  })

  it("falls back to the address when no name is known", async () => {
    const wrapper = await mount(EmailPreviewDialog, {
      props: {modelValue: true, preview: {...preview, recipientName: null}},
    })

    expect(wrapper.find('[data-testid="email-preview-recipient"]').text()).toContain("alice@example.com")
  })

  it("names no recipient when the render did not identify one", async () => {
    const wrapper = await mount(EmailPreviewDialog, {
      props: {modelValue: true, preview: {subject: "s", html: "<p>x</p>"}},
    })

    expect(wrapper.find('[data-testid="email-preview-recipient"]').exists()).toBe(false)
  })

  it("hosts a caller's own controls beside the email", async () => {
    const wrapper = await mount(EmailPreviewDialog, {
      props: {modelValue: true, preview},
      slots: {recipient: '<div data-testid="pick-recipient">choose</div>'},
    })

    // Bulk dialogs put a recipient picker here; changing it re-renders the email.
    expect(wrapper.find('[data-testid="pick-recipient"]').exists()).toBe(true)
  })

  describe("as the confirmation step for sending", () => {
    it("offers no send button when the caller did not ask for one", async () => {
      const wrapper = await mount(EmailPreviewDialog, {props: {modelValue: true, preview}})

      expect(wrapper.find('[data-testid="email-preview-send-btn"]').exists()).toBe(false)
    })

    it("offers the send the caller named", async () => {
      const wrapper = await mount(EmailPreviewDialog, {
        props: {modelValue: true, preview, confirmLabel: "Resend Member Activation"},
      })

      const send = wrapper.find('[data-testid="email-preview-send-btn"]')
      expect(send.exists()).toBe(true)
      expect(send.text()).toContain("Resend Member Activation")
    })

    it("asks the caller to send when it is pressed", async () => {
      const wrapper = await mount(EmailPreviewDialog, {
        props: {modelValue: true, preview, confirmLabel: "Send"},
      })

      await wrapper.find('[data-testid="email-preview-send-btn"]').trigger("click")

      expect(wrapper.emitted("confirm")).toHaveLength(1)
    })

    it("will not send an email nobody has read yet", async () => {
      const wrapper = await mount(EmailPreviewDialog, {
        props: {modelValue: true, loading: true, confirmLabel: "Send"},
      })

      // Nothing rendered means nothing was confirmed, so there is nothing to send.
      expect(wrapper.find('[data-testid="email-preview-send-btn"]').exists()).toBe(false)
    })

    it("will not send when rendering failed", async () => {
      const wrapper = await mount(EmailPreviewDialog, {
        props: {modelValue: true, preview, error: "boom", confirmLabel: "Send"},
      })

      expect(wrapper.find('[data-testid="email-preview-send-btn"]').exists()).toBe(false)
    })
  })

  it("says the links were taken out of an email that was already sent", async () => {
    const wrapper = await mount(EmailPreviewDialog, {props: {modelValue: true, preview: {...preview, linksRedacted: true}}})

    expect(wrapper.find('[data-testid="email-preview-redacted-notice"]').text()).toContain("one-time credentials")
  })

  it("closes from its Close button and from the dialog's own cross", async () => {
    const wrapper = await mount(EmailPreviewDialog, {props: {modelValue: true, preview}})

    await wrapper.find('[data-testid="email-preview-close"]').trigger("click")
    await wrapper.find('[data-testid="island-dialog-close"]').trigger("click")

    expect(wrapper.emitted("update:modelValue")).toEqual([[false], [false]])
  })
})
