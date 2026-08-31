import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import MembershipStatusDialog from "@/components/common/modals/bulk/MembershipStatusDialog.vue"
import {BulkRowDisposition, BulkRowReason} from "@/services/api"
import {settle} from "../../../../helpers/testUtils"
import {endedMemberTarget, noMembershipTarget, target} from "../../../../helpers/bulkFixtures"

const {mockPreviewBulkEnd, mockEndMemberships, mockPreviewBulkStart, mockStartMemberships} = vi.hoisted(() => ({
  mockPreviewBulkEnd: vi.fn(),
  mockEndMemberships: vi.fn(),
  mockPreviewBulkStart: vi.fn(),
  mockStartMemberships: vi.fn(),
}))
vi.mock("@/services/api/blueshell/sdk.gen", () => ({
  previewBulkEnd: mockPreviewBulkEnd,
  endMemberships: mockEndMemberships,
  previewBulkStart: mockPreviewBulkStart,
  startMemberships: mockStartMemberships,
}))

const TODAY = "2026-08-31"

function previewOf(rows: Array<{userId: number; disposition: BulkRowDisposition; reason?: BulkRowReason}>) {
  return {data: {effectiveDate: TODAY, rows}, response: {status: 200}}
}

function mountDialog(targetState: "end" | "start", targets = [target(1)]) {
  return mount(MembershipStatusDialog, {props: {modelValue: true, targetState, targets}})
}

describe("MembershipStatusDialog (End membership)", () => {
  it("renders the rows the api decided, not ones it worked out itself", async () => {
    mockPreviewBulkEnd.mockResolvedValue(
      previewOf([
        {userId: 1, disposition: BulkRowDisposition.INCLUDED},
        {userId: 2, disposition: BulkRowDisposition.SKIPPED, reason: BulkRowReason.NO_ACTIVE_MEMBERSHIP},
      ]),
    )

    const wrapper = mountDialog("end", [target(1), noMembershipTarget(2)])
    await settle()

    expect(mockPreviewBulkEnd).toHaveBeenCalledWith({body: {userIds: [1, 2]}})
    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Included")
    expect(wrapper.find('[data-testid="bulk-preview-disposition-2"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="bulk-preview-note-2"]').text()).toContain("No active membership")
  })

  it("states the api's effective date rather than the browser's", async () => {
    mockPreviewBulkEnd.mockResolvedValue(previewOf([{userId: 1, disposition: BulkRowDisposition.INCLUDED}]))

    const wrapper = mountDialog("end")
    await settle()

    const info = wrapper.find('[data-testid="bulk-membership-effective-date"]').text()
    expect(info).toContain("Memberships end on 31/08/2026")
  })

  it("sends the whole previewed selection and reports what came back", async () => {
    mockPreviewBulkEnd.mockResolvedValue(
      previewOf([
        {userId: 1, disposition: BulkRowDisposition.INCLUDED},
        {userId: 2, disposition: BulkRowDisposition.SKIPPED, reason: BulkRowReason.STARTED_TODAY},
      ]),
    )
    mockEndMemberships.mockResolvedValue({data: {applied: 1, skipped: 1, queued: 0}, response: {status: 200}})

    const wrapper = mountDialog("end", [target(1), target(2)])
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

    const wrapper = mountDialog("end", [target(1), target(2, {name: "Grace Hopper"})])
    await settle()

    expect(wrapper.emitted("stale")).toHaveLength(1)
    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    const rejection = wrapper.find('[data-testid="bulk-membership-rejection"]')
    expect(rejection.text()).toContain("no longer exist")
    expect(rejection.text()).toContain("Grace Hopper")
  })

  it("asks for nothing when the selection is empty", async () => {
    mountDialog("end", [])
    await settle()

    expect(mockPreviewBulkEnd).not.toHaveBeenCalled()
  })
})

describe("MembershipStatusDialog (Start membership)", () => {
  it("calls the start endpoints rather than the end ones", async () => {
    mockPreviewBulkStart.mockResolvedValue(
      previewOf([{userId: 1, disposition: BulkRowDisposition.INCLUDED, reason: BulkRowReason.WILL_START_NEW}]),
    )
    mockStartMemberships.mockResolvedValue({data: {applied: 1, skipped: 0, queued: 0}, response: {status: 200}})

    const wrapper = mountDialog("start", [endedMemberTarget(1)])
    await settle()

    expect(mockPreviewBulkStart).toHaveBeenCalledWith({body: {userIds: [1]}})
    expect(wrapper.text()).toContain("Start membership")
    expect(wrapper.find('[data-testid="bulk-membership-effective-date"]').text())
      .toContain("Memberships start on 31/08/2026")

    await wrapper.find('[data-testid="bulk-action-confirm-btn"]').trigger("click")
    await settle()

    expect(mockStartMemberships).toHaveBeenCalledWith({body: {userIds: [1]}})
    expect(mockEndMemberships).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="bulk-membership-result"]').text()).toContain("1 started, 0 skipped")
  })

  it("shows an already-active member as skipped with the reason", async () => {
    mockPreviewBulkStart.mockResolvedValue(
      previewOf([{userId: 1, disposition: BulkRowDisposition.SKIPPED, reason: BulkRowReason.ALREADY_ACTIVE}]),
    )

    const wrapper = mountDialog("start")
    await settle()

    expect(wrapper.find('[data-testid="bulk-preview-disposition-1"]').text()).toContain("Skipped")
    expect(wrapper.find('[data-testid="bulk-preview-note-1"]').text())
      .toContain("Already has an active membership")
  })

  it("keeps the joining date the table already shows, not the day the new spell starts", async () => {
    mockPreviewBulkStart.mockResolvedValue(
      previewOf([{userId: 1, disposition: BulkRowDisposition.INCLUDED, reason: BulkRowReason.WILL_START_NEW}]),
    )

    const wrapper = mountDialog("start", [endedMemberTarget(1)])
    await settle()

    expect(wrapper.find('[data-testid="bulk-preview-member-since-1"]').text()).toContain("01/01/2024")
  })
})
