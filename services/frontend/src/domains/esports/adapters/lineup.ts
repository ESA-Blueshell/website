/**
 * Writing a line-up: one Save, and a team fielded with the people it brings.
 *
 * A Save is one request the api applies whole or not at all. Fielding a team that played
 * before is still several, so it stops at the first refusal and reports the stage that stopped
 * it with the count of entries that landed. No sentence a reader sees is written here and
 * nothing about a `Row` or a `Picture` crosses this seam.
 */
import {publishLineup as sendLineup} from "@/services/api"
import type {Refused} from "@/types/api"
import {
  addToRoster,
  fieldTeamInSeason,
  type GameCode,
  type TeamRole,
} from "./esports"
import {accepted, reasonFor} from "../refusals"

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
  /** Entries taken off. */
  removed: number[]
  entries: DraftEntry[]
}

export type CarryStage = "source" | "fielding" | "carry"

/**
 * How far fielding a team got.
 *
 * `written` counts roster entries, so every stage before the carry reports none — it is not a
 * count of requests.
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
 * A line-up draft saved in one request: the team, this season's art, who comes off and everybody
 * else in order. The api applies it in one transaction, so a refusal leaves the line-up as it was
 * and there is no half to report. Blank rows are dropped here, before positions are handed out.
 */
export async function publishLineup(draft: LineupDraft): Promise<{ok: true} | Refused> {
  try {
    return await accepted(sendLineup({
      path: {seasonId: draft.seasonId},
      body: {
        teamId: draft.teamId,
        name: draft.name,
        icon: draft.icon,
        game: draft.game,
        banner: draft.banner,
        removed: draft.removed,
        entries: draft.entries
          .filter(entry => !isBlank(entry))
          .map(entry => ({id: entry.id, ...bodyOf(entry), userId: entry.userId})),
      },
    }), "The line-up could not be saved.")
  } catch (error) {
    return {ok: false, reason: reasonFor(error, "The line-up could not be saved.")}
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
    const fielded = await fieldTeamInSeason(input.teamId, input.seasonId, {
      game: input.game, carryLineup: false, carryFrom: whole ? input.from : undefined,
    })
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
