/**
 * The roles and channels the site keeps on Discord, for the pickers that link them. Empty where the
 * api has no bot in the server.
 */
import {type KeptChannel, type KeptRole, listKeptChannels, listKeptRoles} from "@/services/api"
import {readOr} from "@/utils/answers"

export type {KeptChannel, KeptRole}

export const listKeepableRoles = (): Promise<KeptRole[]> => readOr(listKeptRoles(), [])

export const listKeepableChannels = (): Promise<KeptChannel[]> => readOr(listKeptChannels(), [])
