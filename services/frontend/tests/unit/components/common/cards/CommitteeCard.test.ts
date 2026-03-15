import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import CommitteeCard from "@/components/common/cards/CommitteeCard.vue"

const {mockMarkdownToHtml} = vi.hoisted(() => ({
  mockMarkdownToHtml: vi.fn((text: string) => `<p>${text}</p>`),
}))

vi.mock("@/plugins/markdownToHtml.ts", () => ({
  default: mockMarkdownToHtml,
}))

describe("CommitteeCard", () => {
  it("renders committee name and markdown description", () => {
    const wrapper = mount(CommitteeCard, {
      props: {
        committee: {
          id: 1,
          name: "Esports Committee",
          description: "Description",
        },
      },
    })

    expect(wrapper.text()).toContain("Esports Committee")
    expect(mockMarkdownToHtml).toHaveBeenCalledWith("Description")
  })
})
