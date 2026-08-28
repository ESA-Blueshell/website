/**
 * Esports domain adapter — the only file in this domain that imports from
 * @/services/api (per frontend ADR-002). Everything else imports from here.
 */
import {
  addRosterEntry,
  clearGameAccount,
  createSeason,
  createTeam,
  deleteSeason,
  deleteTeam,
  fieldTeam,
  findEsportsPage,
  findGameAccounts,
  createGame,
  deleteGame,
  findGameContents,
  updateGamePage,
  findGamePages,
  findRoster,
  findSeasonContents,
  findSeasons,
  findTeamSeasons,
  findTeams,
  findUsers,
  linkRosterEntry,
  removeRosterEntry,
  setGameAccount,
  updateRosterEntry,
  unfieldTeam,
  updateSeason,
  updateTeam,
} from "@/services/api"
import type {
  EsportsPageResponse,
  FieldedTeamResponse,
  GameAccountResponse,
  GamePageResponse,
  RosterEntryResponse,
  SeasonResponse,
  TeamResponse,
  TeamRole as ApiTeamRole,
  TeamRosterResponse,
} from "@/services/api"

/**
 * A game's code: the identity a team, a roster and a member's handle point at. A plain string
 * because the games that exist are rows rather than a list fixed when the code is built.
 */
export type Game = string
export type TeamRole = ApiTeamRole
export type EsportsPage = EsportsPageResponse
export type Season = SeasonResponse
export type Team = TeamResponse
export type TeamRoster = TeamRosterResponse
export type RosterEntry = RosterEntryResponse
export type GameAccount = GameAccountResponse
/** A game itself: what it is called, the art it is drawn with, and how its page presents it. */
export type GameRecord = GamePageResponse
export type FieldedTeam = FieldedTeamResponse

/** What a season holds, so an offer to remove it can say what goes with it. */
export interface SeasonContents {
  teams: number
  players: number
}

/**
 * Every game the association knows, in the order their records put them.
 *
 * Answers with a list whatever came back. Every page asks for this now, including ones served
 * before the api is reachable, and a body that is not the list it was promised must read as no
 * games rather than take the navigation down with it.
 */
export async function loadGames(): Promise<GameRecord[]> {
  const res = await findGamePages()
  return Array.isArray(res.data) ? res.data : []
}

export interface GameSaved {
  ok: true
  game: GameRecord
}

/**
 * A game the association has started playing.
 *
 * Its code is the api's to derive from the name: a code is what everything else points at, and
 * two people naming the same game must not end up with two of it.
 */
export async function addGameOrReason(
  game: {name: string; slug: string},
): Promise<GameSaved | SeasonRefused> {
  const res = await createGame({body: {name: game.name, slug: game.slug}})
  if (res.error || !res.data) return {ok: false, reason: reasonFrom(res.error, "The game could not be added.")}
  return {ok: true, game: res.data}
}

/**
 * A game corrected, from wherever it is shown.
 *
 * Its code is not here: it is the identity a team, a roster and a member's handle point at, and
 * changing it would be a different game.
 */
export async function saveGameOrReason(
  code: Game,
  game: {
    name: string
    slug: string
    intro: string | null
    accent: string | null
    mark: string | null
    banner: string | null
    sortIndex: number
    fielded: boolean
  },
): Promise<GameSaved | SeasonRefused> {
  const res = await updateGamePage({
    path: {game: code},
    body: {
      name: game.name,
      slug: game.slug,
      intro: game.intro ?? undefined,
      accent: game.accent ?? undefined,
      mark: game.mark ?? undefined,
      banner: game.banner ?? undefined,
      sortIndex: game.sortIndex,
      fielded: game.fielded,
    },
  })
  if (res.error || !res.data) return {ok: false, reason: reasonFrom(res.error, "The game could not be saved.")}
  return {ok: true, game: res.data}
}

/** What a game holds, so an offer to remove it can say what would go with it. */
export async function loadGameContents(game: Game): Promise<SeasonContents> {
  const res = await findGameContents({path: {game}})
  return res.data ?? {teams: 0, players: 0}
}

/**
 * A game taken off the site.
 *
 * The refusal is the point: a game carrying history cannot go, and the api says so in words the
 * reader can act on. The sdk answers with an error rather than throwing, so a caller that only
 * catches would report a removal that never happened.
 */
export async function dropGameOrReason(game: Game): Promise<{ok: true} | SeasonRefused> {
  const res = await deleteGame({path: {game}})
  if (res.error) return {ok: false, reason: reasonFrom(res.error, "The game could not be removed.")}
  return {ok: true}
}

export async function loadEsportsPage(game: Game, seasonId?: number): Promise<EsportsPage | null> {
  const res = await findEsportsPage({path: {game}, query: seasonId == null ? {} : {seasonId}})
  return res.data ?? null
}

export async function loadSeasons(): Promise<Season[]> {
  const res = await findSeasons()
  return res.data ?? []
}

/** A save that was refused, in the api's own words. */
export interface SeasonRefused {
  ok: false
  reason: string
}

export interface SeasonSaved {
  ok: true
  season: Season | null
}

/**
 * The api answers a refused write with a body rather than a thrown error, so a caller that
 * only reads `data` cannot tell a rejection from a success. This reports both, for the places
 * that have to say why.
 */
export async function saveSeasonOrReason(
  season: {id?: number; name: string; startDate: string; endDate: string},
): Promise<SeasonSaved | SeasonRefused> {
  const body = {name: season.name, startDate: season.startDate, endDate: season.endDate}
  const res = season.id == null
    ? await createSeason({body})
    : await updateSeason({path: {id: season.id}, body})
  if (res.error) return {ok: false, reason: reasonFrom(res.error)}
  return {ok: true, season: res.data ?? null}
}

/** Whatever the api said, preferring the specific complaint over the generic one. */
function reasonFrom(error: unknown, fallback = "The season could not be saved."): string {
  const body = (error as {detail?: string; title?: string; errors?: Array<{message?: string}>})
  const fields = body?.errors?.map(one => one?.message).filter(Boolean).join(". ")
  return fields || body?.detail || body?.title || fallback
}

export async function saveSeason(
  season: {id?: number; name: string; startDate: string; endDate: string},
): Promise<Season | null> {
  const body = {name: season.name, startDate: season.startDate, endDate: season.endDate}
  const res = season.id == null
    ? await createSeason({body})
    : await updateSeason({path: {id: season.id}, body})
  return res.data ?? null
}

export async function loadSeasonContents(id: number): Promise<SeasonContents> {
  const res = await findSeasonContents({path: {id}})
  return res.data ?? {teams: 0, players: 0}
}

/**
 * Drops a team from one season. The team, and the seasons it played, are untouched: a team
 * fielded in five seasons and dropped from one still played the other four.
 */
export async function unfieldTeamFromSeason(teamId: number, seasonId: number): Promise<void> {
  const res = await unfieldTeam({path: {seasonId, teamId}})
  if (res.error) throw res.error
}

export async function dropSeason(id: number): Promise<void> {
  await deleteSeason({path: {id}})
}

export async function loadTeams(game: Game): Promise<Team[]> {
  const res = await findTeams({query: {game}})
  return res.data ?? []
}

export async function saveTeam(
  team: {id?: number; game: Game; name: string; image?: string | null},
): Promise<Team | null> {
  const res = team.id == null
    ? await createTeam({body: {game: team.game, name: team.name, image: team.image ?? undefined}})
    : await updateTeam({path: {id: team.id}, body: {name: team.name, image: team.image ?? undefined}})
  return res.data ?? null
}

export interface TeamSaved {
  ok: true
  team: Team | null
}

/** Same reason as a season's: the api answers a refusal with a body, not a thrown error. */
export async function saveTeamOrReason(
  team: {game: Game; name: string; image?: string | null},
): Promise<TeamSaved | SeasonRefused> {
  const res = await createTeam({body: {game: team.game, name: team.name, image: team.image ?? undefined}})
  if (res.error) return {ok: false, reason: reasonFrom(res.error)}
  return {ok: true, team: res.data ?? null}
}

/**
 * Renames a team, or changes the banner it is drawn with. Its game never changes: a team is
 * of the game it was made for, and moving one between games would be a different team.
 */
export async function renameTeam(
  id: number,
  name: string,
  image: string | null,
): Promise<TeamSaved | SeasonRefused> {
  const res = await updateTeam({path: {id}, body: {name, image: image ?? undefined}})
  if (res.error) return {ok: false, reason: reasonFrom(res.error)}
  return {ok: true, team: res.data ?? null}
}

export async function dropTeam(id: number): Promise<void> {
  const res = await deleteTeam({path: {id}})
  if (res.error) throw res.error
}

/** The seasons a team has been fielded in, newest first. */
export async function loadTeamSeasons(teamId: number): Promise<Season[]> {
  const res = await findTeamSeasons({path: {teamId}})
  return res.data ?? []
}

/**
 * Fields a team in a season, optionally bringing across the line-up it last had.
 *
 * Answers with what came with it, so a caller can show the roster it is about to publish.
 */
export async function fieldTeamInSeason(
  teamId: number,
  seasonId: number,
  carryLineup: boolean,
): Promise<FieldedTeam | null> {
  const res = await fieldTeam({path: {seasonId, teamId}, body: {carryLineup}})
  return res.data ?? null
}

export async function loadRoster(teamId: number, seasonId: number): Promise<RosterEntry[]> {
  const res = await findRoster({path: {teamId}, query: {seasonId}})
  return res.data ?? []
}

export async function addToRoster(
  teamId: number,
  entry: {
    seasonId: number
    handle: string
    role: TeamRole
    userId?: number | null
    displayName?: string | null
    roleTitle?: string | null
    description?: string | null
  },
): Promise<RosterEntry | null> {
  const res = await addRosterEntry({
    path: {teamId},
    body: {
      seasonId: entry.seasonId,
      handle: entry.handle,
      role: entry.role,
      userId: entry.userId ?? undefined,
      displayName: entry.displayName ?? undefined,
      roleTitle: entry.roleTitle ?? undefined,
      description: entry.description ?? undefined,
    },
  })
  return res.data ?? null
}

export async function saveRosterEntry(
  id: number,
  entry: {
    handle: string
    role: TeamRole
    displayName?: string | null
    sortIndex: number
    roleTitle?: string | null
    description?: string | null
  },
): Promise<RosterEntry | null> {
  const res = await updateRosterEntry({
    path: {id},
    body: {
      handle: entry.handle,
      role: entry.role,
      displayName: entry.displayName ?? undefined,
      sortIndex: entry.sortIndex,
      roleTitle: entry.roleTitle ?? undefined,
      description: entry.description ?? undefined,
    },
  })
  return res.data ?? null
}

/** A null member detaches the entry, which is how an unattributed roster spot is kept. */
export async function linkRosterMember(id: number, userId: number | null): Promise<RosterEntry | null> {
  const res = await linkRosterEntry({path: {id}, body: {userId: userId ?? undefined}})
  return res.data ?? null
}

export async function dropRosterEntry(id: number): Promise<void> {
  await removeRosterEntry({path: {id}})
}

/** A member as a roster entry needs to name them: who they are, and how to tell two apart. */
export interface Member {
  id: number
  name: string
  email: string | null
}

/**
 * The members an entry can be attached to.
 *
 * Asked for once and filtered where it is used, the way the rest of the site's member pickers
 * work. Attaching a roster entry is rare enough that a search round trip per keystroke would
 * buy nothing.
 */
export async function loadMembers(): Promise<Member[]> {
  const res = await findUsers({query: {size: 500}})
  return (res.data?.content ?? [])
    .filter(user => user.id != null)
    .map(user => ({
      id: user.id as number,
      name: user.fullName ?? user.email ?? `Member ${user.id}`,
      email: user.email ?? null,
    }))
    .sort((a, b) => a.name.localeCompare(b.name))
}

export async function loadGameAccounts(userId: number): Promise<GameAccount[]> {
  const res = await findGameAccounts({path: {userId}})
  return res.data ?? []
}

export async function saveGameAccount(
  userId: number,
  game: Game,
  handle: string,
): Promise<GameAccount | null> {
  const res = await setGameAccount({path: {userId, game}, body: {handle}})
  return res.data ?? null
}

export async function dropGameAccount(userId: number, game: Game): Promise<void> {
  await clearGameAccount({path: {userId, game}})
}
