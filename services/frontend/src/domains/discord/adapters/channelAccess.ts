/**
 * A game's channels on Discord: making one with the default access, and reading and setting how far
 * everybody and members get into one. The access is written to Discord only when it is set here.
 */
import {
  type ChannelAccessPolicy,
  type ChannelAccessState,
  createGameChannel,
  findChannelAccess,
  GameChannelCategory,
  setChannelAccess,
} from "@/services/api"
import type {Refused} from "@/types/api"
import {readOr} from "@/utils/answers"
import type {Saved} from "@/utils/refusals"
import {refusable} from "../refusals"
import type {GameRoom} from "./channels"

export type {ChannelAccessPolicy, ChannelAccessState}
export {ChannelAccess} from "@/services/api"

/** Makes a games or esports channel, everybody reading and members writing, for the form to add. */
export const makeGameChannel = (name: string, category: GameChannelCategory): Promise<Saved<GameRoom> | Refused> =>
  refusable(createGameChannel({body: {name, category}}), "The channel could not be made.")

export const readChannelAccess = (id: string): Promise<ChannelAccessState | null> => readOr(findChannelAccess({path: {id}}), null)

export const saveChannelAccess = (id: string, policy: ChannelAccessPolicy): Promise<Saved<ChannelAccessState> | Refused> =>
  refusable(setChannelAccess({path: {id}, body: policy}), "The channel's access could not be set.")
