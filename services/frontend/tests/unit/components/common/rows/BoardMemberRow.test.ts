import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import BoardMemberRow from "@/components/common/rows/BoardMemberRow.vue"

describe("BoardMemberRow", () => {
  it("renders both image and no-image variants", () => {
    const withImage = mount(BoardMemberRow, {
      props: {
        member: {name: "Emma", title: "Chair", image: "/img.jpg"},
      },
    })

    expect(withImage.text()).toContain("Emma")
    expect(withImage.find("v-img").exists()).toBe(true)

    const noImage = mount(BoardMemberRow, {
      props: {
        member: {name: "Viktor", title: "Secretary"},
      },
    })

    expect(noImage.text()).toContain("Viktor")
    expect(noImage.find("v-img").exists()).toBe(false)
  })

  // The display face has no İ, ı, ş or Ş — see tests/unit/styles/nameGlyphCoverage.test.ts —
  // so a seat's name carries font-name beside its size class, in both variants. The role
  // line under it is body text already and must not pick the class up.
  it("sets the name, and only the name, in the font that has the letters", () => {
    for (const image of [undefined, "/img.jpg"]) {
      const row = mount(BoardMemberRow, {
        props: {member: {name: "İlayda Hotamiş", title: "Chair", image}},
      })

      const name = row.find("p.font-name")
      expect(name.exists()).toBe(true)
      expect(name.text()).toBe("İlayda Hotamiş")
      expect(name.classes()).toContain("text-h2")
      expect(row.findAll("p.font-name")).toHaveLength(1)
    }
  })
})
