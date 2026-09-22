import {describe, expect, it, vi} from "vitest"
import {deleteAddress} from "@/domains/user/adapters/users"
import {deleteAddressById} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  deleteAddressById: vi.fn(),
}))

describe("deleteAddress", () => {
  it("addresses the address by its number, and throws on a refusal", async () => {
    vi.mocked(deleteAddressById).mockResolvedValue({} as never)

    await deleteAddress(11)

    expect(deleteAddressById).toHaveBeenCalledWith({path: {id: 11}, throwOnError: true})
  })
})
