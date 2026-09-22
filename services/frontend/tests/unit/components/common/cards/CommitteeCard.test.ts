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
    expect(wrapper.get(".committee-card").attributes("id")).toBe("committee-1")
  })

  // A committee the api answered without a number is still a committee, and the card is
  // linked to by its name instead.
  it("says so where a committee has no description, and keys itself by name", () => {
    const wrapper = mount(CommitteeCard, {
      props: {committee: {name: "Esports Committee"}},
    })

    expect(wrapper.text()).toContain("No description...")
    expect(mockMarkdownToHtml).not.toHaveBeenCalled()
    expect(wrapper.get(".committee-card").attributes("id")).toBe("committee-Esports Committee")
  })
})
