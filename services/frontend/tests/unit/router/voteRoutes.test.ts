import {describe, expect, it} from "vitest"
import router from "@/plugins/router"
import {OST_TOP_50} from "@/domains/vote"

describe("the annual vote's routes", () => {
  const propsAt = (path: string) => {
    const route = router.resolve(path)
    const props = route.matched[0]!.props.default as (at: typeof route) => Record<string, unknown>
    return props(route)
  }

  it("shows the kind's current vote at its own path, and a past year at that year", () => {
    expect(propsAt("/events/ost-top-50")).toEqual({page: OST_TOP_50, year: undefined})
    expect(propsAt("/events/ost-top-50/2025")).toEqual({page: OST_TOP_50, year: 2025})
  })

  it("takes a year only as four digits", () => {
    expect(router.resolve("/events/ost-top-50/abc").matched[0]?.name).not.toBe(OST_TOP_50.routeName)
  })
})
