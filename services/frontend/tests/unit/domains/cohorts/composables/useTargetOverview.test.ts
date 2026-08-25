import {describe, expect, it, vi} from "vitest"
import {useTargetOverview} from "@/domains/cohorts/composables/useTargetOverview"
import {
  fetchTargetDescriptors,
  fetchTargetOptions,
  type ExternalTarget,
  type TargetDescriptor,
} from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/cohorts/adapters/cohorts")>()
  return {...actual, fetchTargetDescriptors: vi.fn(), fetchTargetOptions: vi.fn()}
})

const brevo: TargetDescriptor = {
  system: "BREVO",
  kind: "LIST",
  systemLabel: "Brevo",
  targetLabel: "Brevo list",
  idLabel: "List id",
  folderLabel: "Folder",
  capabilities: ["CATALOG", "CREATE"],
}

function target(
  externalId: string,
  label: string,
  folderLabel: string | null,
  extra: Partial<ExternalTarget> = {},
): ExternalTarget {
  return {
    system: "BREVO",
    externalId,
    kind: "LIST",
    label,
    folderLabel,
    memberCount: null,
    linkedCohortId: null,
    ...extra,
  }
}

async function loaded(targets: ExternalTarget[]) {
  vi.mocked(fetchTargetDescriptors).mockResolvedValue([brevo])
  vi.mocked(fetchTargetOptions).mockResolvedValue(targets)
  const overview = useTargetOverview()
  await overview.load("BREVO")
  return overview
}

describe("useTargetOverview", () => {
  it("groups targets under their folder, in name order", async () => {
    const o = await loaded([
      target("2", "Zeta", "Newsletter"),
      target("1", "Alpha", "Newsletter"),
      target("3", "Beta", "Contributions"),
    ])

    expect(o.folders.value.map((f) => f.label)).toEqual(["Contributions", "Newsletter"])
    expect(o.folders.value[1]!.targets.map((t) => t.label)).toEqual(["Alpha", "Zeta"])
  })

  it("puts the unfiled targets last, because they are the exception", async () => {
    const o = await loaded([target("1", "Loose", null), target("2", "Filed", "Newsletter")])

    expect(o.folders.value.map((f) => f.label)).toEqual(["Newsletter", null])
  })

  it("sums the contacts a folder holds", async () => {
    const o = await loaded([
      target("1", "A", "Newsletter", {memberCount: 12}),
      target("2", "B", "Newsletter", {memberCount: 30}),
    ])

    expect(o.folders.value[0]!.memberCount).toBe(42)
  })

  it("reports an unknown count as unknown rather than as zero", async () => {
    const o = await loaded([target("1", "A", "Newsletter")])

    expect(o.folders.value[0]!.memberCount).toBeNull()
  })

  it("counts what a folder has linked, and what nothing points at", async () => {
    const o = await loaded([
      target("1", "Linked", "Newsletter", {linkedCohortId: 7}),
      target("2", "Orphan", "Newsletter"),
      target("3", "Also orphan", "Contributions"),
    ])

    expect(o.folders.value.find((f) => f.label === "Newsletter")!.linkedCount).toBe(1)
    expect(o.unlinkedCount.value).toBe(2)
  })

  it("searches name, folder and id alike", async () => {
    const o = await loaded([
      target("100", "Members", "Newsletter"),
      target("200", "Alumni", "Contributions"),
    ])

    o.search.value = "contrib"
    expect(o.folders.value.flatMap((f) => f.targets).map((t) => t.label)).toEqual(["Alumni"])

    o.search.value = "100"
    expect(o.folders.value.flatMap((f) => f.targets).map((t) => t.label)).toEqual(["Members"])
  })

  it("asks for no catalogue from a system that has none", async () => {
    vi.mocked(fetchTargetDescriptors).mockResolvedValue([{...brevo, capabilities: []}])
    const o = useTargetOverview()

    await o.load("BREVO")

    expect(fetchTargetOptions).not.toHaveBeenCalled()
    expect(o.folders.value).toEqual([])
  })

  it("reports a failure rather than showing an empty account", async () => {
    vi.mocked(fetchTargetDescriptors).mockRejectedValue(new Error("boom"))
    const o = useTargetOverview()

    await o.load("BREVO")

    expect(o.errorMessage.value).toBe("boom")
    expect(o.loading.value).toBe(false)
  })
})
