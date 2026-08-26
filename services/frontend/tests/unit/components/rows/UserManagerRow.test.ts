import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import UserManagerRow from "@/components/common/rows/UserManagerRow.vue"
import UserManagerMobileRow from "@/components/common/rows/UserManagerMobileRow.vue"
import type {MemberRow} from "@/composables/useUserRows"
import {MemberType} from "@/services/api"

/**
 * The rows are their own components, so everything the page used to do inline now crosses a
 * boundary as an event. These cover that boundary: what each control emits, and what the row
 * renders from its props rather than deciding for itself.
 *
 * The re-render isolation these components exist for is covered separately, against the page.
 */
function memberRow(overrides: Partial<MemberRow> = {}): MemberRow {
  return {
    id: 7,
    fullName: "Emma Dokter",
    username: "emma",
    role: "member",
    status: "Current",
    memberSince: "2025-01-01",
    latestType: MemberType.REGULAR,
    latestIncasso: false,
    paid: true,
    wasMemberInPeriod: true,
    ...overrides,
  }
}

/**
 * Vuetify is not installed in the unit environment, so its components would render as
 * unresolved elements — which drops slot content and never produces the `input` a checkbox is
 * driven by. These stubs are the smallest thing that behaves like the real component from the
 * point of view of the contract under test: props in, events out.
 */
const stubs = {
  VTooltip: {template: "<div><slot name='activator' :props='{}' /></div>"},
  VCheckboxBtn: {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: `<input
      type="checkbox"
      :checked="modelValue"
      @change="$emit('update:modelValue', !modelValue)"
    >`,
  },
  VBtn: {props: ["disabled", "loading"], template: "<button :disabled='disabled'><slot /></button>"},
  VIcon: {props: ["icon"], template: "<i :data-icon='icon'></i>"},
  VChip: {template: "<span><slot /></span>"},
  VListItem: {template: "<div><slot name='append' /><slot /></div>"},
  VListItemTitle: {template: "<div><slot /></div>"},
  VListItemSubtitle: {template: "<div><slot /></div>"},
}

const desktop = (props: Record<string, unknown> = {}) =>
  mount(UserManagerRow, {
    props: {row: memberRow(), selected: false, saving: false, toggleDisabled: false, ...props},
    global: {stubs},
  })

describe("UserManagerRow", () => {
  it("asks the page to toggle selection, naming the row", async () => {
    const wrapper = desktop()

    await wrapper.find('[data-testid="member-manager-checkbox-7"]').setValue(true)

    expect(wrapper.emitted("toggle-selection")).toEqual([[7]])
  })

  it("asks the page to toggle payment, naming the row", async () => {
    const wrapper = desktop()

    await wrapper.find('[data-testid="member-manager-toggle-paid-btn-7"]').trigger("click")

    expect(wrapper.emitted("toggle-paid")).toEqual([[7]])
  })

  it("emits the row itself for the actions that need more than an id", async () => {
    const wrapper = desktop()

    await wrapper.find('[data-testid="member-manager-manage-membership-btn-7"]').trigger("click")
    await wrapper.find('[data-testid="member-manager-edit-profile-btn-7"]').trigger("click")
    await wrapper.find('[data-testid="member-manager-delete-btn-7"]').trigger("click")

    expect(wrapper.emitted("manage-membership")?.[0]?.[0]).toMatchObject({id: 7})
    expect(wrapper.emitted("edit-profile")?.[0]?.[0]).toMatchObject({id: 7})
    expect(wrapper.emitted("delete")?.[0]?.[0]).toMatchObject({id: 7})
  })

  it("takes selection from the prop rather than keeping its own", () => {
    const checkbox = desktop({selected: true})
      .find('[data-testid="member-manager-checkbox-7"]').element as HTMLInputElement

    expect(checkbox.checked).toBe(true)
  })

  it("marks a selected row so the stripe and the highlight can tell them apart", () => {
    expect(desktop({selected: true}).classes()).toContain("mm-row--selected")
    expect(desktop({selected: false}).classes()).not.toContain("mm-row--selected")
  })

  it("will not delete an admin", () => {
    const wrapper = desktop({row: memberRow({role: "admin"})})

    expect(wrapper.find('[data-testid="member-manager-delete-btn-7"]').attributes("disabled"))
      .toBeDefined()
  })

  it("disables the paid toggle when there is no period to mark against", () => {
    const wrapper = desktop({toggleDisabled: true})

    expect(wrapper.find('[data-testid="member-manager-toggle-paid-btn-7"]').attributes("disabled"))
      .toBeDefined()
  })

  it("shows the type marker only for a notable membership type", () => {
    expect(desktop().find('[data-testid="member-manager-type-incasso-7"]').html())
      .not.toContain("mdi-crown")

    expect(desktop({row: memberRow({latestType: MemberType.HONORARY})})
      .find('[data-testid="member-manager-type-incasso-7"]').html()).toContain("mdi-crown")
    expect(desktop({row: memberRow({latestType: MemberType.ALUMNI})})
      .find('[data-testid="member-manager-type-incasso-7"]').html()).toContain("mdi-school")
  })

  it("shows the incasso marker only when incasso is active", () => {
    expect(desktop().find('[data-testid="member-manager-type-incasso-7"]').html())
      .not.toContain("mdi-bank-transfer")
    expect(desktop({row: memberRow({latestIncasso: true})})
      .find('[data-testid="member-manager-type-incasso-7"]').html())
      .toContain("mdi-bank-transfer")
  })

  it("says so when a member has no start date rather than leaving the cell empty", () => {
    const wrapper = desktop({row: memberRow({memberSince: null})})

    expect(wrapper.find('[data-testid="member-manager-member-since-7"]').text()).toBe("—")
  })
})

describe("UserManagerMobileRow", () => {
  const mobile = (props: Record<string, unknown> = {}) =>
    mount(UserManagerMobileRow, {
      props: {row: memberRow(), saving: false, toggleDisabled: false, ...props},
      global: {stubs},
    })

  it("carries the same actions as the desktop row, minus selection", async () => {
    const wrapper = mobile()

    // The narrow layout offers no bulk selection, so it has no checkbox to reconcile.
    expect(wrapper.find('[data-testid="member-manager-checkbox-7"]').exists()).toBe(false)

    await wrapper.find('[data-testid="member-manager-mobile-toggle-paid-btn-7"]').trigger("click")
    await wrapper.find('[data-testid="member-manager-mobile-manage-membership-btn-7"]').trigger("click")
    await wrapper.find('[data-testid="member-manager-mobile-edit-profile-btn-7"]').trigger("click")
    await wrapper.find('[data-testid="member-manager-mobile-delete-btn-7"]').trigger("click")

    expect(wrapper.emitted("toggle-paid")).toEqual([[7]])
    expect(wrapper.emitted("manage-membership")?.[0]?.[0]).toMatchObject({id: 7})
    expect(wrapper.emitted("edit-profile")?.[0]?.[0]).toMatchObject({id: 7})
    expect(wrapper.emitted("delete")?.[0]?.[0]).toMatchObject({id: 7})
  })

  it("will not delete an admin either", () => {
    const wrapper = mobile({row: memberRow({role: "admin"})})

    expect(wrapper.find('[data-testid="member-manager-mobile-delete-btn-7"]').attributes("disabled"))
      .toBeDefined()
  })
})
