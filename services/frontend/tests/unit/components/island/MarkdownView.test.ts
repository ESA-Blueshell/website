import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import MarkdownView from "@/components/island/MarkdownView.vue"

describe("MarkdownView", () => {
  it("draws the markdown it is given as its elements", () => {
    const wrapper = mount(MarkdownView, {props: {source: "# Hey\n\n- **one**\n- two"}})

    expect(wrapper.get(".markdown-view h1").text()).toBe("Hey")
    expect(wrapper.findAll(".markdown-view li")).toHaveLength(2)
    expect(wrapper.get("li strong").text()).toBe("one")
  })

  it("drops a script written into it", () => {
    const wrapper = mount(MarkdownView, {props: {source: "hi <script>alert(1)</script>"}})

    expect(wrapper.find("script").exists()).toBe(false)
  })
})
