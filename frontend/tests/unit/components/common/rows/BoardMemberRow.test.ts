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
})
