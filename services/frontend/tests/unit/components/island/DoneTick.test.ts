import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import DoneTick from "@/components/island/DoneTick.vue"

describe("DoneTick", () => {
  it("is a picture a screen reader skips, at 48 pixels or the size it is given", () => {
    expect(mount(DoneTick).attributes()).toMatchObject({"aria-hidden": "true", width: "48", height: "48"})
    expect(mount(DoneTick, {props: {size: 64}}).attributes()).toMatchObject({width: "64", height: "64"})
  })
})
