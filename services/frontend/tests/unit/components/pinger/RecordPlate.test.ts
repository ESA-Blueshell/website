/**
 * The record plate: the combined record and when it was set, beside the pings sent in total.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
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

  // Same day as at, so the time shows without a date.
  beforeEach(() => {
    vi.useFakeTimers({toFake: ["Date"]})
    vi.setSystemTime(new Date(2026, 9, 9, 22, 0))
  })

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.unmount())
    vi.useRealTimers()
  })

  it("shows the record with when it was set, and the pings sent in total", () => {
    const wrapper = render({record: {pps: 2_400_000, at}, totalSent: 1_200_000_000})

    expect(wrapper.get("[data-testid=snt-record-best]").text()).toBe("2.4M pings a second, set 21:14")
    expect(wrapper.get("[data-testid=snt-record-total]").text()).toBe("1.2B pings")
  })

  it("says no record was set when there is none", () => {
    const wrapper = render({record: null, totalSent: 0})

    expect(wrapper.get("[data-testid=snt-record-none]").text()).toBe("No record set")
  })
})
