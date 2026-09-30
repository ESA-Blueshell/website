import {flushPromises, mount} from "@vue/test-utils"
import {beforeEach, describe, expect, it, vi} from "vitest"
import {Role} from "@/services/api"
import UserRolesPanel from "@/domains/user/components/UserRolesPanel.vue"

const user = vi.hoisted(() => ({listRoleChanges: vi.fn(), readRoleStanding: vi.fn(), saveRolesOrReason: vi.fn()}))
vi.mock("@/domains/user", async importOriginal => ({...(await importOriginal<typeof import("@/domains/user")>()), ...user}))

const standing = (granted: Role[]) => ({
  userId: 7, roles: granted, granted, assignable: [Role.MEMBER, Role.BOARD], derived: [], dormant: [], implied: [],
})

beforeEach(() => {
  user.readRoleStanding.mockResolvedValue(standing([Role.MEMBER]))
  user.listRoleChanges.mockResolvedValue([])
})

describe("the roles panel", () => {
  it("saves the chosen roles, takes the api's answer as the new standing and tells the page", async () => {
    user.saveRolesOrReason.mockResolvedValue({ok: true, saved: standing([Role.MEMBER, Role.BOARD])})
    const wrapper = mount(UserRolesPanel, {props: {userId: 7, editable: true}})
    await flushPromises()
    const vm = wrapper.vm as unknown as {chosen: Role[]; note: string; save: () => Promise<void>}

    vm.chosen = [Role.MEMBER, Role.BOARD]
    vm.note = " promoted "
    await vm.save()
    await flushPromises()

    expect(user.saveRolesOrReason).toHaveBeenCalledWith(7, [Role.MEMBER, Role.BOARD], "promoted")
    expect(wrapper.emitted("changed")).toEqual([[standing([Role.MEMBER, Role.BOARD])]])
    expect(vm.note).toBe("")
  })

  it("keeps what was chosen and says why when the api refuses", async () => {
    user.saveRolesOrReason.mockResolvedValue({ok: false, reason: "Nope."})
    const wrapper = mount(UserRolesPanel, {props: {userId: 7, editable: true}})
    await flushPromises()
    const vm = wrapper.vm as unknown as {chosen: Role[]; failure: string | null; save: () => Promise<void>}

    vm.chosen = [Role.BOARD]
    await vm.save()

    expect(vm.failure).toBe("Nope.")
    expect(wrapper.emitted("changed")).toBeUndefined()
  })

  it("lets anybody but an admin read the roles without changing them", async () => {
    const wrapper = mount(UserRolesPanel, {props: {userId: 7, editable: false}})
    await flushPromises()

    expect(wrapper.find('[data-testid="user-roles-save-btn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="user-roles-note"]').exists()).toBe(false)
    expect(wrapper.findComponent({name: "VCheckbox"}).props("disabled")).toBe(true)
  })

  it("saves from its own button, and reads again for another person", async () => {
    user.saveRolesOrReason.mockResolvedValue({ok: true, saved: standing([Role.MEMBER, Role.BOARD])})
    const wrapper = mount(UserRolesPanel, {props: {userId: 7, editable: true}})
    await flushPromises()
    ;(wrapper.vm as unknown as {chosen: Role[]}).chosen = [Role.MEMBER, Role.BOARD]
    await flushPromises()

    await wrapper.get('[data-testid="user-roles-save-btn"]').trigger("click")
    await flushPromises()
    expect(user.saveRolesOrReason).toHaveBeenCalled()

    await wrapper.setProps({userId: 8})
    await flushPromises()
    expect(user.readRoleStanding).toHaveBeenLastCalledWith(8)
  })
})
