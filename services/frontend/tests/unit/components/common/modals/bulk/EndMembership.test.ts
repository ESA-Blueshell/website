import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EndMembershipDialog from "@/components/common/modals/bulk/EndMembershipDialog.vue"
import {BulkRowDisposition, BulkRowReason} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"
import {noMembershipTarget, target} from "../../../../helpers/bulkFixtures"

const {mockPreviewBulkEnd, mockEndMemberships} = vi.hoisted(() => ({
  mockPreviewBulkEnd: vi.fn(),
  mockEndMemberships: vi.fn(),
}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  previewBulkEnd: mockPreviewBulkEnd,
  endMemberships: mockEndMemberships,
}))

const TODAY = "2026-08-31"

function previewOf(rows: Array<{userId: number; disposition: BulkRowDisposition; reason?: BulkRowReason}>) {
  return {data: {effectiveDate: TODAY, rows}, response: {status: 200}}
}

function mountDialog(targets = [target(1)]) {
  return mount(EndMembershipDialog, {props: {modelValue: true, targets}})
}

describe("EndMembershipDialog", () => {
  it("renders the rows the api decided, not ones it worked out itself", async () => {
    mockPreviewBulkEnd.mockResolvedValue(
      previewOf([
        {userId: 1, disposition: BulkRowDisposition.INCLUDED},
        {userId: 2, disposition: BulkRowDisposition.SKIPPED, reason: BulkRowReason.NO_ACTIVE_MEMBERSHIP},
      ]),
    )

    const wrapper = mountDialog([target(1), noMembershipTarget(2)])
    await settle()

    expect(mockPreviewBulkEnd).toHaveBeenCalledWith({body: {userIds: [1, 2]}})
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="bulk-preview-note-2"]').text()).toContain("No active membership")
  })

  it("states the api's effective date rather than the browser's", async () => {
    mockPreviewBulkEnd.mockResolvedValue(previewOf([{userId: 1, disposition: BulkRowDisposition.INCLUDED}]))

    const wrapper = mountDialog()
    await settle()

    expect(wrapper.find('[data-testid="bulk-membership-effective-date"]').text()).toContain("31/08/2026")
  })

  it("sends the whole previewed selection and reports what came back", async () => {
    mockPreviewBulkEnd.mockResolvedValue(
      previewOf([
        {userId: 1, disposition: BulkRowDisposition.INCLUDED},
        {userId: 2, disposition: BulkRowDisposition.SKIPPED, reason: BulkRowReason.STARTED_TODAY},
      ]),
    )
    mockEndMemberships.mockResolvedValue({data: {applied: 1, skipped: 1, queued: 0}, response: {status: 200}})

    const wrapper = mountDialog([target(1), target(2)])
    await settle()

    await wrapper.find('[data-testid="bulk-action-confirm-btn"]').trigger("click")
    await settle()

    expect(mockEndMemberships).toHaveBeenCalledWith({body: {userIds: [1, 2]}})
    expect(wrapper.find('[data-testid="bulk-membership-result"]').text()).toContain("1 ended, 1 skipped")
  })

  it("keeps the dialog open and names the stale rows when the api refuses the selection", async () => {
    mockPreviewBulkEnd.mockResolvedValue({
      error: {
        errors: [
          {
            code: "UnknownUserIds",
            field: "userIds",
            message: "1 of the selected users no longer exist.",
            values: [2],
          },
        ],
      },
      response: {status: 409},
    })

    const wrapper = mountDialog([target(1), target(2, {name: "Grace Hopper"})])
    await settle()

    expect(wrapper.emitted("stale")).toHaveLength(1)
    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    const rejection = wrapper.find('[data-testid="bulk-membership-rejection"]')
    expect(rejection.text()).toContain("no longer exist")
    expect(rejection.text()).toContain("Grace Hopper")
  })

  it("asks for nothing when the selection is empty", async () => {
    mountDialog([])
    await settle()

    expect(mockPreviewBulkEnd).not.toHaveBeenCalled()
  })
})
