import {describe, expect, it, vi} from "vitest"
import {AlertKind, hideAlert as hideSdk, listAlerts, showAlert as showSdk} from "@/services/api"
import {type Alert, alertLink, alertRow, alertTitle, hideAlert, loadAlerts, showAlert, useAlerts} from "@/domains/alerts"
import {answer, emptyAnswer, refusal} from "../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  listAlerts: vi.fn(),
  hideAlert: vi.fn(),
  showAlert: vi.fn(),
}))

const alert = (kind: AlertKind, fields: Partial<Alert> = {}): Alert => ({key: kind, kind, count: 1, hidden: false, ...fields})

describe("the alerts adapter", () => {
  it("reads the reader's alerts, and none where they could not be read", async () => {
    vi.mocked(listAlerts).mockResolvedValueOnce(answer(listAlerts, [alert(AlertKind.JOB_DEAD)]))
    await expect(loadAlerts()).resolves.toHaveLength(1)

    vi.mocked(listAlerts).mockResolvedValueOnce(refusal(listAlerts, {detail: "Forbidden"}))
    await expect(loadAlerts()).resolves.toEqual([])
  })

  it("hides and shows an alert by its key, and says why when it cannot", async () => {
    vi.mocked(hideSdk).mockResolvedValueOnce(emptyAnswer(hideSdk))
    vi.mocked(showSdk).mockResolvedValueOnce(emptyAnswer(showSdk))

    await expect(hideAlert("job-dead:4")).resolves.toMatchObject({ok: true})
    expect(hideSdk).toHaveBeenCalledWith({body: {key: "job-dead:4"}})
    await expect(showAlert("job-dead:4")).resolves.toMatchObject({ok: true})

    vi.mocked(hideSdk).mockResolvedValueOnce(refusal(hideSdk, {}))
    vi.mocked(showSdk).mockResolvedValueOnce(refusal(showSdk, {}))
    await expect(hideAlert("x")).resolves.toEqual({ok: false, reason: "That alert could not be hidden."})
    await expect(showAlert("x")).resolves.toEqual({ok: false, reason: "That alert could not be shown again."})
  })

  it("shares one list, counting only what the reader has not hidden", async () => {
    vi.mocked(listAlerts).mockResolvedValueOnce(answer(listAlerts, [alert(AlertKind.JOB_DEAD), alert(AlertKind.EXCEPTION_OPEN, {hidden: true})]))
    const first = useAlerts()
    await first.refresh()

    const second = useAlerts()
    expect(second.count.value).toBe(1)
    expect(second.hidden.value.map((one) => one.kind)).toEqual([AlertKind.EXCEPTION_OPEN])
  })
})

describe("reading an alert", () => {
  it.each([
    [alert(AlertKind.TARGET_DRIFT, {subjectId: 2, subjectLabel: "Sitecie", count: 3}), "Sitecie is out of sync with Brevo: 3 people differ", "/management/platforms/brevo/cohort/2"],
    [alert(AlertKind.TARGET_DRIFT, {count: 1}), "A list is out of sync with Brevo: 1 person differs", "/management/platforms/brevo"],
    [alert(AlertKind.COHORT_WITHOUT_LIST, {subjectId: 5, subjectLabel: "Paid 2026"}), "Paid 2026 has no Brevo list", "/management/platforms/brevo/cohort/5"],
    [alert(AlertKind.COHORT_WITHOUT_LIST), "A cohort has no Brevo list", "/management/platforms/brevo"],
    [alert(AlertKind.EMAIL_FAILED, {count: 2}), "2 emails failed or bounced this month", "/management/mail/sent"],
    [alert(AlertKind.JOB_DEAD), "1 job is dead", "/management/jobs?status=DEAD"],
    [alert(AlertKind.EXCEPTION_OPEN, {count: 4}), "4 exceptions are open", "/management/exceptions"],
    [alert(AlertKind.ROLE_AWAITING_TWO_FACTOR, {subjectId: 5, subjectLabel: "ada"}), "@ada's granted role waits on two-factor", "/management/users/5"],
    [
      alert(AlertKind.DISCORD_BOT_PERMISSIONS, {count: 2, subjectLabel: "Manage Roles, Create Invite"}),
      "The Discord bot lacks 2 permissions: Manage Roles, Create Invite",
      "/management/platforms/discord/bot",
    ],
    [alert(AlertKind.BREVO_FOLDERS_SHARE_NAME, {count: 2, subjectLabel: "Boards, Teams"}), "Brevo has folders that share a name: Boards, Teams", "/management/platforms/brevo"],
  ])("words %o and links it", (one, title, link) => {
    expect(alertTitle(one)).toBe(title)
    expect(alertLink(one)).toBe(link)
  })
})

describe("an alert as a short row", () => {
  it.each([
    [alert(AlertKind.TARGET_DRIFT, {subjectLabel: "Sitecie", count: 3}), "A list is out of sync", "Sitecie · 3 people differ", "Brevo"],
    [alert(AlertKind.TARGET_DRIFT, {count: 1}), "A list is out of sync", "A list · 1 person differs", "Brevo"],
    [alert(AlertKind.COHORT_WITHOUT_LIST, {subjectLabel: "Paid 2026"}), "A Brevo list is missing", "Paid 2026", "Brevo"],
    [alert(AlertKind.COHORT_WITHOUT_LIST), "A Brevo list is missing", "A cohort", "Brevo"],
    [alert(AlertKind.EMAIL_FAILED, {count: 2}), "2 emails failed or bounced", "This month", "Sent"],
    [alert(AlertKind.JOB_DEAD), "1 job is dead", "After every retry", "Jobs"],
    [alert(AlertKind.EXCEPTION_OPEN, {count: 4}), "4 exceptions are open", "Not resolved yet", "Exceptions"],
    [alert(AlertKind.ROLE_AWAITING_TWO_FACTOR, {subjectLabel: "ada"}), "A role waits on two-factor", "@ada", "Users"],
    [alert(AlertKind.DISCORD_BOT_PERMISSIONS, {subjectLabel: "Manage Roles"}), "The bot lacks 1 permission", "Manage Roles", "Discord"],
    [alert(AlertKind.DISCORD_BOT_PERMISSIONS, {subjectLabel: null}), "The bot lacks 1 permission", "", "Discord"],
    [alert(AlertKind.BREVO_FOLDERS_SHARE_NAME, {subjectLabel: "Boards"}), "Folders share a name", "Boards", "Brevo"],
    [alert(AlertKind.BREVO_FOLDERS_SHARE_NAME, {subjectLabel: null}), "Folders share a name", "", "Brevo"],
  ])("names %o, says what it is about and where it comes from", (one, name, meta, from) => {
    expect(alertRow(one)).toEqual({name, meta, from})
  })
})
