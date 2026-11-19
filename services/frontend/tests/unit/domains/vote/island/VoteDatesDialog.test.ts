import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import * as api from "@/services/api"
import DateTimeInput from "@/components/island/DateTimeInput.vue"
import VoteDatesDialog from "@/domains/vote/island/VoteDatesDialog.vue"
import {isoOf, type AnnualVote} from "@/domains/vote"
import {answer, refusal} from "../../../helpers/sdkAnswers"
import {aVote} from "../fixtures"

vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  createAnnualVote: vi.fn(),
  rescheduleAnnualVote: vi.fn(),
}))

// The island's dialog portals to the body; a stand-in keeps what it holds where it can be read.
const ModalDialog = {
  name: "ModalDialog",
  props: ["open", "title", "testid", "cancelTestid", "wide"],
  emits: ["update:open"],
  template: "<div><h2>{{ title }}</h2><slot /><slot name='footer' /></div>",
}

describe("the board's dates dialog", () => {
  beforeEach(() => vi.clearAllMocks())

  const dialog = async (vote: AnnualVote | null, year = 2026) => {
    const wrapper = mount(VoteDatesDialog, {props: {open: false, kind: "OST_TOP_50", vote, year}, global: {stubs: {ModalDialog}}})
    await wrapper.setProps({open: true})
    await flushPromises()
    return wrapper
  }

  const fields = (wrapper: Awaited<ReturnType<typeof dialog>>) => wrapper.findAllComponents(DateTimeInput)

  it("is titled for the year it starts or the vote it changes", async () => {
    expect((await dialog(null, 2027)).get("h2").text()).toBe("Start the 2027 vote")
    expect((await dialog(aVote("VOTING"))).get("h2").text()).toBe("The 2026 vote")
  })

  it("starts this year's vote before the first, on December's dates unless changed", async () => {
    const started = aVote("UPCOMING")
    vi.mocked(api.createAnnualVote).mockResolvedValueOnce(answer(api.createAnnualVote, started))
    const wrapper = await dialog(null)

    expect(fields(wrapper).map(field => field.props("modelValue"))).toEqual(["2026-12-01T00:00", "2026-12-31T17:00", "2026-12-31T20:00"])
    expect(wrapper.get("[data-testid=vote-board-save]").text()).toBe("Start the 2026 vote")
    fields(wrapper)[2]!.vm.$emit("update:modelValue", "2026-12-31T21:00")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(api.createAnnualVote).toHaveBeenCalledWith({
      path: {kind: "OST_TOP_50"},
      body: {year: 2026, opensAt: isoOf("2026-12-01T00:00"), closesAt: isoOf("2026-12-31T17:00"), revealAt: isoOf("2026-12-31T21:00"), picks: 5},
    })
    expect(wrapper.emitted("changed")).toEqual([[started]])
    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })

  it("shows a revealed vote as it ran, with nothing to change", async () => {
    const wrapper = await dialog(aVote("REVEALED"))

    expect(wrapper.text()).toContain("dates and picks stay as they were")
    expect(fields(wrapper).every(field => field.props("disabled"))).toBe(true)
    expect(wrapper.find("[data-testid=vote-board-save]").exists()).toBe(false)
    expect(wrapper.get("input[data-testid=vote-board-picks]").attributes("disabled")).toBeDefined()
  })

  it("sets the number of picks until the first vote is cast, and sends none when it is emptied", async () => {
    const vote = aVote("UPCOMING", {ballots: 0, points: [8, 6, 4]})
    vi.mocked(api.rescheduleAnnualVote).mockResolvedValue(answer(api.rescheduleAnnualVote, vote))
    const wrapper = await dialog(vote)
    const picks = wrapper.get("[data-testid=vote-board-picks]")

    expect((picks.element as HTMLInputElement).value).toBe("3")
    await picks.setValue("7")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(api.rescheduleAnnualVote).toHaveBeenLastCalledWith({
      path: {kind: "OST_TOP_50", year: 2026},
      body: {opensAt: vote.opensAt, closesAt: vote.closesAt, revealAt: vote.revealAt, version: 2, picks: 7},
    })

    await picks.setValue("")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(api.rescheduleAnnualVote).toHaveBeenLastCalledWith({
      path: {kind: "OST_TOP_50", year: 2026},
      body: {opensAt: vote.opensAt, closesAt: vote.closesAt, revealAt: vote.revealAt, version: 2},
    })
  })

  it("moves an unrevealed vote's dates at the version it read, shown in Amsterdam time", async () => {
    const vote = aVote("VOTING", {ballots: 3})
    vi.mocked(api.rescheduleAnnualVote).mockResolvedValueOnce(answer(api.rescheduleAnnualVote, vote))
    const wrapper = await dialog(vote)

    expect(wrapper.get("[data-testid=vote-board-save]").text()).toBe("Save")
    expect(wrapper.get("input[data-testid=vote-board-picks]").attributes("disabled")).toBeDefined()
    expect(wrapper.text()).toContain("Fixed once votes are cast")
    await wrapper.get("form").trigger("submit")
    await flushPromises()

    expect(api.rescheduleAnnualVote).toHaveBeenCalledWith({
      path: {kind: "OST_TOP_50", year: 2026},
      body: {opensAt: vote.opensAt, closesAt: vote.closesAt, revealAt: vote.revealAt, version: 2},
    })
    expect(wrapper.emitted("changed")).toEqual([[vote]])
  })

  it("asks for all three dates, and says why a save was refused", async () => {
    const wrapper = await dialog(aVote("UPCOMING"))
    fields(wrapper)[0]!.vm.$emit("update:modelValue", "")
    await wrapper.get("form").trigger("submit")
    expect(wrapper.get("[data-testid=vote-board-problem]").text()).toBe("Fill in all three dates.")
    expect(api.rescheduleAnnualVote).not.toHaveBeenCalled()

    vi.mocked(api.rescheduleAnnualVote).mockResolvedValueOnce(refusal(api.rescheduleAnnualVote, {code: "VoteDatesOutOfOrder"}))
    fields(wrapper)[0]!.vm.$emit("update:modelValue", "2026-12-25T00:00")
    await wrapper.get("form").trigger("submit")
    await flushPromises()
    expect(wrapper.get("[data-testid=vote-board-problem]").text()).toContain("opens before it closes")
    expect(wrapper.emitted("changed")).toBeUndefined()
  })

})
