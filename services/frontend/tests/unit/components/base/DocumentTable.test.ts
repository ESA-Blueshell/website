import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import DocumentTable from "@/components/base/DocumentTable.vue"
import {
  ACTIVE_COOKIE_POLICY_DOWNLOAD_NAMES,
  ACTIVE_COOKIE_POLICY_PATHS,
} from "@/config/policies"

const {mockRequire} = vi.hoisted(() => ({
  mockRequire: vi.fn((path: string) => `https://assets.example.test/${encodeURIComponent(path)}`),
}))

vi.mock("@/plugins/require.ts", () => ({
  $require: mockRequire,
}))

function assetUrl(path: string) {
  return `https://assets.example.test/${encodeURIComponent(path)}`
}

function mountTable() {
  return shallowMount(DocumentTable, {
    global: {
      stubs: {
        VSheet: {template: "<div><slot /></div>"},
        VRow: {template: "<div><slot /></div>"},
        VCol: {template: "<div><slot /></div>"},
        VDivider: {template: "<hr />"},
        VBtn: {template: "<a><slot /></a>"},
      },
    },
  })
}

function documentLinks(wrapper: ReturnType<typeof mountTable>) {
  return wrapper.findAll("a").map((node) => ({
    label: node.text().trim(),
    href: node.attributes("href"),
    download: node.attributes("download"),
  }))
}

describe("DocumentTable", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("renders a Dutch and English link for every document", () => {
    const wrapper = mountTable()

    for (const title of [
      "Statutes",
      "Domestic Regulations",
      "Privacy Policy",
      "Code of Conduct",
      "Cookie Policy",
    ]) {
      expect(wrapper.text()).toContain(title)
    }

    const links = documentLinks(wrapper)
    expect(links).toHaveLength(10)
    expect(links.map((link) => link.label)).toEqual(Array(5).fill(["Dutch", "English"]).flat())
  })

  it("links the statutes to their bundled assets with a download filename", () => {
    const dutchPath = "@/assets/documents/20171212 - ESA Blueshell Statuten.pdf"
    const englishPath = "@/assets/documents/20171212 - ESA Blueshell Statutes.pdf"

    const links = documentLinks(mountTable())

    expect(links[0]).toEqual({
      label: "Dutch",
      href: assetUrl(dutchPath),
      download: "ESA Blueshell - Statuten.pdf",
    })
    expect(links[1]).toEqual({
      label: "English",
      href: assetUrl(englishPath),
      download: "ESA Blueshell - Statutes.pdf",
    })
    expect(mockRequire).toHaveBeenCalledWith(dutchPath)
    expect(mockRequire).toHaveBeenCalledWith(englishPath)
  })

  it("links the cookie policy from the active policy metadata", () => {
    const links = documentLinks(mountTable())

    expect(links.at(-2)).toEqual({
      label: "Dutch",
      href: assetUrl(ACTIVE_COOKIE_POLICY_PATHS.dutch),
      download: ACTIVE_COOKIE_POLICY_DOWNLOAD_NAMES.dutch,
    })
    expect(links.at(-1)).toEqual({
      label: "English",
      href: assetUrl(ACTIVE_COOKIE_POLICY_PATHS.english),
      download: ACTIVE_COOKIE_POLICY_DOWNLOAD_NAMES.english,
    })
  })
})
