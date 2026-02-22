import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import DocumentTable from "@/components/base/DocumentTable.vue"

const {mockRequire} = vi.hoisted(() => ({
  mockRequire: vi.fn((path: string) => `https://assets.example.test/${encodeURIComponent(path)}`),
}))

vi.mock("@/plugins/require.ts", () => ({
  $require: mockRequire,
}))

function findButton(wrapper: ReturnType<typeof shallowMount>, label: string) {
  const button = wrapper.findAll("button").find((node) => node.text().trim() === label)
  if (!button) {
    throw new Error(`Expected button with label '${label}'`)
  }
  return button
}

describe("DocumentTable", () => {
  const createdLinks: HTMLAnchorElement[] = []

  beforeEach(() => {
    vi.clearAllMocks()
    createdLinks.length = 0

    const createElement = document.createElement.bind(document)
    vi.spyOn(document, "createElement").mockImplementation((tagName: string) => {
      const element = createElement(tagName)
      if (tagName.toLowerCase() === "a") {
        createdLinks.push(element as HTMLAnchorElement)
        vi.spyOn(element as HTMLAnchorElement, "click").mockImplementation(() => {})
      }
      return element
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("renders statutes language actions", () => {
    const wrapper = shallowMount(DocumentTable, {
      global: {
        stubs: {
          VSheet: {template: "<div><slot /></div>"},
          VRow: {template: "<div><slot /></div>"},
          VCol: {template: "<div><slot /></div>"},
          VDivider: {template: "<hr />"},
          VBtn: {
            template: "<button @click=\"$emit('click')\"><slot /></button>",
            emits: ["click"],
          },
        },
      },
    })

    expect(wrapper.text()).toContain("Statutes")
    expect(findButton(wrapper, "Dutch").exists()).toBe(true)
    expect(findButton(wrapper, "English").exists()).toBe(true)
  })

  it("downloads dutch and english statutes with expected filenames", async () => {
    const appendSpy = vi.spyOn(document.body, "appendChild")
    const removeSpy = vi.spyOn(document.body, "removeChild")
    const wrapper = shallowMount(DocumentTable, {
      global: {
        stubs: {
          VSheet: {template: "<div><slot /></div>"},
          VRow: {template: "<div><slot /></div>"},
          VCol: {template: "<div><slot /></div>"},
          VDivider: {template: "<hr />"},
          VBtn: {
            template: "<button @click=\"$emit('click')\"><slot /></button>",
            emits: ["click"],
          },
        },
      },
    })

    await findButton(wrapper, "Dutch").trigger("click")
    await findButton(wrapper, "English").trigger("click")

    expect(createdLinks).toHaveLength(2)
    expect(createdLinks[0].download).toBe("ESA Blueshell - Statuten.pdf")
    expect(createdLinks[1].download).toBe("ESA Blueshell - Statutes.pdf")
    expect(mockRequire).toHaveBeenCalledWith("@/assets/documents/20171212 - ESA Blueshell Statuten.pdf")
    expect(mockRequire).toHaveBeenCalledWith("@/assets/documents/20171212 - ESA Blueshell Statutes.pdf")
    expect(createdLinks[0].click).toHaveBeenCalledTimes(1)
    expect(createdLinks[1].click).toHaveBeenCalledTimes(1)
    expect(appendSpy).toHaveBeenCalledTimes(2)
    expect(removeSpy).toHaveBeenCalledTimes(2)
  })
})
