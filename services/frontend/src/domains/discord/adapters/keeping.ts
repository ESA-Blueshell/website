/**
 * The roles and channels the site keeps on Discord, for the pickers that link them. Nothing where
 * they could not be read, so an empty server reads apart from an unreachable one.
 */
import {type KeptChannel, type KeptRole, listKeptChannels, listKeptRoles} from "@/services/api"
import {readOr} from "@/utils/answers"

export type {KeptChannel, KeptRole}

export const listKeepableRoles = (): Promise<KeptRole[] | null> => readOr(listKeptRoles(), null)

export const listKeepableChannels = (): Promise<KeptChannel[] | null> => readOr(listKeptChannels(), null)
