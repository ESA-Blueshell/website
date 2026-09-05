import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import BoardMemberDialog from "@/domains/boards/island/BoardMemberDialog.vue"
import {loadMemberAccounts} from "@/domains/user"
import {settle} from "../../../helpers/testUtils"

/**
 * The writes are the adapter's. What is left here is what the dialog says about the accounts a
 * membership may be attached to, which is the one place an unread list would read as a claim
 * about the association.
 */
vi.mock("@/domains/user", () => ({loadMemberAccounts: vi.fn()}))

// The dialog portals its content out of the component's subtree, so it is replaced by a
// pass-through: what is under test is what the form puts inside it.
const stubs = {
  IslandDialog: {
    props: ["open"],
    setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) =>
      () => h("div", [slots["default"]?.(), slots["footer"]?.()]),
  },
  ConfirmDialog: true,
  ImagePicker: true,
}

const openDialog = async () => {
  const wrapper = mount(BoardMemberDialog, {
    props: {open: true, boardId: 4, member: null},
    global: {stubs},
  })
  await settle()
  return wrapper
}

const note = "[data-testid='board-member-dialog-account-none']"

describe("BoardMemberDialog, on the accounts a membership may be attached to", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("says nobody has an account where the read came back empty", async () => {
    vi.mocked(loadMemberAccounts).mockResolvedValue([])

    const wrapper = await openDialog()

    expect(wrapper.find(note).text()).toBe("Nobody has an account here yet.")
  })

  it("says the accounts could not be read rather than that there are none", async () => {
    vi.mocked(loadMemberAccounts).mockResolvedValue(null)

    const wrapper = await openDialog()

    expect(wrapper.find(note).text()).toContain("could not be read")
  })

  it("offers the accounts it read, and says nothing about an empty list", async () => {
    vi.mocked(loadMemberAccounts)
      .mockResolvedValue([{id: 2, name: "Roos Kruk", email: "roos@esa.test"}])

    const wrapper = await openDialog()

    expect(wrapper.find(note).exists()).toBe(false)
  })
})
