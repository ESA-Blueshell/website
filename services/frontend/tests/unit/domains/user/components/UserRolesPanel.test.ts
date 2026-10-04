import {flushPromises, mount} from "@vue/test-utils"
import {beforeEach, describe, expect, it, vi} from "vitest"
import {Role} from "@/services/api"
import UserRolesPanel from "@/domains/user/components/UserRolesPanel.vue"

vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRouter: () => ({push: vi.fn()})}))

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
    expect(wrapper.get('[data-testid="user-roles-checkbox-board"] input').attributes("disabled")).toBeDefined()
  })

  it("saves from its own button, and reads again for another person", async () => {
    user.saveRolesOrReason.mockResolvedValue({ok: true, saved: standing([Role.MEMBER, Role.BOARD])})
    const wrapper = mount(UserRolesPanel, {props: {userId: 7, editable: true}})
    await flushPromises()
    await wrapper.get('[data-testid="user-roles-checkbox-board"] input').setValue(true)
    await wrapper.get('[data-testid="user-roles-checkbox-member"] input').setValue(false)
    await wrapper.get('[data-testid="user-roles-checkbox-member"] input').setValue(true)

    await wrapper.get('[data-testid="user-roles-note"] input').setValue("Took office")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(user.saveRolesOrReason).toHaveBeenCalledWith(7, [Role.BOARD, Role.MEMBER], "Took office")

    await wrapper.setProps({userId: 8})
    await flushPromises()
    expect(user.readRoleStanding).toHaveBeenLastCalledWith(8)
  })

  it("names the roles that follow a record and the ones that come with them, and lists each change", async () => {
    user.readRoleStanding.mockResolvedValue({
      ...standing([Role.BOARD]),
      derived: [{role: Role.GUEST, source: "ACCOUNT"}, {role: Role.MEMBER, source: "SOMETHING_NEW"}],
      dormant: [Role.BOARD],
      implied: [Role.TREASURER],
    })
    user.listRoleChanges.mockResolvedValue([
      {id: 3, before: [], after: [Role.BOARD], actorName: "Ada Admin", changedAt: "2026-09-01T10:00:00Z", note: "Took office"},
      {id: 4, before: [Role.BOARD], after: [], actorName: "Ada Admin", changedAt: "2026-09-02T10:00:00Z", note: null},
    ])
    const wrapper = mount(UserRolesPanel, {props: {userId: 7, editable: true}})
    await flushPromises()

    expect(wrapper.get('[data-testid="user-roles-derived-guest"]').text()).toContain("every account has it")
    expect(wrapper.get('[data-testid="user-roles-derived-member"]').text()).toContain("SOMETHING_NEW")
    expect(wrapper.get('[data-testid="user-roles-implied-treasurer"]').text()).toContain("@Treasurer")
    expect(wrapper.get('[data-testid="user-roles-checkbox-board"]').text()).toContain("Dormant")
    expect(wrapper.get('[data-testid="user-roles-dormant"]').text()).toContain("two-factor")
    expect(wrapper.get('[data-testid="user-roles-history-3"]').text()).toContain("Nothing to Board")
    expect(wrapper.get('[data-testid="user-roles-history-3"]').text()).toContain("Took office")
    expect(wrapper.get('[data-testid="user-roles-history-4"]').text()).toContain("Board to nothing")
  })

  it("says so when the roles cannot be read, and when nothing has changed yet", async () => {
    user.readRoleStanding.mockResolvedValue(null)
    user.listRoleChanges.mockResolvedValue(null)
    const unread = mount(UserRolesPanel, {props: {userId: 7, editable: true}})
    await flushPromises()
    expect(unread.get('[data-testid="user-roles-load-failure"]').text()).toContain("could not be read")

    user.readRoleStanding.mockResolvedValue(standing([Role.MEMBER]))
    const fresh = mount(UserRolesPanel, {props: {userId: 7, editable: true}})
    expect(fresh.text()).toContain("Reading the roles.")
    await flushPromises()
    expect(fresh.find('[data-testid="user-roles-history-empty"]').exists()).toBe(true)

    user.saveRolesOrReason.mockResolvedValue({ok: false, reason: "Nope."})
    await fresh.get('[data-testid="user-roles-checkbox-board"] input').setValue(true)
    await fresh.get("form").trigger("submit")
    await flushPromises()
    expect(fresh.get('[data-testid="user-roles-failure"]').text()).toBe("Nope.")
  })
})
