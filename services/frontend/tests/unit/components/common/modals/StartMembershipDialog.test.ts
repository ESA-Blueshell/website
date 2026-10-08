import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import StartMembershipDialog from "@/components/common/modals/StartMembershipDialog.vue"

const {mockStartMembershipAsBoard, mockHandleNetworkError} = vi.hoisted(() => ({
  mockStartMembershipAsBoard: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/user", () => ({
  startMembershipAsBoard: mockStartMembershipAsBoard,
  MemberType: {
    REGULAR: "REGULAR",
  },
}))

vi.mock("@/plugins/handleNetworkError", () => ({
  $handleNetworkError: mockHandleNetworkError,
  $showStatusMessage: vi.fn(),
}))

const mountDialog = () => mount(StartMembershipDialog, {
  props: {modelValue: true, userId: 7},
  global: {stubs: {VDialog: {template: "<div><slot /></div>"}}},
})

describe("StartMembershipDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStartMembershipAsBoard.mockResolvedValue({id: 33, userId: 7})
  })

  it("creates membership and closes dialog", async () => {
    const wrapper = mountDialog()

    await (wrapper.vm as any).confirm()

    expect(mockStartMembershipAsBoard)
      .toHaveBeenCalledWith(7, expect.objectContaining({userId: 7}))
    expect(wrapper.emitted("update:membership")?.[0]).toEqual([{id: 33, userId: 7}])
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false])
  })

  it("asks for a start date before it starts anything", async () => {
    const wrapper = mountDialog()
    const startDate = wrapper.findComponent({name: "FormControl"})
    await startDate.vm.$emit("update:modelValue", "")

    await (wrapper.vm as any).confirm()
    await wrapper.vm.$nextTick()

    expect(mockStartMembershipAsBoard).not.toHaveBeenCalled()
    expect(startDate.props("errorMessages")).toEqual(["This field is required"])
  })

  it("puts the api's refusal on the start date", async () => {
    mockStartMembershipAsBoard.mockRejectedValue({
      response: {status: 400, data: {errors: [{field: "startDate", message: "Overlaps a running membership."}]}},
    })
    const wrapper = mountDialog()

    await (wrapper.vm as any).confirm()
    await flushPromises()

    expect(wrapper.findComponent({name: "FormControl"}).props("errorMessages")).toEqual(["Overlaps a running membership."])
    expect(mockHandleNetworkError).not.toHaveBeenCalled()
  })
})
