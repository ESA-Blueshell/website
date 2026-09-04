import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
// aliased: the local mount helper below would otherwise shadow what it calls
import {mount as mountComponent, type VueWrapper} from "@vue/test-utils"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {nextTick} from "vue"
import {unmountAll} from "../../helpers/testUtils"

describe("SubmitButton", () => {
  const wrappers: VueWrapper[] = []

  function mount(props: Record<string, unknown> = {}) {
    const wrapper = mountComponent(SubmitButton, {
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
    unmountAll(wrappers, "SubmitButton")
  })

  it("renders default text when idle", () => {
    const wrapper = mount()
    expect(wrapper.text()).toContain("Submit")
  })

  it("renders custom slot text", () => {
    const wrapper = mountComponent(SubmitButton, {
      slots: {default: "Save changes"},
      props: {submitState: "idle", showSubmitStatus: false},
    })
    wrappers.push(wrapper)
    expect(wrapper.text()).toContain("Save changes")
  })

  it("shows a loader and stops taking presses while loading", () => {
    const wrapper = mount({loading: true})
    const btn = wrapper.get(".submit-btn")

    expect(btn.find(".v-progress-circular").exists()).toBe(true)
    expect((btn.element as HTMLButtonElement).disabled).toBe(true)
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
