import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"

const preview = {
  subject: "Activate your Account",
  html: "<p>Dear Alice Regular</p>",
  recipientEmail: "alice@example.com",
  recipientName: "Alice Regular",
}

describe("EmailPreviewDialog", () => {
  it("shows the subject and who the email would go to", () => {
    const wrapper = mount(EmailPreviewDialog, {props: {modelValue: true, preview}})

    expect(wrapper.find('[data-testid="email-preview-subject"]').text()).toBe("Activate your Account")
    expect(wrapper.find('[data-testid="email-preview-recipient"]').text())
      .toContain("Alice Regular <alice@example.com>")
  })

  it("renders the email inside a sandboxed frame", () => {
    const wrapper = mount(EmailPreviewDialog, {props: {modelValue: true, preview}})
    const frame = wrapper.find('[data-testid="email-preview-frame"]')

    expect(frame.attributes("srcdoc")).toBe("<p>Dear Alice Regular</p>")
    // Empty sandbox: the email's styles cannot reach the app and nothing in it runs.
    expect(frame.attributes("sandbox")).toBe("")
  })

  it("says the links are inert when a placeholder stands in for a token", () => {
    const wrapper = mount(EmailPreviewDialog, {
      props: {modelValue: true, preview: {...preview, linkPlaceholder: "PREVIEW-ONLY-NO-TOKEN-ISSUED"}},
    })

    expect(wrapper.find('[data-testid="email-preview-placeholder-notice"]').text())
      .toContain("do not work")
  })

  it("stays quiet about links when the email carries no credential", () => {
    const wrapper = mount(EmailPreviewDialog, {props: {modelValue: true, preview}})

    expect(wrapper.find('[data-testid="email-preview-placeholder-notice"]').exists()).toBe(false)
  })

  it("shows progress instead of an empty frame while rendering", () => {
    const wrapper = mount(EmailPreviewDialog, {props: {modelValue: true, loading: true}})

    expect(wrapper.find('[data-testid="email-preview-loading"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="email-preview-frame"]').exists()).toBe(false)
  })

  it("shows the error instead of the email when rendering failed", () => {
    const wrapper = mount(EmailPreviewDialog, {
      props: {modelValue: true, preview, error: "The preview could not be rendered."},
    })

    expect(wrapper.find('[data-testid="email-preview-error"]').text()).toContain("could not be rendered")
    expect(wrapper.find('[data-testid="email-preview-frame"]').exists()).toBe(false)
  })

  it("falls back to the address when no name is known", () => {
    const wrapper = mount(EmailPreviewDialog, {
      props: {modelValue: true, preview: {...preview, recipientName: null}},
    })

    expect(wrapper.find('[data-testid="email-preview-recipient"]').text()).toContain("alice@example.com")
  })

  it("names no recipient when the render did not identify one", () => {
    const wrapper = mount(EmailPreviewDialog, {
      props: {modelValue: true, preview: {subject: "s", html: "<p>x</p>"}},
    })

    expect(wrapper.find('[data-testid="email-preview-recipient"]').exists()).toBe(false)
  })

  it("hosts a caller's own controls beside the email", () => {
    const wrapper = mount(EmailPreviewDialog, {
      props: {modelValue: true, preview},
      slots: {recipient: '<div data-testid="pick-recipient">choose</div>'},
    })

    // Bulk dialogs put a recipient picker here; changing it re-renders the email.
    expect(wrapper.find('[data-testid="pick-recipient"]').exists()).toBe(true)
  })
})
