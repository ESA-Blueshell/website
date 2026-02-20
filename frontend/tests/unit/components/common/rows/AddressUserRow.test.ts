import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import AddressUserRow from "@/components/common/rows/AddressUserRow.vue"

const {mockDeleteAddressById} = vi.hoisted(() => ({
  mockDeleteAddressById: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  deleteAddressById: mockDeleteAddressById,
}))

vi.mock("@/components/form/AddressForm.vue", () => ({
  default: {
    name: "AddressForm",
    template: "<div />",
  },
}))

describe("AddressUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDeleteAddressById.mockResolvedValue({})
  })

  it("toggles expanded row and deletes address", async () => {
    const wrapper = mount(AddressUserRow, {
      props: {
        user: {id: 1, fullName: "Emma", username: "emma", addressId: 11},
        addresses: [{id: 11, userId: 1}],
        expanded: 0,
      },
      global: {
        stubs: {
          AddressForm: true,
          DeleteConfirmationDialog: true,
        },
      },
    })

    ;(wrapper.vm as any).toggleExpanded()
    expect(wrapper.emitted("update:expanded")?.[0]).toEqual([1])

    await (wrapper.vm as any).confirmDeleteAddress()
    expect(mockDeleteAddressById).toHaveBeenCalledWith({path: {id: 11}})
    expect(wrapper.emitted("delete:address")?.[0]).toEqual([11])
  })
})
