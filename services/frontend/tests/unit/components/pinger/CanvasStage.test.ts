/**
 * The canvas stage sums its placements' pixel counts and drops a removed placement's share, so the
 * total it reports never keeps counting an image that is gone.
 */
import {afterEach, describe, expect, it} from "vitest"
import {defineComponent, h} from "vue"
import {mount, type VueWrapper} from "@vue/test-utils"
import CanvasStage from "@/components/pinger/CanvasStage.vue"

// Stub the per-image canvas: it just reports a fixed pass count for its box on mount.
const CountStub = defineComponent({
  props: {originX: {type: Number, required: true}},
  emits: ["passtotal"],
  setup(props, {emit}) {
    emit("passtotal", props.originX)
    return () => h("div")
  },
})

const placement = (id: number, originX: number) => ({
  id,
  imageUrl: `/files/public/pinger-paint/${id}.webp`,
  originX,
  originY: 0,
  width: 100,
  height: 100,
})

describe("CanvasStage", () => {
  const wrappers: VueWrapper[] = []
  const render = (placements: ReturnType<typeof placement>[]) => {
    const wrapper = mount(CanvasStage, {
      props: {placements, running: true, sent: 0, pps: 0, prefixLabel: "2001:db8::/64"},
      global: {stubs: {CanvasPlacement: CountStub}},
    })
    wrappers.push(wrapper)
    return wrapper
  }

  afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

  it("reports the sum of its placements' counts", async () => {
    const wrapper = render([placement(1, 30), placement(2, 70)])
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("passtotal")?.at(-1)?.[0]).toBe(100)
  })

  it("drops a removed placement's count from the total", async () => {
    const wrapper = render([placement(1, 30), placement(2, 70)])
    await wrapper.vm.$nextTick()

    await wrapper.setProps({placements: [placement(1, 30)]})
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("passtotal")?.at(-1)?.[0]).toBe(30)
  })
})
