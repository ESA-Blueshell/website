import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h} from "vue"
import {usePhone} from "@/composables/usePhone"

const Probe = defineComponent({
  setup() {
    const phone = usePhone()
    return () => h("p", phone.value ? "phone" : "wide")
  },
})

describe("usePhone", () => {
  afterEach(() => vi.unstubAllGlobals())

  it("follows the window across the phone width, and lets go when its part leaves", async () => {
    const listeners: Array<(event: MediaQueryListEvent) => void> = []
    const query = {
      matches: false,
      addEventListener: vi.fn((_: string, listener: (event: MediaQueryListEvent) => void) => listeners.push(listener)),
      removeEventListener: vi.fn(),
    }
    vi.stubGlobal("matchMedia", vi.fn(() => query))
    const wrapper = mount(Probe)

    expect(wrapper.text()).toBe("wide")
    listeners[0]!({matches: true} as MediaQueryListEvent)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toBe("phone")
    wrapper.unmount()
    expect(query.removeEventListener).toHaveBeenCalledWith("change", listeners[0])
  })

  it("reads wide where the window cannot be asked", () => {
    vi.stubGlobal("matchMedia", undefined)

    expect(mount(Probe).text()).toBe("wide")
  })
})
