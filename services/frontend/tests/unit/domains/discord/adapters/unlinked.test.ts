import {describe, expect, it, vi} from "vitest"
import {listMyUnlinkedRoles} from "@/domains/discord/adapters/unlinked"
import {listMyUnlinkedTargets} from "@/services/api"
import {answer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", () => ({listMyUnlinkedTargets: vi.fn(), TargetSystem: {DISCORD: "DISCORD", BREVO: "BREVO"}}))

describe("listMyUnlinkedRoles", () => {
  it("answers the Discord roles waiting on a linked account, and none where the api cannot say", async () => {
    vi.mocked(listMyUnlinkedTargets).mockResolvedValue(answer(listMyUnlinkedTargets, [
      {system: "DISCORD", label: "Sitecie"},
      {system: "BREVO", label: "Members"},
    ]))
    expect(await listMyUnlinkedRoles()).toEqual(["Sitecie"])

    vi.mocked(listMyUnlinkedTargets).mockResolvedValue(refusal(listMyUnlinkedTargets, {status: 401}))
    expect(await listMyUnlinkedRoles()).toEqual([])
  })
})
