import {describe, expect, it} from "vitest"
import $markdownToHtml from "@/plugins/markdownToHtml"

describe("markdownToHtml plugin", () => {
  it("renders markdown and emoji", () => {
    const html = $markdownToHtml("**Bold** :rocket:")
    expect(html).toContain("<strong>Bold</strong>")
    expect(html).toContain("🚀")
  })

  it("sanitizes scripts", () => {
    const html = $markdownToHtml("<script>alert('x')</script><p>safe</p>")
    expect(html).not.toContain("<script>")
    expect(html).toContain("safe")
  })
})
