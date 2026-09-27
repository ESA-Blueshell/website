/**
 * Discord mentions adapter: the names behind a description's `<@id>`, `<@&id>` and `<#id>`, and
 * the channels a description may mention. Null where the api cannot ask Discord.
 */
import {type DiscordMentionsResponse, type DiscordNameResponse, listDiscordChannels, readDiscordMentions}
  from "@/services/api"

export interface MentionIds {
  users: string[]
  roles: string[]
  channels: string[]
}

export async function readMentionNames(ids: MentionIds): Promise<DiscordMentionsResponse | null> {
  const {data, error} = await readDiscordMentions({query: ids})
  return error || !data ? null : data
}

export async function listServerChannels(): Promise<DiscordNameResponse[] | null> {
  const {data, error} = await listDiscordChannels()
  return error || !data ? null : data
}
