import {describe, expect, it, vi} from "vitest"
import {useTargetOverview} from "@/domains/cohorts/composables/useTargetOverview"
import {
  fetchTargetDescriptors,
  fetchTargetFolders,
  fetchTargetOptions,
  moveTargetToFolder,
  type ExternalTarget,
  type TargetDescriptor,
} from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/cohorts/adapters/cohorts")>()
  return {
    ...actual,
    fetchTargetDescriptors: vi.fn(),
    fetchTargetOptions: vi.fn(),
    fetchTargetFolders: vi.fn(),
    moveTargetToFolder: vi.fn(),
  }
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

async function loaded(targets: ExternalTarget[], descriptor = brevo) {
  vi.mocked(fetchTargetDescriptors).mockResolvedValue([descriptor])
  vi.mocked(fetchTargetOptions).mockResolvedValue(targets)
  vi.mocked(fetchTargetFolders).mockResolvedValue(["Contributions", "Newsletter"])
  const overview = useTargetOverview()
  await overview.load("BREVO")
  return overview
}

const movable = {...brevo, capabilities: ["CATALOG", "MOVE"] as typeof brevo.capabilities}

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

  describe("moving a target to another folder", () => {
    it("says so only when the system can move one", async () => {
      const withoutMove = await loaded([target("1", "A", "Newsletter")])
      expect(withoutMove.canMove.value).toBe(false)

      const withMove = await loaded([target("1", "A", "Newsletter")], movable)
      expect(withMove.canMove.value).toBe(true)
    })

    it("reads the folders from the system rather than from the targets", async () => {
      const o = await loaded([target("1", "A", "Newsletter")], movable)

      // "Contributions" holds no targets here, and is still a place a target can go.
      expect(o.folderNames.value).toEqual(["Contributions", "Newsletter"])
    })

    it("asks for no folders from a system that cannot move", async () => {
      await loaded([target("1", "A", "Newsletter")])

      expect(fetchTargetFolders).not.toHaveBeenCalled()
    })

    it("takes the row from what the api answered, not from what was asked", async () => {
      const o = await loaded([target("1", "A", "Newsletter")], movable)
      vi.mocked(moveTargetToFolder).mockResolvedValue(target("1", "A", "Contributions"))

      const ok = await o.move("BREVO", o.targets.value[0]!, "Contributions")

      expect(ok).toBe(true)
      expect(o.targets.value[0]!.folderLabel).toBe("Contributions")
      expect(o.folders.value.map((f) => f.label)).toEqual(["Contributions"])
    })

    it("reports a refusal and leaves the row where it was", async () => {
      const o = await loaded([target("1", "A", "Newsletter")], movable)
      vi.mocked(moveTargetToFolder).mockRejectedValue(new Error("No folder named 'Nowhere'"))

      const ok = await o.move("BREVO", o.targets.value[0]!, "Nowhere")

      expect(ok).toBe(false)
      expect(o.errorMessage.value).toContain("No folder named")
      expect(o.targets.value[0]!.folderLabel).toBe("Newsletter")
      expect(o.moving.value).toBeNull()
    })
  })
})
