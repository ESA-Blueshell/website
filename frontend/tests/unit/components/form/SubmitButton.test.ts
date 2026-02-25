import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount, type VueWrapper} from "@vue/test-utils"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {nextTick} from "vue"

describe("SubmitButton", () => {
  const wrappers: VueWrapper[] = []

  function mount(props: Record<string, unknown> = {}) {
    const wrapper = shallowMount(SubmitButton, {
      props: {
        submitState: "idle",
        showSubmitStatus: false,
        ...props,
      },
    })
    wrappers.push(wrapper)
    return wrapper
  }

  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    while (wrappers.length > 0) {
      wrappers.pop()?.unmount()
    }
  })

  it("renders default text when idle", () => {
    const wrapper = mount()
    expect(wrapper.text()).toContain("Submit")
  })

  it("renders custom slot text", () => {
    const wrapper = shallowMount(SubmitButton, {
      slots: {default: "Save changes"},
      props: {submitState: "idle", showSubmitStatus: false},
    })
    wrappers.push(wrapper)
    expect(wrapper.text()).toContain("Save changes")
  })

  it("passes loading prop to v-btn", () => {
    const wrapper = mount({loading: true})
    const btn = wrapper.find(".submit-btn")
    expect(btn.attributes("loading")).toBeDefined()
  })

  it("shows success status icon after successful submit", async () => {
    const wrapper = mount({
      submitState: "success",
      showSubmitStatus: true,
      useStatusIcon: true,
    })
    await nextTick()

    expect((wrapper.vm as any).hasStatus).toBe(true)
    expect((wrapper.vm as any).statusIcon).toBe("mdi-check-circle")
    expect((wrapper.vm as any).statusColorClass).toBe("submit-btn__status-overlay--success")
  })

  it("shows error status icon after failed submit", async () => {
    const wrapper = mount({
      submitState: "error",
      showSubmitStatus: true,
      useStatusIcon: true,
    })
    await nextTick()

    expect((wrapper.vm as any).hasStatus).toBe(true)
    expect((wrapper.vm as any).statusIcon).toBe("mdi-close-circle")
    expect((wrapper.vm as any).statusColorClass).toBe("submit-btn__status-overlay--error")
  })

  it("auto-hides status icon after timeout", async () => {
    const wrapper = mount({
      submitState: "success",
      showSubmitStatus: true,
      useStatusIcon: true,
    })
    await nextTick()

    expect((wrapper.vm as any).hasStatus).toBe(true)

    vi.advanceTimersByTime(3200)
    await nextTick()

    expect((wrapper.vm as any).hasStatus).toBe(false)
  })

  it("emits click event on button press", async () => {
    const wrapper = mount()
    await wrapper.find(".submit-btn").trigger("click")

    expect(wrapper.emitted("click")).toHaveLength(1)
  })

  it("does not show status icon when useStatusIcon is false", async () => {
    const wrapper = mount({
      submitState: "success",
      showSubmitStatus: true,
      useStatusIcon: false,
    })
    await nextTick()

    expect((wrapper.vm as any).hasStatus).toBe(false)
  })

  it("returns null statusIcon when submitState is idle", () => {
    const wrapper = mount({submitState: "idle"})
    expect((wrapper.vm as any).statusIcon).toBeNull()
  })
})
