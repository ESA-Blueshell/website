import {flushPromises, mount} from "@vue/test-utils"
import {beforeEach, describe, expect, it, vi} from "vitest"
import {Role} from "@/services/api"
import UserRolesDialog from "@/domains/user/components/UserRolesDialog.vue"

const user = vi.hoisted(() => ({listRoleChanges: vi.fn(), readRoleStanding: vi.fn(), saveRolesOrReason: vi.fn()}))
vi.mock("@/domains/user", async importOriginal => ({...(await importOriginal<typeof import("@/domains/user")>()), ...user}))

const standing = (granted: Role[]) => ({
  userId: 7, roles: granted, granted, assignable: [Role.MEMBER, Role.BOARD], derived: [], dormant: [], implied: [],
})

beforeEach(() => {
  user.readRoleStanding.mockResolvedValue(standing([Role.MEMBER]))
  user.listRoleChanges.mockResolvedValue([])
})

describe("the roles dialog", () => {
  it("saves the chosen roles, takes the api's answer as the new standing and tells the page", async () => {
    user.saveRolesOrReason.mockResolvedValue({ok: true, saved: standing([Role.MEMBER, Role.BOARD])})
    const wrapper = mount(UserRolesDialog, {props: {modelValue: true, userId: 7, userName: "Roos"}})
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
    const wrapper = mount(UserRolesDialog, {props: {modelValue: true, userId: 7, userName: "Roos"}})
    await flushPromises()
    const vm = wrapper.vm as unknown as {chosen: Role[]; failure: string | null; save: () => Promise<void>}

    vm.chosen = [Role.BOARD]
    await vm.save()

    expect(vm.failure).toBe("Nope.")
    expect(wrapper.emitted("changed")).toBeUndefined()
  })
})
