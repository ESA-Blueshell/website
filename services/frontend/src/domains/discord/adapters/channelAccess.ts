/**
 * A game's channels on Discord: making one with the default access, reading and setting how far
 * everybody and members get into all of them, and archiving one. The access is written to Discord
 * only when it is set here.
 */
import {
  type ChannelAccessPolicy,
  type ChannelAccessState,
  type GameAccessState,
  archiveChannel,
  createGameChannel,
  findGameAccess,
  GameChannelCategory,
  setGameAccess,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {accepted, refusable} from "../refusals"
import type {GameRoom} from "./channels"

export type {ChannelAccessPolicy, ChannelAccessState, GameAccessState}
export {ChannelAccess} from "@/services/api"

/** Makes a games or esports channel, everybody reading and members writing, for the form to add. */
export const makeGameChannel = (name: string, category: GameChannelCategory): Promise<Saved<GameRoom> | Refused> =>
  refusable(createGameChannel({body: {name, category}}), "The channel could not be made.")

/** Every channel of the game with the access they share; null where Discord cannot be read. */
export const readGameAccess = (code: string): Promise<GameAccessState | null> => readOr(findGameAccess({path: {code}}), null)

export const saveGameAccess = (code: string, policy: ChannelAccessPolicy): Promise<Saved<GameAccessState> | Refused> =>
  refusable(setGameAccess({path: {code}, body: policy}), "The access could not be set.")

/** Moves one channel into the archive category, read only and kept for its history. */
export const archiveGameChannel = (id: string): Promise<{ok: true} | Refused> =>
  accepted(archiveChannel({path: {id}}), "The channel could not be archived.")
