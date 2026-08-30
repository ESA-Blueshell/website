/**
 * Esports domain adapter — every call this domain makes to the api goes through here, and
 * everything else in the domain imports from here rather than from @/services/api (frontend
 * ADR-002).
 *
 * One exception stands: a component may import a generated enum straight from the sdk, as
 * LineupEditor does for TeamRole, so that the values a picker offers are the ones the api
 * declares rather than a list copied into a component and left to drift.
 */
import {
  addRosterEntry,
  apiUrl,
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
  uploadPublicImage,
} from "@/services/api"
import type {
  FileType,
  EsportsPageResponse,
  Image,
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
/** An image a page draws: where it is served, how large it is, and the widths it is stored at. */
export type EsportsImage = Image

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
  return Array.isArray(res.data) ? res.data.map(withArt) : []
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
): Promise<GameSaved | Refused> {
  const res = await createGame({body: {name: game.name, slug: game.slug}})
  if (res.error || !res.data) return {ok: false, reason: reasonFrom(res.error, "The game could not be added.")}
  return {ok: true, game: withBanner(res.data)}
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
    banner: string | null
    icon: string | null
    sortIndex: number
  },
): Promise<GameSaved | Refused> {
  const res = await updateGamePage({
    path: {game: code},
    body: {
      name: game.name,
      slug: game.slug,
      intro: game.intro ?? undefined,
      accent: game.accent ?? undefined,
      banner: game.banner ?? undefined,
      icon: game.icon ?? undefined,
      sortIndex: game.sortIndex,
    },
  })
  if (res.error || !res.data) return {ok: false, reason: reasonFrom(res.error, "The game could not be saved.")}
  return {ok: true, game: withBanner(res.data)}
}

/**
 * What a game holds, so an offer to remove it can say what would go with it.
 *
 * Answers null when the read fails, rather than zero. A failed read is not an empty game, and
 * reporting it as one would offer to remove a game while telling the reader it holds nothing.
 */
export async function loadGameContents(game: Game): Promise<SeasonContents | null> {
  const res = await findGameContents({path: {game}})
  return res.data ?? null
}

/**
 * A game taken off the site.
 *
 * The refusal is the point: a game carrying history cannot go, and the api says so in words the
 * reader can act on. The sdk answers with an error rather than throwing, so a caller that only
 * catches would report a removal that never happened.
 */
export async function dropGameOrReason(game: Game): Promise<{ok: true} | Refused> {
  const res = await deleteGame({path: {game}})
  if (res.error) return {ok: false, reason: reasonFrom(res.error, "The game could not be removed.")}
  return {ok: true}
}

/**
 * The images the api points at, resolved to where they are actually served.
 *
 * Done here rather than at each place one is drawn: the api answers with its own paths, and
 * a bare path resolves against the frontend's origin instead of the api's. Resolving at the
 * one seam every image comes through means no component has to remember.
 *
 * Every width is resolved, not only the full-size one, so a component can hand the whole set
 * to a `srcset` without checking which of them are usable.
 */
const image = (one: Image): Image => ({
  ...one,
  url: apiUrl(one.url),
  renditions: one.renditions.map(rendition => ({...rendition, url: apiUrl(rendition.url)})),
})

export interface PictureStored {
  ok: true
  picture: EsportsImage
}

/**
 * A picture put into storage, ready for a save to name it.
 *
 * Storing and applying are separate: the dialog that chose the picture is what puts it on the
 * team, the person or the game, so cancelling that dialog leaves all three as they were. What
 * comes back is the whole image — where it is served, how large it is and the widths it is
 * stored at — so a picker can draw it before anything has been saved.
 *
 * A refusal comes back in the api's own words. A picture the converter cannot read is the one
 * thing whoever chose it can act on, and "something went wrong" does not tell them to pick
 * another.
 */
export async function storePicture(
  file: File,
  kind: FileType,
): Promise<PictureStored | Refused> {
  const res = await uploadPublicImage({query: {type: kind}, body: {file}})
  if (res.error || !res.data) {
    return {ok: false, reason: reasonFrom(res.error, "That picture could not be stored.")}
  }
  return {ok: true, picture: image(res.data)}
}

const imageOrNone = (one?: Image | null): Image | null => (one ? image(one) : null)

const withBanner = <T extends {banner?: Image | null}>(record: T): T =>
  ({...record, banner: imageOrNone(record.banner)})

const withIcon = <T extends {icon?: Image | null}>(entry: T): T =>
  ({...entry, icon: imageOrNone(entry.icon)})

/**
 * Both of a record's pictures, resolved against the api.
 *
 * A game and a team each carry a banner and an icon, so what used to be one helper on one field
 * is one helper on the pair. Every url an image carries is resolved here and nowhere else.
 */
const withArt = <T extends {banner?: Image | null; icon?: Image | null}>(record: T): T =>
  withIcon(withBanner(record))

const withMedia = (team: TeamRoster): TeamRoster => ({
  ...withArt(team),
  members: team.members.map(withIcon),
})

export async function loadEsportsPage(game: Game, seasonId?: number): Promise<EsportsPage | null> {
  const res = await findEsportsPage({path: {game}, query: seasonId == null ? {} : {seasonId}})
  const page = res.data
  if (!page) return null
  return {...page, teams: page.teams.map(withMedia)}
}

export async function loadSeasons(): Promise<Season[]> {
  const res = await findSeasons()
  return res.data ?? []
}

/**
 * A write the api refused, in its own words.
 *
 * Every write in this adapter that can be argued with answers this rather than throwing: the
 * sdk hands a refusal back as a body, so a caller that only reads `data` cannot tell a
 * rejection from a success.
 */
export interface Refused {
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
): Promise<SeasonSaved | Refused> {
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
export async function unfieldTeamFromSeason(
  teamId: number,
  game: Game,
  seasonId: number,
): Promise<void> {
  const res = await unfieldTeam({path: {seasonId, teamId}, query: {game}})
  if (res.error) throw res.error
}

export async function dropSeason(id: number): Promise<void> {
  await deleteSeason({path: {id}})
}

/**
 * Every team the association has, not the teams of one game.
 *
 * The pool is shared: a team that has only ever played League of Legends is still one the
 * board can field in Valorant, which is the whole point of a team being the association's.
 */
export async function loadTeams(): Promise<Team[]> {
  const res = await findTeams()
  return (res.data ?? []).map(withArt)
}

export interface TeamSaved {
  ok: true
  team: Team | null
}

/** Same reason as a season's: the api answers a refusal with a body, not a thrown error. */
export async function saveTeamOrReason(
  team: {name: string; icon?: string | null},
): Promise<TeamSaved | Refused> {
  const res = await createTeam({
    body: {
      name: team.name,
      icon: team.icon ?? undefined,
    },
  })
  if (res.error) return {ok: false, reason: reasonFrom(res.error, "The team could not be added.")}
  return {ok: true, team: res.data ? withArt(res.data) : null}
}

/**
 * The team as it now stands: what it is called, the banner it is drawn on and the icon it is
 * known by. Its game never changes — a team is of the game it was made for, and moving one
 * between games would be a different team.
 *
 * Both pictures are part of this write rather than something applied when they were chosen, so
 * cancelling the dialog leaves the team exactly as it was. Naming no picture takes it away.
 */
/**
 * A team's name and its logo. The art it is drawn with belongs to the fielding, so it is
 * saved with the season rather than here.
 */
export async function saveTeamAs(
  id: number,
  team: {name: string; icon: string | null},
): Promise<TeamSaved | Refused> {
  const res = await updateTeam({
    path: {id},
    body: {name: team.name, icon: team.icon ?? undefined},
  })
  if (res.error) return {ok: false, reason: reasonFrom(res.error, "The team could not be saved.")}
  return {ok: true, team: res.data ? withArt(res.data) : null}
}

export async function dropTeam(id: number): Promise<void> {
  const res = await deleteTeam({path: {id}})
  if (res.error) throw res.error
}

/**
 * The line-ups a team has, newest first: which game, in which season.
 *
 * A fielding rather than a season, because a team that played two games in one season has
 * two of them, with a line-up in each.
 */
export interface Fielding {
  game: Game
  season: Season
}

export async function loadTeamSeasons(teamId: number): Promise<Fielding[]> {
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
  game: Game,
  seasonId: number,
  carryLineup: boolean,
  banner?: string | null,
): Promise<FieldedTeam | null> {
  const res = await fieldTeam({
    path: {seasonId, teamId},
    // Naming no banner leaves the art alone rather than taking it away: a team is re-fielded
    // to say it plays this season as often as to change its picture.
    body: {game, carryLineup, banner: banner ?? undefined},
  })
  return res.data ?? null
}

export async function loadRoster(
  teamId: number,
  game: Game,
  seasonId: number,
): Promise<RosterEntry[]> {
  const res = await findRoster({path: {teamId}, query: {game, seasonId}})
  return (res.data ?? []).map(withIcon)
}

export async function addToRoster(
  teamId: number,
  entry: {
    game: Game
    seasonId: number
    handle: string
    role: TeamRole
    userId?: number | null
    displayName?: string | null
    roleTitle?: string | null
    description?: string | null
    icon?: string | null
  },
): Promise<RosterEntry | null> {
  const res = await addRosterEntry({
    path: {teamId},
    body: {
      game: entry.game,
      seasonId: entry.seasonId,
      handle: entry.handle,
      role: entry.role,
      userId: entry.userId ?? undefined,
      displayName: entry.displayName ?? undefined,
      roleTitle: entry.roleTitle ?? undefined,
      description: entry.description ?? undefined,
      icon: entry.icon ?? undefined,
    },
  })
  return res.data ? withIcon(res.data) : null
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
    icon?: string | null
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
      icon: entry.icon ?? undefined,
    },
  })
  return res.data ? withIcon(res.data) : null
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
