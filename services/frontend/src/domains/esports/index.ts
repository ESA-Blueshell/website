/**
 * The esports domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001). Re-exported by name rather than with
 * `export *`, because the list of names is the promise being made.
 */
export {JOIN_CALL} from "./island/joinCall"
export {seasonInRoute} from "./island/seasonInRoute"
export {useGames} from "./island/useGames"
export {useMayEditEsports} from "./island/useMayEditEsports"
export {useSeasonLineup, type LineupEntry} from "./island/useSeasonLineup"
export {lineupSliceOf} from "./island/lineupSlice"
export {useSeasons} from "./island/useSeasons"
export {useTeamToEdit} from "./island/useTeamToEdit"
export {forgetCompetitionReads} from "./island/forgetCompetitionReads"
export {seasonStops} from "./island/seasonAxis"
export {enterGameInSeason, leaveGameInSeason} from "./adapters/esports"
export {type Game} from "./adapters/esports"
export {type GameCode} from "./adapters/esports"
export {type Season} from "./adapters/esports"
