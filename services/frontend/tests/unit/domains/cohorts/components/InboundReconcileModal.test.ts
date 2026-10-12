import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import InboundReconcileModal from "@/domains/cohorts/components/InboundReconcileModal.vue"
import {applyInboundReconcileSelection, fetchInboundReconcilePreview, type InboundReconcilePreview} from "@/domains/cohorts/adapters/cohorts"
import {settle} from "../../../pages/helpers"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/cohorts/adapters/cohorts")>()),
  fetchInboundReconcilePreview: vi.fn(),
  applyInboundReconcileSelection: vi.fn(),
}))

const inline = {global: {stubs: {VDialog: {name: "VDialog", template: "<div><slot /></div>"}}}}

function preview(overrides: Partial<InboundReconcilePreview> = {}): InboundReconcilePreview {
  return {
    cohortLabel: "Paid this year",
    definitionKey: "contribution-paid",
    writerSupported: true,
    previewToken: "token-1",
    remoteCount: 2,
    matched: [
      {externalUserId: "ext-new", userId: 1, userFullName: "Ada", alreadyMember: false, writable: true},
      {externalUserId: "ext-in", userId: 2, userFullName: "Bob", alreadyMember: true, writable: false},
    ],
    skipped: [],
    ...overrides,
  }
}

async function open(answer: InboundReconcilePreview) {
  vi.mocked(fetchInboundReconcilePreview).mockResolvedValue(answer)
  const wrapper = mount(InboundReconcileModal, {...inline, props: {modelValue: false, cohortId: 10, targetId: 20}})
  await wrapper.setProps({modelValue: true})
  await settle()
  return wrapper
}

describe("the inbound reconcile modal", () => {
  beforeEach(() => vi.clearAllMocks())

  it("marks a matched account that is a member already apart from one it would add", async () => {
    const wrapper = await open(preview())

    expect(wrapper.get('[data-testid="inbound-match-ext-in"]').text()).toContain("Already a member")
    expect(wrapper.get('[data-testid="inbound-match-ext-new"]').text()).toContain("Writable")
  })

  it("names the cohort it cannot write to", async () => {
    const wrapper = await open(preview({writerSupported: false}))

    expect(wrapper.get('[data-testid="inbound-reconcile-unsupported"]').text())
      .toContain("Paid this year cannot be written from inbound reconcile.")
  })

  it("applies the selection for the cohort and target it was opened for", async () => {
    vi.mocked(applyInboundReconcileSelection).mockResolvedValue({jobId: 3, acceptedCount: 1, skippedCount: 1})
    const wrapper = await open(preview())

    await wrapper.get('[data-testid="inbound-reconcile-apply"]').trigger("click")
    await settle()

    expect(vi.mocked(applyInboundReconcileSelection).mock.calls[0]?.slice(0, 2)).toEqual([10, 20])
    expect(wrapper.emitted("applied")).toHaveLength(1)
  })
})
