import {flushPromises, mount} from "@vue/test-utils"
import {describe, expect, it, vi} from "vitest"
import BoardBand from "@/domains/boards/island/BoardBand.vue"
import {coveredWidth} from "@/components/island/pictures"
import {anImage} from "../../../helpers/apiFixtures"

const photo = anImage({width: 1600, height: 900})

const box = (width: number, height: number) => {
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(width)
  vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockReturnValue(height)
}

describe("the board band's photograph", () => {
  it("asks for the width its box covers once it has been measured", async () => {
    box(800, 600)

    const wrapper = mount(BoardBand, {props: {photo, label: "Board X", testid: "band"}})
    await flushPromises()

    const covered = coveredWidth({boxWidth: 800, boxHeight: 600, imageWidth: 1600, imageHeight: 900})
    expect(wrapper.get("[data-testid=board-photo]").attributes("sizes")).toBe(`${covered}px`)
  })

  it("measures a photograph the board is given after the band is drawn", async () => {
    box(400, 300)
    const wrapper = mount(BoardBand, {props: {photo: null, label: "Board X", testid: "band"}})
    await flushPromises()
    expect(wrapper.find("[data-testid=board-photo]").exists()).toBe(false)

    await wrapper.setProps({photo})
    await flushPromises()

    const covered = coveredWidth({boxWidth: 400, boxHeight: 300, imageWidth: 1600, imageHeight: 900})
    expect(wrapper.get("[data-testid=board-photo]").attributes("sizes")).toBe(`${covered}px`)
  })
})
