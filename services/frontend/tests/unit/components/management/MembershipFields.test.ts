import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import MembershipFields from "@/components/management/MembershipFields.vue"
import {MemberType, type MembershipResponse} from "@/services/api"

const user = vi.hoisted(() => ({saveMembership: vi.fn(), startMembershipAsBoard: vi.fn()}))
const {mockHandleSubmitError} = vi.hoisted(() => ({mockHandleSubmitError: vi.fn()}))

vi.mock("@/domains/user", async (importOriginal) => ({...(await importOriginal<typeof import("@/domains/user")>()), ...user}))
vi.mock("@/composables/formUtils", () => ({handleSubmitError: mockHandleSubmitError}))

const held: MembershipResponse = {
  id: 9, userId: 42, startDate: "2025-01-01", endDate: null, memberType: MemberType.ALUMNI,
  pending: false, version: 3, createdAt: "", updatedAt: "",
}

describe("a membership's fields", () => {
  const fields = (membership?: MembershipResponse) => mount(MembershipFields, {
    props: {userId: 42, membership, submitText: membership ? "Save" : "Add membership", submitTestid: "membership-save"},
  })
  const input = (wrapper: ReturnType<typeof fields>, name: string) => wrapper.get(`[data-testid="membership-form-${name}"] input`)
  const submit = async (wrapper: ReturnType<typeof fields>) => {
    await wrapper.get("form").trigger("submit")
    await flushPromises()
  }

  beforeEach(() => vi.clearAllMocks())

  it("adds a membership with no incasso tick, since how somebody pays stands on them", async () => {
    user.startMembershipAsBoard.mockResolvedValue({...held, id: 10})
    const wrapper = fields()

    expect(wrapper.text()).not.toContain("Incasso")
    await submit(wrapper)
    expect(wrapper.get('[data-testid="membership-form-start-date"]').text()).toContain("Fill in the start date.")
    expect(user.startMembershipAsBoard).not.toHaveBeenCalled()

    await input(wrapper, "start-date").setValue("2026-09-01")
    wrapper.findComponent({name: "MemberTypeSelect"}).vm.$emit("update:modelValue", MemberType.HONORARY)
    await submit(wrapper)

    expect(user.startMembershipAsBoard).toHaveBeenCalledWith(42, {userId: 42, startDate: "2026-09-01", endDate: null, memberType: MemberType.HONORARY})
    expect(wrapper.emitted("saved")?.[0]).toEqual([{...held, id: 10}])
    expect(wrapper.get('[data-testid="membership-save"]').attributes("data-submit-mode")).toBe("create")
  })

  it("edits a membership and leaves how it is paid as it was", async () => {
    user.saveMembership.mockResolvedValue({...held, endDate: "2026-08-31"})
    const wrapper = fields(held)

    expect((input(wrapper, "start-date").element as HTMLInputElement).value).toBe("01/01/2025")
    expect(wrapper.findComponent({name: "MemberTypeSelect"}).props("modelValue")).toBe(MemberType.ALUMNI)
    await input(wrapper, "end-date").setValue("2026-08-31")
    await submit(wrapper)

    expect(user.saveMembership).toHaveBeenCalledWith(9, expect.objectContaining({version: 3, startDate: "2025-01-01", endDate: "2026-08-31", memberType: MemberType.ALUMNI}))
    expect(wrapper.emitted("saved")).toHaveLength(1)
    expect(wrapper.get('[data-testid="membership-save"]').attributes("data-submit-mode")).toBe("update")
  })

  it("puts what the api refuses on the field it names, and saves nothing", async () => {
    const refusal = new Error("refused")
    user.saveMembership.mockRejectedValue(refusal)
    mockHandleSubmitError.mockImplementation((form: {setFieldError: (field: string, message: string | string[]) => void}) => {
      form.setFieldError("endDate", ["The end is before the start."])
      form.setFieldError("startDate", "Overlaps another membership.")
    })
    const wrapper = fields(held)

    await submit(wrapper)

    expect(mockHandleSubmitError.mock.calls[0]?.[1]).toBe(refusal)
    expect(wrapper.get('[data-testid="membership-form-end-date"]').text()).toContain("The end is before the start.")
    expect(wrapper.get('[data-testid="membership-form-start-date"]').text()).toContain("Overlaps another membership.")
    expect(wrapper.emitted("saved")).toBeUndefined()
    expect(wrapper.get('[data-testid="membership-save"]').attributes("disabled")).toBeUndefined()
  })
})
