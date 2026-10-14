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
    await expect(loadAlerts()).resolves.toBeNull()
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

  it("keeps the answer to the read asked last, whichever answers last", async () => {
    const answers: Array<(read: Awaited<ReturnType<typeof listAlerts>>) => void> = []
    vi.mocked(listAlerts).mockImplementation((() => new Promise((resolve) => answers.push(resolve))) as typeof listAlerts)
    const {count, refresh} = useAlerts()

    const older = refresh()
    const newer = refresh()
    answers[1](answer(listAlerts, []))
    await newer
    answers[0](answer(listAlerts, [alert(AlertKind.JOB_DEAD)]))
    await older

    expect(count.value).toBe(0)
    vi.mocked(listAlerts).mockReset()
  })

  it("forgets the alerts it held, for the next person to sign in", async () => {
    vi.mocked(listAlerts).mockResolvedValueOnce(answer(listAlerts, [alert(AlertKind.JOB_DEAD)]))
    const {count, refresh, forget} = useAlerts()
    await refresh()

    forget()

    expect(count.value).toBe(0)
  })

  it("keeps the alerts it had when a read fails, and says the read failed", async () => {
    vi.mocked(listAlerts).mockResolvedValueOnce(answer(listAlerts, [alert(AlertKind.JOB_DEAD)]))
    const {count, unread, refresh} = useAlerts()
    await refresh()

    vi.mocked(listAlerts).mockResolvedValueOnce(refusal(listAlerts, null, 500))
    await refresh()

    expect(count.value).toBe(1)
    expect(unread.value).toBe(true)

    vi.mocked(listAlerts).mockResolvedValueOnce(answer(listAlerts, []))
    await refresh()
    expect(count.value).toBe(0)
    expect(unread.value).toBe(false)
  })
})

describe("reading an alert", () => {
  it.each([
    [alert(AlertKind.JOB_DEAD), "1 job is dead", "/management/jobs?status=DEAD"],
    [alert(AlertKind.JOB_DEAD, {count: 3}), "3 jobs are dead", "/management/jobs?status=DEAD"],
    [alert(AlertKind.EXCEPTION_OPEN), "1 exception is open", "/management/exceptions"],
    [alert(AlertKind.EXCEPTION_OPEN, {count: 4}), "4 exceptions are open", "/management/exceptions"],
    [alert(AlertKind.DISCORD_BOT_PERMISSIONS, {count: 2, subjectLabel: "Manage Roles, Create Invite"}),
      "The Discord bot lacks 2 permissions: Manage Roles, Create Invite", "/management/platforms/discord/bot"],
  ])("words %o and links it", (one, title, link) => {
    expect(alertTitle(one)).toBe(title)
    expect(alertLink(one)).toBe(link)
  })
})

describe("an alert as a short row", () => {
  it.each([
    [alert(AlertKind.JOB_DEAD), "1 job is dead", "Every retry failed", "Jobs"],
    [alert(AlertKind.EXCEPTION_OPEN, {count: 4}), "4 exceptions are open", "Not resolved yet", "Exceptions"],
    [alert(AlertKind.DISCORD_BOT_PERMISSIONS, {count: 1, subjectLabel: "Manage Roles"}), "The bot lacks 1 permission", "Manage Roles", "Discord"],
  ])("names %o, says what it is about and where it comes from", (one, name, meta, from) => {
    expect(alertRow(one)).toEqual({name, meta, from})
  })
})
