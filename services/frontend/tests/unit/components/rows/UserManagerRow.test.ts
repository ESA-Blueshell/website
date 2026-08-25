import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import UserManagerRow from "@/components/common/rows/UserManagerRow.vue"
import UserManagerMobileRow from "@/components/common/rows/UserManagerMobileRow.vue"
import type {MemberRow} from "@/composables/useUserRows"
import {MemberType} from "@/services/api"

/**
 * The rows moved out of the page, so everything the page used to do inline now has to cross a
 * component boundary as an event. These check that it does, and that it carries what the page
 * needs to act on.
 */
function row(overrides: Partial<MemberRow> = {}): MemberRow {
  return {
    id: 7,
    fullName: "Emma Dokter",
    username: "emma",
    role: "member",
    status: "Current",
    memberSince: "2025-01-01",
    wasMemberInPeriod: true,
    paid: true,
    latestType: MemberType.REGULAR,
    latestIncasso: false,
    ...overrides,
  } as MemberRow
}

/**
 * Vuetify is not installed in the unit environment, so its components would otherwise render
 * as unresolved elements — which drops slot content and never produces the `input` a checkbox
 * is driven by. These stubs are the smallest thing that behaves like the real component from
 * the point of view of the contract under test: props in, events out.
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
  VBtn: {
    props: ["disabled", "loading"],
    template: "<button :disabled='disabled'><slot /></button>",
  },
  VIcon: {props: ["icon"], template: "<i :data-icon='icon'></i>"},
  VChip: {template: "<span><slot /></span>"},
  VListItem: {
    template: "<div><slot name='append' /><slot /></div>",
  },
  VListItemTitle: {template: "<div><slot /></div>"},
  VListItemSubtitle: {template: "<div><slot /></div>"},
}

const desktop = (props: Partial<InstanceType<typeof UserManagerRow>["$props"]> = {}) =>
  mount(UserManagerRow, {
    props: {row: row(), selected: false, saving: false, toggleDisabled: false, ...props},
    global: {stubs},
  })

describe("UserManagerRow", () => {
  it("asks the page to toggle selection, naming the row", async () => {
    const wrapper = desktop()

    await wrapper.find('[data-testid="member-manager-checkbox-7"]').setValue(true)

    expect(wrapper.emitted("toggle-selected")).toEqual([[7]])
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
    expect(wrapper.emitted("delete-user")?.[0]?.[0]).toMatchObject({id: 7})
  })

  it("reflects selection from the prop rather than keeping its own", () => {
    const wrapper = desktop({selected: true})

    const input = wrapper.find('[data-testid="member-manager-checkbox-7"]')
      .element as HTMLInputElement
    expect(input.checked).toBe(true)
  })

  it("will not delete an admin", () => {
    const wrapper = desktop({row: row({role: "admin"})})

    expect(wrapper.find('[data-testid="member-manager-delete-btn-7"]').attributes("disabled"))
      .toBeDefined()
  })

  it("disables the paid toggle when there is no period to mark against", () => {
    const wrapper = desktop({toggleDisabled: true})

    expect(wrapper.find('[data-testid="member-manager-toggle-paid-btn-7"]').attributes("disabled"))
      .toBeDefined()
  })

  it("shows the honorary marker only for a notable type", () => {
    expect(desktop().find('[data-testid="member-manager-type-incasso-7"]').text()).toBe("")

    const honorary = desktop({row: row({latestType: MemberType.HONORARY})})
    expect(honorary.find('[data-testid="member-manager-type-incasso-7"]').html())
      .toContain("mdi-crown")
  })
})

describe("UserManagerMobileRow", () => {
  const mobile = () => mount(UserManagerMobileRow, {
    props: {row: row(), saving: false, toggleDisabled: false},
    global: {stubs},
  })

  it("carries the same actions as the desktop row, minus selection", async () => {
    const wrapper = mobile()

    expect(wrapper.find('[data-testid="member-manager-checkbox-7"]').exists()).toBe(false)

    await wrapper.find('[data-testid="member-manager-mobile-toggle-paid-btn-7"]').trigger("click")
    await wrapper.find('[data-testid="member-manager-mobile-edit-profile-btn-7"]').trigger("click")

    expect(wrapper.emitted("toggle-paid")).toEqual([[7]])
    expect(wrapper.emitted("edit-profile")?.[0]?.[0]).toMatchObject({id: 7})
  })
})
