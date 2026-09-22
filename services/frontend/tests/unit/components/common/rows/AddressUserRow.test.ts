import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import AddressUserRow from "@/components/common/rows/AddressUserRow.vue"

const {mockDeleteAddress, mockHandleNetworkError} = vi.hoisted(() => ({
  mockDeleteAddress: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/domains/user", () => ({
  deleteAddress: mockDeleteAddress,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/components/form/AddressForm.vue", () => ({
  default: {
    name: "AddressForm",
    template: "<div />",
  },
}))

const row = (props: Record<string, unknown> = {}) =>
  mount(AddressUserRow, {
    props: {
      user: {id: 1, fullName: "Emma", username: "emma", addressId: 11},
      addresses: [{id: 11, userId: 1}],
      expanded: 0,
      ...props,
    },
    global: {stubs: {AddressForm: true, DeleteConfirmationDialog: true}},
  })

describe("AddressUserRow", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDeleteAddress.mockResolvedValue(undefined)
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
    expect(mockDeleteAddress).toHaveBeenCalledWith(11)
    expect(wrapper.emitted("delete:address")?.[0]).toEqual([11])
  })

  // The row stays on a refused removal: the address is still on file.
  it("reports a removal the api refused, and says nothing was removed", async () => {
    mockDeleteAddress.mockRejectedValue(new Error("refused"))
    const wrapper = row()

    await (wrapper.vm as any).confirmDeleteAddress()

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(wrapper.emitted("delete:address")).toBeUndefined()
  })

  // An address answers to the account it names as well as to the one that names it.
  it("finds the address that names the account, when the account names none", () => {
    const wrapper = row({
      user: {id: 1, fullName: "Emma", username: "emma"},
      addresses: [{id: 11, userId: 1}],
    })

    expect((wrapper.vm as any).hasAddress).toBe(true)
  })

  it("closes the row on a save with nothing on file to pass up", () => {
    const wrapper = row({user: {id: 1, fullName: "Emma", username: "emma"}, addresses: []})

    ;(wrapper.vm as any).onSubmitted(true)

    expect(wrapper.emitted("update:address")).toBeUndefined()
    expect(wrapper.emitted("update:expanded")?.[0]).toEqual([0])
  })

  it("removes nothing for an account with no address on file", async () => {
    const wrapper = row({user: {id: 1, fullName: "Emma", username: "emma"}, addresses: []})

    await (wrapper.vm as any).confirmDeleteAddress()

    expect(mockDeleteAddress).not.toHaveBeenCalled()
  })

  it("passes an edited address up, and ignores an empty one", async () => {
    const wrapper = row()

    ;(wrapper.vm as any).addressModel = {id: 11, userId: 1, city: "Enschede"}
    expect(wrapper.emitted("update:address")?.[0]).toEqual([{id: 11, userId: 1, city: "Enschede"}])

    ;(wrapper.vm as any).addressModel = undefined
    expect(wrapper.emitted("update:address")).toHaveLength(1)
  })

  it("closes the row and passes the address up once the form saved", async () => {
    const wrapper = row()

    ;(wrapper.vm as any).onSubmitted(false)
    expect(wrapper.emitted("update:expanded")).toBeUndefined()

    ;(wrapper.vm as any).onSubmitted(true)
    expect(wrapper.emitted("update:address")?.[0]).toEqual([{id: 11, userId: 1}])
    expect(wrapper.emitted("update:expanded")?.[0]).toEqual([0])
  })

  it("opens the confirmation before removing anything", () => {
    const wrapper = row()

    ;(wrapper.vm as any).openDelete()

    expect((wrapper.vm as any).deleteDialog).toBe(true)
  })

  // Both children write back through v-model, so the row has to carry their writes.
  it("carries what the form and the dialog write back", async () => {
    const wrapper = mount(AddressUserRow, {
      props: {
        user: {id: 1, fullName: "Emma", username: "emma", addressId: 11},
        addresses: [{id: 11, userId: 1}],
        expanded: 1,
      },
      global: {
        stubs: {
          AddressForm: {
            props: ["modelValue"],
            template: "<button data-test='edit' @click=\"$emit('update:modelValue', {id: 11, city: 'Enschede'})\">{{ modelValue?.id }}</button>",
          },
          DeleteConfirmationDialog: {
            props: ["modelValue"],
            template: "<button data-test='close' @click=\"$emit('update:modelValue', false)\" />",
          },
        },
      },
    })

    expect(wrapper.get("[data-test='edit']").text()).toBe("11")

    await wrapper.get("[data-test='edit']").trigger("click")
    expect(wrapper.emitted("update:address")?.[0]).toEqual([{id: 11, city: "Enschede"}])

    await wrapper.get("[data-test='close']").trigger("click")
    expect((wrapper.vm as any).deleteDialog).toBe(false)
  })

  it("names the last role the account holds", () => {
    const wrapper = row({
      user: {id: 1, fullName: "Emma", username: "emma", addressId: 11, roles: ["USER", "BOARD"]},
      addresses: [{id: 11, userId: 1}],
    })

    expect(wrapper.text().toLowerCase()).toContain("board")
  })
})
