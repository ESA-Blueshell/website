/**
 * Discord mentions adapter: the names behind a description's `<@id>`, `<@&id>` and `<#id>`, and
 * the channels a description may mention. Null where the api cannot ask Discord.
 */
import {type DiscordMentionsResponse, type DiscordNameResponse, listDiscordChannels, readDiscordMentions}
  from "@/services/api"
import {readOr} from "@/utils/answers"

export interface MentionIds {
  users: string[]
  roles: string[]
  channels: string[]
}

export const readMentionNames = (ids: MentionIds): Promise<DiscordMentionsResponse | null> =>
  readOr(readDiscordMentions({query: ids}), null)

export const listServerChannels = (): Promise<DiscordNameResponse[] | null> =>
  readOr(listDiscordChannels(), null)
