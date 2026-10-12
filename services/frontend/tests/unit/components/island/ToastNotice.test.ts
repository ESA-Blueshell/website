import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import ToastArea from "@/components/island/ToastArea.vue"
import ToastNotice from "@/components/island/ToastNotice.vue"

const toast = (props: Record<string, unknown> = {}) => mount(ToastNotice, {
  props: {open: true, testid: "saved", ...props},
  slots: {default: "Saved", actions: "<button>Close</button>"},
})

describe("ToastNotice", () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it("says its message to a screen reader, with its actions beside it", () => {
    const wrapper = toast()

    const said = wrapper.get("[data-testid='saved']")
    expect(said.attributes("role")).toBe("status")
    expect(said.text()).toContain("Saved")
    expect(said.get("button").text()).toBe("Close")
  })

  it("draws nothing while it is closed", () => {
    expect(toast({open: false}).find("[data-testid='saved']").exists()).toBe(false)
  })

  it("stays until it is closed when it is given no time", () => {
    const wrapper = toast()

    vi.advanceTimersByTime(600_000)

    expect(wrapper.emitted("update:open")).toBeUndefined()
  })

  it("closes itself once its time is up", () => {
    const wrapper = toast({timeout: 10_000})

    vi.advanceTimersByTime(9_999)
    expect(wrapper.emitted("update:open")).toBeUndefined()

    vi.advanceTimersByTime(1)
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })

  it("starts its time over when it opens again, and counts nothing while closed", async () => {
    const wrapper = toast({timeout: 10_000})

    vi.advanceTimersByTime(6_000)
    await wrapper.setProps({open: false})
    vi.advanceTimersByTime(60_000)
    expect(wrapper.emitted("update:open")).toBeUndefined()

    await wrapper.setProps({open: true})
    vi.advanceTimersByTime(9_999)
    expect(wrapper.emitted("update:open")).toBeUndefined()
    vi.advanceTimersByTime(1)
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })

  it("waits while it is pointed at or holds the focus, then starts its time over", async () => {
    const wrapper = toast({timeout: 10_000})
    const said = wrapper.get("[data-testid='saved']")

    vi.advanceTimersByTime(9_000)
    await said.trigger("pointerenter")
    vi.advanceTimersByTime(60_000)
    expect(wrapper.emitted("update:open")).toBeUndefined()

    await said.trigger("pointerleave")
    vi.advanceTimersByTime(9_999)
    expect(wrapper.emitted("update:open")).toBeUndefined()

    await said.get("button").trigger("focusin")
    vi.advanceTimersByTime(60_000)
    expect(wrapper.emitted("update:open")).toBeUndefined()

    await said.get("button").trigger("focusout")
    vi.advanceTimersByTime(10_000)
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })

  it("leaves no timer running once it is taken off the page", () => {
    const wrapper = toast({timeout: 10_000})

    wrapper.unmount()
    vi.advanceTimersByTime(10_000)

    expect(wrapper.emitted("update:open")).toBeUndefined()
  })

  it("is drawn as a warning when it warns", () => {
    expect(toast({tone: "warning"}).get("[data-testid='saved']").classes()).toContain("toast--warning")
    expect(toast().get("[data-testid='saved']").classes()).not.toContain("toast--warning")
  })
})

describe("ToastArea", () => {
  it("holds its toasts at the foot of the window, or at the top when asked", () => {
    const foot = mount(ToastArea, {slots: {default: "<p>Saved</p>"}})
    const top = mount(ToastArea, {props: {place: "top"}, slots: {default: "<p>Saved</p>"}})

    expect(foot.classes()).toContain("toast-area--bottom")
    expect(foot.text()).toBe("Saved")
    expect(top.classes()).toContain("toast-area--top")
  })
})
