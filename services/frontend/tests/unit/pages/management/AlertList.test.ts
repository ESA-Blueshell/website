import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import AlertList from "@/pages/management/AlertList.vue"
import {mountInApp, settle, unmountAll} from "../helpers"

const {mockList, mockHide, mockShow, mockStore} = vi.hoisted(() => ({
  mockList: vi.fn(),
  mockHide: vi.fn(),
  mockShow: vi.fn(),
  mockStore: {commit: vi.fn(), getters: {isAdmin: true}},
}))

vi.mock("@/plugins/store", () => ({default: mockStore}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, listAlerts: mockList, hideAlert: mockHide, showAlert: mockShow}
})

const dead = {key: "job-dead:4", kind: "JOB_DEAD", count: 2, hidden: false, since: "2026-09-30T08:00:00Z"}
const open = {key: "exception-open:1", kind: "EXCEPTION_OPEN", count: 1, hidden: true}

describe("the Alerts page", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(AlertList)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue({status: 200, data: [dead, open]})
    mockHide.mockResolvedValue({status: 204, data: undefined})
    mockShow.mockResolvedValue({status: 204, data: undefined})
  })

  afterEach(() => {
    unmountAll(wrappers, "AlertList")
  })

  it("lists what needs someone, each linked to where it is dealt with", async () => {
    const wrapper = await mount()

    expect(wrapper.find('[data-testid="alert-job-dead:4"]').text()).toContain("2 jobs are dead")
    expect(wrapper.find('[data-testid="alert-open-job-dead:4"]').attributes("to")).toBe("/management/jobs?status=DEAD")
    expect(wrapper.find('[data-testid="alert-hidden"]').exists()).toBe(true)
  })

  it("hides an alert for the reader, and shows a hidden one again", async () => {
    const wrapper = await mount()

    await wrapper.find('[data-testid="alert-hide-job-dead:4"]').trigger("click")
    await settle()
    expect(mockHide).toHaveBeenCalledWith({body: {key: "job-dead:4"}})
    expect(mockList).toHaveBeenCalledTimes(2)

    await wrapper.find('[data-testid="alert-hidden-toggle"]').trigger("click")
    await wrapper.find('[data-testid="alert-show-exception-open:1"]').trigger("click")
    await settle()
    expect(mockShow).toHaveBeenCalledWith({body: {key: "exception-open:1"}})
  })

  it("says why hiding was refused", async () => {
    mockHide.mockResolvedValue({status: 404, error: {detail: "No such alert is raised for you"}})
    const wrapper = await mount()

    await wrapper.find('[data-testid="alert-hide-job-dead:4"]').trigger("click")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("setStatusSnackbarMessage", "No such alert is raised for you")
    expect(mockList).toHaveBeenCalledTimes(1)
  })

  it("says so when nothing needs the reader", async () => {
    mockList.mockResolvedValue({status: 200, data: []})
    const wrapper = await mount()

    expect(wrapper.find('[data-testid="alert-list-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="alert-hidden"]').exists()).toBe(false)
  })
})
