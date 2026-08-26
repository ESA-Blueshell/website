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
  findEsportsPage,
  findGameAccounts,
  findRoster,
  findSeasons,
  findTeams,
  linkRosterEntry,
  removeRosterEntry,
  setGameAccount,
  updateRosterEntry,
  updateSeason,
  updateTeam,
} from "@/services/api"
import type {
  EsportsPageResponse,
  Game as ApiGame,
  GameAccountResponse,
  RosterEntryResponse,
  SeasonResponse,
  TeamResponse,
  TeamRole as ApiTeamRole,
  TeamRosterResponse,
} from "@/services/api"

export type Game = ApiGame
export type TeamRole = ApiTeamRole
export type EsportsPage = EsportsPageResponse
export type Season = SeasonResponse
export type Team = TeamResponse
export type TeamRoster = TeamRosterResponse
export type RosterEntry = RosterEntryResponse
export type GameAccount = GameAccountResponse

export async function loadEsportsPage(game: Game, seasonId?: number): Promise<EsportsPage | null> {
  const res = await findEsportsPage({path: {game}, query: seasonId == null ? {} : {seasonId}})
  return res.data ?? null
}

export async function loadSeasons(): Promise<Season[]> {
  const res = await findSeasons()
  return res.data ?? []
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

export async function dropTeam(id: number): Promise<void> {
  await deleteTeam({path: {id}})
}

export async function loadRoster(teamId: number, seasonId: number): Promise<RosterEntry[]> {
  const res = await findRoster({path: {teamId}, query: {seasonId}})
  return res.data ?? []
}

export async function addToRoster(
  teamId: number,
  entry: {seasonId: number; handle: string; role: TeamRole; userId?: number | null; displayName?: string | null},
): Promise<RosterEntry | null> {
  const res = await addRosterEntry({
    path: {teamId},
    body: {
      seasonId: entry.seasonId,
      handle: entry.handle,
      role: entry.role,
      userId: entry.userId ?? undefined,
      displayName: entry.displayName ?? undefined,
    },
  })
  return res.data ?? null
}

export async function saveRosterEntry(
  id: number,
  entry: {handle: string; role: TeamRole; displayName?: string | null; sortIndex: number},
): Promise<RosterEntry | null> {
  const res = await updateRosterEntry({
    path: {id},
    body: {
      handle: entry.handle,
      role: entry.role,
      displayName: entry.displayName ?? undefined,
      sortIndex: entry.sortIndex,
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
