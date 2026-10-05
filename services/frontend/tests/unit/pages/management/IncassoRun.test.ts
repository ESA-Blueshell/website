import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import IncassoRun from "@/pages/management/IncassoRun.vue"
import {BulkFeeType, ContributionEmailKind, IncassoLeftOut} from "@/services/api"
import {aContributionPeriod} from "../../helpers/apiFixtures"
import {mountInApp, settle, sortByEveryHead, unmountAll} from "../helpers"

const api = vi.hoisted(() => ({
  findContributionPeriods: vi.fn(),
  planIncasso: vi.fn(),
  startIncassoRun: vi.fn(),
  findIncassoRun: vi.fn(),
  readContributionEmail: vi.fn(),
  downloadIncassoFile: vi.fn(),
  markIncassoRunSubmitted: vi.fn(),
}))
const {mockRoute, mockReplace, mockHandleNetworkError} = vi.hoisted(() => ({
  mockRoute: {params: {periodId: "2"} as Record<string, string>},
  mockReplace: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => mockRoute,
  useRouter: () => ({replace: mockReplace}),
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  ...api,
}))

const candidate = (userId: number, name: string, fields: Record<string, unknown> = {}) => ({
  userId, name, ingName: name, memberSince: "2025-09-01", feeType: BulkFeeType.FULL_YEAR_FEE, amount: 25,
  ibanCountry: "NL", ibanLastTwo: `${userId}${userId}`.slice(0, 2), mandateReference: `BLUESHELL-${userId}`, mandateSignedOn: "2025-09-03",
  leftOut: null, lastNotifiedOn: null, ...fields,
})

const later = new Date(Date.now() + 10 * 86_400_000).toISOString().slice(0, 10)

const run = {
  id: 11, contributionPeriodId: 2, collectionDate: later, statementText: "Contributie 2025-2026 ESA Blueshell", total: 30,
  createdAt: "2026-09-30T10:00:00Z", submittedAt: null, fileParts: 1,
  collections: [{userId: 1, name: "Mila Vries", ingName: "Mila Vries", ibanCountry: "NL", ibanLastTwo: "11", mandateReference: "BLUESHELL-1",
    mandateSignedOn: "2025-09-03", feeType: BulkFeeType.FULL_YEAR_FEE, amount: 30}],
}

describe("the incasso task", () => {
  const wrappers: VueWrapper[] = []
  const mount = async () => {
    const wrapper = mountInApp(IncassoRun)
    wrappers.push(wrapper)
    await settle()
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.params = {periodId: "2"}
    api.findContributionPeriods.mockResolvedValue({status: 200, data: [
      aContributionPeriod({id: 2, startDate: "2025-09-01", endDate: "2026-08-31", fullYearFee: 30, halfYearFee: 15, alumniFee: 5}),
    ]})
    api.planIncasso.mockResolvedValue({status: 200, data: [
      candidate(1, "Mila Vries"),
      candidate(2, "Zoë Bakker", {ingName: "Zoe Bakker"}),
      candidate(3, "Lotte Meijer", {ibanCountry: null, ibanLastTwo: null, mandateReference: null, mandateSignedOn: null, leftOut: IncassoLeftOut.NO_BANK_DETAILS}),
      candidate(4, "Bram Kok", {leftOut: IncassoLeftOut.ALREADY_PAID}),
    ]})
    api.startIncassoRun.mockResolvedValue({status: 201, data: run})
    api.findIncassoRun.mockResolvedValue({status: 200, data: {...run, submittedAt: "2026-10-20T10:00:00Z"}})
    api.downloadIncassoFile.mockResolvedValue({status: 200, data: new Blob(["PK"])})
    api.markIncassoRunSubmitted.mockResolvedValue({status: 200, data: {...run, submittedAt: "2026-10-20T10:00:00Z"}})
    api.readContributionEmail.mockResolvedValue({status: 200, data: {
      subject: "Collected", html: "<p>x</p>", recipientEmail: "a@x", recipientName: "A", kind: "INCASSO_NOTIFICATION", feeType: "FULL_YEAR_FEE",
    }})
  })

  afterEach(() => {
    unmountAll(wrappers, "IncassoRunPage")
  })

  it("walks who, amounts and the check to a run, and emails nobody before it", async () => {
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="incasso-run-with-mandate"]').text()).toBe("3 with a mandate")
    expect(wrapper.get('[data-testid="incasso-run-row-1"]').text()).toContain("NL•• … ••11")
    expect(wrapper.get('[data-testid="incasso-run-left-out-3"]').text()).toContain("Ask them to add")
    expect(wrapper.get('[data-testid="incasso-run-left-out-4"]').text()).toBe("Already paid")
    expect(await sortByEveryHead(wrapper)).toBe(4)

    await wrapper.get('[data-testid="incasso-run-next"]').trigger("click")
    await settle()
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", BulkFeeType.HALF_YEAR_FEE)
    await settle()
    expect(wrapper.get('[data-testid="incasso-run-amounts"]').text()).toContain("€ 15.00")
    expect(await sortByEveryHead(wrapper)).toBe(4)

    await wrapper.get('[data-testid="incasso-run-next"]').trigger("click")
    await settle()
    expect(wrapper.text()).toContain("€ 45.00 will be collected from 2 members")
    expect(await sortByEveryHead(wrapper)).toBe(5)
    expect(wrapper.get('[data-testid="incasso-run-renamed"]').text()).toContain("Zoë Bakker as Zoe Bakker")
    expect(wrapper.get('[data-testid="incasso-run-left-out"]').text()).toContain("Lotte Meijer")
    expect(wrapper.get('[data-testid="incasso-run-start"]').attributes("disabled")).toBeDefined()
    wrapper.findComponent({name: "DateInput"}).vm.$emit("update:modelValue", later)
    await settle()

    await wrapper.get('[data-testid="incasso-run-preview-1"]').trigger("click")
    await settle()
    expect(api.readContributionEmail).toHaveBeenCalledWith({query: {
      kind: ContributionEmailKind.INCASSO_NOTIFICATION, contributionPeriodId: 2, userId: 1, date: later, feeType: BulkFeeType.HALF_YEAR_FEE,
    }})
    expect(api.startIncassoRun).not.toHaveBeenCalled()
    wrapper.findComponent({name: "EmailPreviewDialog"}).vm.$emit("update:modelValue", false)

    expect(wrapper.get('[data-testid="incasso-run-start"]').text()).toBe("Email the incasso notification to 2 members")
    await wrapper.get('[data-testid="incasso-run-start"]').trigger("click")
    await settle()
    expect(api.startIncassoRun).toHaveBeenCalledWith({path: {periodId: 2}, body: {
      userIds: [1, 2], feeTypeOverrides: {1: BulkFeeType.HALF_YEAR_FEE}, collectionDate: later, statementText: "Contributie 2025-2026 ESA Blueshell",
    }})
    expect(wrapper.get('[data-testid="incasso-run-done"]').text()).toContain("1 incasso notification sent")
    expect(wrapper.get('[data-testid="incasso-run-waiting"]').text()).toContain("put the collection in ING")
    expect(mockReplace).toHaveBeenCalledWith("/management/contributions/2/incasso/11")
  })

  it("leaves out who is unticked, and says why a run was refused", async () => {
    api.startIncassoRun.mockResolvedValue({status: 400, error: {code: "CollectionDateNotAhead"}})
    const wrapper = await mount()

    await wrapper.get('[data-testid="incasso-run-tick-2"]').setValue(false)
    await wrapper.get('[data-testid="incasso-run-next"]').trigger("click")
    await wrapper.get('[data-testid="incasso-run-previous"]').trigger("click")
    await wrapper.get('[data-testid="incasso-run-next"]').trigger("click")
    await wrapper.get('[data-testid="incasso-run-next"]').trigger("click")
    await settle()
    expect(wrapper.find('[data-testid="incasso-run-renamed"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="incasso-run-left-out"]').text()).toContain("Not chosen")
    wrapper.findComponent({name: "DateInput"}).vm.$emit("update:modelValue", later)
    wrapper.findComponent({name: "TextInput"}).vm.$emit("update:modelValue", "a".repeat(141))
    await settle()
    expect(wrapper.get('[data-testid="incasso-run-start"]').attributes("disabled")).toBeDefined()
    wrapper.findComponent({name: "TextInput"}).vm.$emit("update:modelValue", "Contributie")
    await settle()
    await wrapper.get('[data-testid="incasso-run-start"]').trigger("click")
    await settle()

    expect(api.startIncassoRun).toHaveBeenCalledWith({path: {periodId: 2}, body: expect.objectContaining({userIds: [1], feeTypeOverrides: {}})})
    expect(wrapper.get('[data-testid="incasso-run-failure"]').text()).toBe("The collection date has to be after today.")
  })

  it("opens a run on its last step, and says when nobody pays by incasso", async () => {
    mockRoute.params = {periodId: "2", runId: "11"}
    const opened = await mount()
    expect(api.planIncasso).not.toHaveBeenCalled()
    expect(opened.get('[data-testid="incasso-run-in-ing"]').text()).toContain("Submitted to ING on 20 Oct 2026")
    expect(await sortByEveryHead(opened)).toBe(4)

    mockRoute.params = {periodId: "2"}
    api.planIncasso.mockResolvedValue({status: 200, data: []})
    expect((await mount()).find('[data-testid="incasso-run-empty"]').exists()).toBe(true)

    api.findContributionPeriods.mockRejectedValue(new Error("offline"))
    await mount()
    expect(mockHandleNetworkError).toHaveBeenCalled()
  })

  it("downloads ING's file for a run, one per part, then marks it in ING", async () => {
    const createObjectURL = vi.fn(() => "blob:file")
    const revokeObjectURL = vi.fn()
    vi.stubGlobal("URL", {...URL, createObjectURL, revokeObjectURL})
    const clicked = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined)
    mockRoute.params = {periodId: "2", runId: "11"}
    api.findIncassoRun.mockResolvedValue({status: 200, data: {...run, fileParts: 2}})
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="incasso-run-file"]').text()).toContain("so there are 2")
    expect(wrapper.get('[data-testid="incasso-run-download-2"]').text()).toBe("Download file 2 of 2")
    await wrapper.get('[data-testid="incasso-run-download-2"]').trigger("click")
    await settle()
    expect(api.downloadIncassoFile).toHaveBeenCalledWith({path: {runId: 11}, query: {part: 2}})
    expect(clicked).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:file")

    api.downloadIncassoFile.mockResolvedValue({status: 409, error: {code: "CollectionDatePassed"}})
    await wrapper.get('[data-testid="incasso-run-download-1"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="incasso-run-done-failure"]').text()).toContain("collection date has passed")

    api.markIncassoRunSubmitted.mockResolvedValueOnce({status: 404, error: {code: "IncassoRunNotFound"}})
    await wrapper.get('[data-testid="incasso-run-submitted"]').trigger("click")
    await settle()
    expect(wrapper.get('[data-testid="incasso-run-done-failure"]').text()).toContain("no such incasso")
    await wrapper.get('[data-testid="incasso-run-submitted"]').trigger("click")
    await settle()
    expect(api.markIncassoRunSubmitted).toHaveBeenCalledWith({path: {runId: 11}})
    expect(wrapper.get('[data-testid="incasso-run-in-ing"]').text()).toContain("Submitted to ING on 20 Oct 2026")
    expect(wrapper.find('[data-testid="incasso-run-file"]').exists()).toBe(false)

    clicked.mockRestore()
    vi.unstubAllGlobals()
  })
})
