import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import StartMembershipDialog from "@/components/common/modals/StartMembershipDialog.vue"

const {mockBoardCreateMembership, mockApply, mockHandleNetworkError} = vi.hoisted(() => ({
  mockBoardCreateMembership: vi.fn(),
  mockApply: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  boardCreateMembership: mockBoardCreateMembership,
  MemberType: {
    REGULAR: "REGULAR",
  },
}))

vi.mock("@/plugins/validation.ts", () => ({
  apply: mockApply,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

vi.mock("@/components/form/fields/VvField.vue", () => ({
  default: {
    name: "VvField",
    template: "<div />",
  },
}))

vi.mock("@/components/form/fields/MemberTypeSelect.vue", () => ({
  default: {
    name: "MemberTypeSelect",
    template: "<div />",
  },
}))

describe("StartMembershipDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockBoardCreateMembership.mockResolvedValue({data: {id: 33, userId: 7}})
    mockApply.mockReturnValue(false)
  })

  it("creates membership and closes dialog", async () => {
    const wrapper = mount(StartMembershipDialog, {
      props: {
        modelValue: true,
        userId: 7,
      },
      global: {
        stubs: {
          Form: true,
          VvField: true,
          MemberTypeSelect: true,
        },
      },
    })

    ;(wrapper.vm as any).formRef = {
      validate: vi.fn().mockResolvedValue({valid: true}),
    }

    await (wrapper.vm as any).confirm()

    expect(mockBoardCreateMembership).toHaveBeenCalledWith({
      path: {userId: 7},
      body: expect.objectContaining({userId: 7}),
      throwOnError: true,
    })
    expect(wrapper.emitted("update:membership")?.[0]).toEqual([{id: 33, userId: 7}])
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false])
  })
})
