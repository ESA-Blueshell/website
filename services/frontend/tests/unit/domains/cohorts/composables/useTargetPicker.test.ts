import { describe, expect, it, vi } from "vitest"
import { useTargetPicker } from "@/domains/cohorts/composables/useTargetPicker"
import { fetchTargetOptions, type ExternalTarget } from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/cohorts/adapters/cohorts")>()
  return {
    ...actual,
    fetchTargetOptions: vi.fn(),
    linkExistingTargetForCohort: vi.fn(),
    createTargetForCohort: vi.fn(),
    switchCohortTarget: vi.fn(),
  }
})

const targets: ExternalTarget[] = [
  target("1", "Guests", "Newsletter"),
  target("2", "Paid members", "Contribution periods"),
]

describe("useTargetPicker", () => {
  it("loads the catalogue, then filters it client-side", async () => {
    vi.mocked(fetchTargetOptions).mockResolvedValue(targets)
    const picker = useTargetPicker()

    await picker.load("BREVO")
    picker.form.search = "paid"

    expect(fetchTargetOptions).toHaveBeenCalledWith("BREVO")
    expect(picker.filteredOptions.value.map((item) => item.externalId)).toEqual(["2"])
  })

  it("offers the folders the catalogue mentions, once each and in order", async () => {
    vi.mocked(fetchTargetOptions).mockResolvedValue([
      target("1", "Guests", "Newsletter"),
      target("2", "Paid members", "Contribution periods"),
      target("3", "Unpaid members", "Contribution periods"),
    ])
    const picker = useTargetPicker()

    await picker.load("BREVO")

    // One entry per folder, sorted, so creating a target beside existing ones is a search
    // rather than a recollection.
    expect(picker.folderOptions.value).toEqual(["Contribution periods", "Newsletter"])
  })

  it("offers no folders before the catalogue is loaded", () => {
    const picker = useTargetPicker()

    expect(picker.folderOptions.value).toEqual([])
  })

  it("leaves out targets that name no folder", async () => {
    vi.mocked(fetchTargetOptions).mockResolvedValue([
      {...target("1", "Loose list", "Newsletter"), folderLabel: null},
      target("2", "Filed list", "Newsletter"),
    ])
    const picker = useTargetPicker()

    await picker.load("BREVO")

    expect(picker.folderOptions.value).toEqual(["Newsletter"])
  })
})

function target(externalId: string, label: string, folderLabel: string): ExternalTarget {
  return {
    system: "BREVO",
    externalId,
    kind: "LIST",
    label,
    folderLabel,
    memberCount: null,
    linkedTargetId: null,
  }
}
