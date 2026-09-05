import {describe, expect, it} from "vitest"
import {defineComponent, nextTick} from "vue"
import {mount} from "@vue/test-utils"
import {useNarrowLayout} from "@/composables/useNarrowLayout"

// The breakpoint is read off the window, so a viewport is a width plus the resize that
// tells the display state about it — not a mocked `useDisplay`.
function resizeTo(width: number): Promise<void> {
  globalThis.innerWidth = width
  globalThis.dispatchEvent(new Event("resize"))
  return nextTick()
}

const Host = defineComponent({
  setup() {
    return useNarrowLayout()
  },
  template: "<div>{{ narrow }}</div>",
})

describe("useNarrowLayout", () => {
  it("calls a viewport too small for a table narrow", async () => {
    await resizeTo(800)
    const wrapper = mount(Host)

    expect(wrapper.vm.narrow).toBe(true)
    wrapper.unmount()
  })

  it("leaves a viewport wide enough for a table alone", async () => {
    await resizeTo(1600)
    const wrapper = mount(Host)

    expect(wrapper.vm.narrow).toBe(false)
    wrapper.unmount()
  })

  it("follows a window the reader resizes, so a page and the modal it opened cannot disagree", async () => {
    await resizeTo(1600)
    const page = mount(Host)
    const modal = mount(Host)
    expect(page.vm.narrow).toBe(false)

    await resizeTo(500)

    expect(page.vm.narrow).toBe(true)
    expect(modal.vm.narrow).toBe(page.vm.narrow)
    page.unmount()
    modal.unmount()
  })
})
