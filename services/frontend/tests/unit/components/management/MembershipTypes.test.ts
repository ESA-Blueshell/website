import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import MembershipTypes from "@/components/management/MembershipTypes.vue"
import {MemberType, type MembershipResponse} from "@/services/api"
import {aMembership} from "../../helpers/apiFixtures"
import {sortByEveryHead} from "../../pages/helpers"

const user = vi.hoisted(() => ({recordTypeOn: vi.fn(), correctTypeOn: vi.fn(), removeTypeFrom: vi.fn()}))
const status = vi.hoisted(() => ({$showStatusMessage: vi.fn(), $handleNetworkError: vi.fn()}))

vi.mock("@/domains/user", async (importOriginal) => ({...(await importOriginal<typeof import("@/domains/user")>()), ...user}))
vi.mock("@/plugins/handleNetworkError.ts", () => status)

const held: MembershipResponse = aMembership({
  id: 9,
  startDate: "2022-09-01",
  memberType: MemberType.ALUMNI,
  types: [
    {id: 1, type: MemberType.REGULAR, since: "2022-09-01"},
    {id: 2, type: MemberType.ALUMNI, since: "2025-09-01"},
  ],
})

describe("a membership's types", () => {
  const types = () => mount(MembershipTypes, {props: {membership: held}})
  const submit = async (wrapper: ReturnType<typeof types>, testid: string) => {
    await wrapper.get(`[data-testid="${testid}"]`).element.closest("form")!.dispatchEvent(new Event("submit"))
    await flushPromises()
  }

  beforeEach(() => vi.clearAllMocks())

  it("lists each type with its day, sorts by either head, and only a later type can be removed", async () => {
    const wrapper = types()
    expect(await sortByEveryHead(wrapper)).toBe(2)

    expect(wrapper.get('[data-testid="manage-membership-type-row-1"]').text()).toContain("Regular")
    expect(wrapper.get('[data-testid="manage-membership-type-row-2"]').text()).toContain("1 Sep 2025")
    expect(wrapper.find('[data-testid="manage-membership-type-remove-btn-1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="manage-membership-type-remove-btn-2"]').exists()).toBe(true)
  })

  it("adds a type from a day and hands back the membership the api answers", async () => {
    const answered = {...held, memberType: MemberType.HONORARY}
    user.recordTypeOn.mockResolvedValue({ok: true, saved: answered})
    const wrapper = types()

    await submit(wrapper, "manage-membership-type-add-btn-9")
    expect(wrapper.get('[data-testid="manage-membership-type-add-btn-9-since"]').text()).toContain("Fill in the day this type starts.")
    expect(user.recordTypeOn).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="manage-membership-type-add-btn-9-since"] input').setValue("2026-01-01")
    wrapper.findAllComponents({name: "MemberTypeSelect"}).at(-1)!.vm.$emit("update:modelValue", MemberType.HONORARY)
    await submit(wrapper, "manage-membership-type-add-btn-9")

    expect(user.recordTypeOn).toHaveBeenCalledWith(9, {type: MemberType.HONORARY, since: "2026-01-01"})
    expect(wrapper.emitted("changed")?.[0]).toEqual([answered])
  })

  it("puts a refusal under the day, and changes nothing", async () => {
    user.recordTypeOn.mockResolvedValue({ok: false, reason: "Another membership type starts on 1 September 2025."})
    const wrapper = types()

    await wrapper.get('[data-testid="manage-membership-type-add-btn-9-since"] input').setValue("2025-09-01")
    await submit(wrapper, "manage-membership-type-add-btn-9")

    expect(wrapper.get('[data-testid="manage-membership-type-add-btn-9-since"]').text()).toContain("Another membership type starts")
    expect(wrapper.emitted("changed")).toBeUndefined()
  })

  it("corrects the first type's kind on the membership's start, its day fixed", async () => {
    user.correctTypeOn.mockResolvedValue({ok: true, saved: held})
    const wrapper = types()

    await wrapper.get('[data-testid="manage-membership-type-edit-btn-1"]').trigger("click")
    expect(wrapper.get('[data-testid="manage-membership-type-save-btn-1-since"] input').attributes("disabled")).toBeDefined()
    wrapper.findAllComponents({name: "MemberTypeSelect"}).at(0)!.vm.$emit("update:modelValue", MemberType.ALUMNI)
    await submit(wrapper, "manage-membership-type-save-btn-1")

    expect(user.correctTypeOn).toHaveBeenCalledWith(9, 1, {type: MemberType.ALUMNI, since: "2022-09-01"})
    expect(wrapper.emitted("changed")).toHaveLength(1)
  })

  it("removes a later type, or says why not", async () => {
    user.removeTypeFrom.mockResolvedValueOnce({ok: false, reason: "That membership type is gone. Reload the page."})
    const wrapper = types()

    await wrapper.get('[data-testid="manage-membership-type-remove-btn-2"]').trigger("click")
    await flushPromises()
    expect(status.$showStatusMessage).toHaveBeenCalledWith("That membership type is gone. Reload the page.")

    user.removeTypeFrom.mockResolvedValueOnce({ok: true, saved: held})
    await wrapper.get('[data-testid="manage-membership-type-remove-btn-2"]').trigger("click")
    await flushPromises()
    expect(user.removeTypeFrom).toHaveBeenLastCalledWith(9, 2)
    expect(wrapper.emitted("changed")).toHaveLength(1)
  })
})
