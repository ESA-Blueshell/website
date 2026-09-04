/**
 * Publishing a line-up draft: the ordered writes that stand behind one Save.
 *
 * Each is several requests with one outcome, and the failure they answer for is half-written
 * data, so they run in a fixed order, stop at the first refusal and report the stage that
 * stopped them with the count of roster entries that landed. No sentence a reader sees is
 * written here and nothing about a `Row` or a `Picture` crosses this seam: the component turns
 * a reason, a count and a stage into prose.
 */
import {
  addToRoster,
  dropRosterEntry,
  fieldTeamInSeason,
  linkRosterMember,
  saveRosterEntry,
  saveTeamAs,
  saveTeamOrReason,
  type GameCode,
  type TeamRole,
} from "./esports"
import {reasonFor} from "../refusals"

/**
 * One person on a line-up being written, held as what a write names rather than as what a form
 * draws: `icon` is where the picture is stored, not the picture.
 */
export interface DraftEntry {
  /** The entry this stands for, or nothing where it is somebody being added. */
  id: number | null
  handle: string
  role: TeamRole
  roleTitle: string
  description: string
  userId: number | null
  displayName: string
  icon: string | null
}

/** A line-up draft as it goes to the api: the team, its art, the people on it and who comes off. */
export interface LineupDraft {
  /** Nothing where the team does not exist yet, in which case it is made before anything else. */
  teamId: number | null
  name: string
  game: GameCode
  seasonId: number
  /** The art of this season's fielding, which is why it is not written with the team. */
  banner: string | null
  icon: string | null
  /** Entries taken off, dropped before the rest are written. */
  removed: number[]
  entries: DraftEntry[]
}

export type PublishStage = "team" | "fielding" | "removals" | "roster"
export type CarryStage = "source" | "fielding" | "carry"

/**
 * How far a publish got.
 *
 * `written` counts roster entries, so every stage before the roster reports none — it is not a
 * count of requests. Generic over one function's own stages, so no caller can branch on a stage
 * that function cannot stop at.
 */
export type Published<S> =
  | {ok: true}
  | {ok: false; reason: string; written: number; stage: S}

/**
 * The fields the blank rule reads. Narrower than a `DraftEntry` so a form's own row can be
 * asked the question without first being turned into one.
 */
export interface Fillable {
  handle: string
  displayName: string
  roleTitle: string
  description: string
  userId: number | null
  icon: string | object | null
}

/**
 * A row nobody typed into. Exported because the Save button offers the same rule the writer
 * applies: two copies of it drifting means Save is offered for a line-up that writes nothing.
 */
export const isBlank = (entry: Fillable): boolean =>
  entry.handle.trim() === "" && entry.displayName.trim() === "" && entry.roleTitle.trim() === ""
  && entry.description.trim() === "" && entry.userId == null && entry.icon == null

const refused = <S>(reason: string, stage: S, written = 0): Published<S> =>
  ({ok: false, reason, written, stage})

/**
 * What a roster write names, whether the entry was typed into a form or carried off another
 * line-up. One rule for both, so the two paths cannot drift on what a blank field means.
 */
const bodyOf = (entry: DraftEntry) => ({
  handle: entry.handle.trim(),
  role: entry.role,
  roleTitle: entry.roleTitle.trim() || null,
  description: entry.description.trim() || null,
  displayName: entry.displayName.trim() || null,
  icon: entry.icon,
})

/**
 * A line-up draft written as one answer.
 *
 * The order is forced: a team has to exist before anything can be written against it, and a
 * rename has to land before rows are written against the renamed team. A throw comes back as
 * the same refusal an argued-with write does, so a caller has one failure to read rather than
 * two.
 */
export async function publishLineup(draft: LineupDraft): Promise<Published<PublishStage>> {
  let stage: PublishStage = "team"
  let written = 0
  try {
    const saved = draft.teamId == null
      ? await saveTeamOrReason({name: draft.name, icon: draft.icon})
      : await saveTeamAs(draft.teamId, {name: draft.name, icon: draft.icon})
    if (!saved.ok) return refused(saved.reason, stage)
    const teamId = draft.teamId ?? saved.team.id

    stage = "fielding"
    const fielded = await fieldTeamInSeason(teamId, draft.game, draft.seasonId, false, draft.banner)
    if (!fielded.ok) return refused(fielded.reason, stage)

    stage = "removals"
    for (const id of draft.removed) {
      const gone = await dropRosterEntry(id)
      if (!gone.ok) return refused(gone.reason, stage)
    }

    // Blanks are dropped before positions are handed out, so a row nobody typed into no longer
    // spends one. A new entry still spends one and cannot be told it: `AddRosterEntryRequest`
    // has no `sortIndex`, so a new row in the middle leaves a gap where it stands.
    stage = "roster"
    const entries = draft.entries.filter(entry => !isBlank(entry))
    for (const [sortIndex, entry] of entries.entries()) {
      const shared = bodyOf(entry)
      // Each answer is read before the next write, so a refusal partway leaves everything
      // before it saved and the count says which one stopped.
      if (entry.id == null) {
        // No position on this one: where a new entry lands is the api's to say, and its
        // request has nowhere to put one.
        const added = await addToRoster(teamId, {
          game: draft.game,
          seasonId: draft.seasonId,
          ...shared,
          userId: entry.userId,
        })
        if (!added.ok) return refused(added.reason, stage, written)
      } else {
        const savedEntry = await saveRosterEntry(entry.id, {...shared, sortIndex})
        if (!savedEntry.ok) return refused(savedEntry.reason, stage, written)
        const linked = await linkRosterMember(entry.id, entry.userId)
        if (!linked.ok) return refused(linked.reason, stage, written)
      }
      written += 1
    }
    return {ok: true}
  } catch (error) {
    return refused(reasonFor(error, "The line-up could not be saved."), stage, written)
  }
}

/** A team out of the association's pool, and whichever of its people are being brought with it. */
export interface TeamFielding {
  teamId: number
  game: GameCode
  seasonId: number
  /** The line-up they come from, or nothing where none was picked. */
  from: {game: GameCode; seasonId: number} | null
  /** The people kept out of it. */
  entries: DraftEntry[]
  /** How many that line-up holds, so keeping all of them can be told from keeping some. */
  sourceSize: number
  /** The source could not be read, which is not the same as it holding nobody. */
  unread: boolean
}

/**
 * A team that played before, fielded this season with the people it is bringing.
 *
 * Everybody kept is the one request that carries them; anything less is carried by hand, so
 * nobody who was dropped is written down and then deleted.
 */
export async function fieldExistingTeam(input: TeamFielding): Promise<Published<CarryStage>> {
  // An unread source carries nobody, and `carryFrom` would have the api copy the whole line-up
  // anyway — so neither half of this may run on one. No reason with it: what a reader is told
  // about a source that could not be read is the component's sentence to write.
  if (input.unread) return refused("", "source")
  let stage: CarryStage = "fielding"
  let written = 0
  try {
    const whole = input.from != null && input.entries.length === input.sourceSize
    const fielded = await fieldTeamInSeason(
      input.teamId, input.game, input.seasonId, false, null, whole ? input.from : null)
    if (!fielded.ok) return refused(fielded.reason, stage)
    if (whole) return {ok: true}

    stage = "carry"
    for (const entry of input.entries) {
      const added = await addToRoster(input.teamId, {
        game: input.game,
        seasonId: input.seasonId,
        ...bodyOf(entry),
        userId: entry.userId,
      })
      if (!added.ok) return refused(added.reason, stage, written)
      written += 1
    }
    return {ok: true}
  } catch (error) {
    return refused(reasonFor(error, "That team could not be fielded this season."), stage, written)
  }
}
