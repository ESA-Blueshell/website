import { describe, expect, it, vi } from "vitest"
import { useInboundReconcile } from "@/domains/cohorts/composables/useInboundReconcile"
import {
  applyInboundReconcileSelection,
  fetchInboundReconcilePreview,
  type InboundReconcilePreview,
} from "@/domains/cohorts/adapters/cohorts"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/domains/cohorts/adapters/cohorts")>()
  return {
    ...actual,
    fetchInboundReconcilePreview: vi.fn(),
    applyInboundReconcileSelection: vi.fn(),
  }
})

describe("useInboundReconcile", () => {
  it("loads preview and selects only writable matched rows", async () => {
    vi.mocked(fetchInboundReconcilePreview).mockResolvedValue(preview())
    const reconcile = useInboundReconcile()

    await reconcile.load(10, 20)

    expect(reconcile.preview.value?.matched).toHaveLength(3)
    expect(reconcile.selectedExternalUserIds.value).toEqual(["ext-writable"])
    expect(reconcile.canApply.value).toBe(true)
  })

  it("does not allow apply when the subject fact has no writer", async () => {
    vi.mocked(fetchInboundReconcilePreview).mockResolvedValue(preview({ writerSupported: false }))
    const reconcile = useInboundReconcile()

    await reconcile.load(10, 20)
    const applied = await reconcile.apply(10, 20)

    expect(applied).toBe(false)
    expect(reconcile.canApply.value).toBe(false)
    expect(applyInboundReconcileSelection).not.toHaveBeenCalled()
  })

  it("applies selected rows with the preview token", async () => {
    vi.mocked(fetchInboundReconcilePreview).mockResolvedValue(preview())
    vi.mocked(applyInboundReconcileSelection).mockResolvedValue({
      jobId: 55,
      acceptedCount: 1,
      skippedCount: 2,
    })
    const reconcile = useInboundReconcile()

    await reconcile.load(10, 20)
    const applied = await reconcile.apply(10, 20)

    expect(applied).toBe(true)
    expect(applyInboundReconcileSelection).toHaveBeenCalledWith(10, 20, "token-1", ["ext-writable"])
    expect(reconcile.applyResult.value?.jobId).toBe(55)
  })
})

function preview(overrides: Partial<InboundReconcilePreview> = {}): InboundReconcilePreview {
  return {
    subjectId: 10,
    cohortId: 20,
    system: "BREVO",
    externalTargetId: "list-20",
    fact: { kind: "CONTRIBUTION_PAID", key: "12" },
    writerSupported: true,
    previewToken: "token-1",
    remoteCount: 4,
    matched: [
      row("ext-writable", false, true),
      row("ext-true", true, false),
      row("ext-unsupported", false, false),
    ],
    skipped: [{ externalUserId: "ext-skip", externalLabel: null, reason: "UNMATCHED" }],
    ...overrides,
  }
}

function row(externalUserId: string, alreadyTrue: boolean, writable: boolean) {
  return {
    externalUserId,
    externalLabel: externalUserId,
    userId: externalUserId.length,
    userFullName: externalUserId,
    userEmail: `${externalUserId}@example.org`,
    alreadyTrue,
    writable,
  }
}
