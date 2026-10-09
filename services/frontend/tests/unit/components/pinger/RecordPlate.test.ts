/**
 * The combined record plate: the record and when it was set, and the combined rate right now
 * against it.
 */
import {afterEach, describe, expect, it} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import RecordPlate from "@/components/pinger/RecordPlate.vue"

const at = new Date(2026, 9, 9, 21, 14).toISOString()

describe("RecordPlate", () => {
  const wrappers: VueWrapper[] = []
  const render = (props: InstanceType<typeof RecordPlate>["$props"]) => {
    const wrapper = mount(RecordPlate, {props})
    wrappers.push(wrapper)
    return wrapper
  }

  afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

  it("shows the record with when it was set, and the rate right now against it", () => {
    const wrapper = render({record: {pps: 2_400_000, at}, combinedPps: 1_200_000})

    expect(wrapper.get("[data-testid=snt-record-best]").text()).toBe("2.4M pings a second, set 21:14")
    expect(wrapper.get("[data-testid=snt-record-now]").text()).toBe("1.2M pings a second")
    expect(wrapper.get(".plate__fill").attributes("style")).toContain("width: 50%")
  })

  it("fills the bar no further than the whole when the rate runs past the record", () => {
    const wrapper = render({record: {pps: 1_000, at}, combinedPps: 5_000})

    expect(wrapper.get(".plate__fill").attributes("style")).toContain("width: 100%")
  })

  it("says no record is set yet, and draws no bar, before the first one", () => {
    const wrapper = render({record: null, combinedPps: 0})

    expect(wrapper.get("[data-testid=snt-record-none]").text()).toBe("No record set yet")
    expect(wrapper.find(".plate__meter").exists()).toBe(false)
  })
})
