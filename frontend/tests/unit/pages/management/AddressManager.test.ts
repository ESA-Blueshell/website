import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import AddressManager from "@/pages/management/AddressManager.vue"
import {settle} from "../helpers"

const {
  mockFindUsers,
  mockFindAllAddresses,
} = vi.hoisted(() => ({
  mockFindUsers: vi.fn(),
  mockFindAllAddresses: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  findUsers: mockFindUsers,
  findAllAddresses: mockFindAllAddresses,
}))

describe("AddressManager page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindUsers.mockResolvedValue({
      status: 200,
      data: {
        content: [
          {id: 1, username: "alice", addressId: 10},
          {id: 2, username: "bob"},
        ],
      },
    })
    mockFindAllAddresses.mockResolvedValue({
      status: 200,
      data: [
        {id: 10, userId: 1, city: "Enschede"},
      ],
    })
  })

  it("loads users/addresses and upserts changed addresses", async () => {
    const wrapper = shallowMount(AddressManager, {
      global: {
        stubs: {
          AddressUserList: true,
        },
      },
    })

    await settle()

    expect(mockFindUsers).toHaveBeenCalledTimes(1)
    expect(mockFindAllAddresses).toHaveBeenCalledTimes(1)
    expect((wrapper.vm as any).usersWithAddress).toHaveLength(1)
    expect((wrapper.vm as any).usersWithoutAddress).toHaveLength(1)

    ;(wrapper.vm as any).addressChanged({id: 10, userId: 1, city: "Utrecht"})
    expect((wrapper.vm as any).addresses).toHaveLength(1)
    expect((wrapper.vm as any).addresses[0].city).toBe("Utrecht")

    ;(wrapper.vm as any).addressChanged({id: 11, userId: 2, city: "Amsterdam"})
    expect((wrapper.vm as any).addresses).toHaveLength(2)
  })

  it("toggles expanded row id", () => {
    const wrapper = shallowMount(AddressManager)

    ;(wrapper.vm as any).toggleExpanded(5)
    expect((wrapper.vm as any).expanded).toBe(5)

    ;(wrapper.vm as any).toggleExpanded(5)
    expect((wrapper.vm as any).expanded).toBe(0)
  })
})
