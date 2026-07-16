import { describe, expect, it, vi } from "vitest"
import { useTargetPicker } from "@/domains/cohorts/composables/useTargetPicker"
import {
  fetchTargetDescriptors,
  fetchTargetOptions,
  type ExternalTarget,
  type TargetDescriptor,
} from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/cohorts/adapters/cohorts")>()
  return {
    ...actual,
    fetchTargetDescriptors: vi.fn(),
    fetchTargetOptions: vi.fn(),
    linkExistingTargetForSubject: vi.fn(),
    createTargetForSubject: vi.fn(),
    switchCohortTarget: vi.fn(),
  }
})

const descriptors: TargetDescriptor[] = [
  {
    system: "BREVO",
    kind: "LIST",
    systemLabel: "Brevo",
    targetLabel: "Brevo list",
    idLabel: "List id",
    folderLabel: "Folder",
    capabilities: ["CATALOG", "CREATE"],
  },
  {
    system: "GOOGLE_CALENDAR",
    kind: "GROUP",
    systemLabel: "Calendar",
    targetLabel: "Calendar group",
    idLabel: "Group id",
    folderLabel: null,
    capabilities: [],
  },
]

const targets: ExternalTarget[] = [
  target("1", "Guests", "Newsletter"),
  target("2", "Paid members", "Contribution periods"),
]

describe("useTargetPicker", () => {
  it("loads descriptors and catalog options, then filters options client-side", async () => {
    vi.mocked(fetchTargetDescriptors).mockResolvedValue(descriptors)
    vi.mocked(fetchTargetOptions).mockResolvedValue(targets)
    const picker = useTargetPicker()

    await picker.load("BREVO")
    picker.form.search = "paid"

    expect(fetchTargetDescriptors).toHaveBeenCalledTimes(1)
    expect(fetchTargetOptions).toHaveBeenCalledTimes(1)
    expect(picker.descriptor.value?.idLabel).toBe("List id")
    expect(picker.filteredOptions.value.map((item) => item.externalId)).toEqual(["2"])
  })

  it("does not load catalog options for descriptors without CATALOG", async () => {
    vi.mocked(fetchTargetDescriptors).mockResolvedValue(descriptors)
    const picker = useTargetPicker()

    await picker.load("GOOGLE_CALENDAR")

    expect(picker.hasCatalog.value).toBe(false)
    expect(fetchTargetOptions).not.toHaveBeenCalled()
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
    linkedCohortId: null,
  }
}
