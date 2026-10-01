/** What the Discord page reads: every channel with who it opens to, and the roles and channels committees and teams could adopt. */
import {type AdoptionMatch, type CataloguedChannel, adoptDiscordMatches, listCataloguedChannels, listDiscordMatches} from "@/services/api"
import {readOr} from "@/utils/answers"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {refusable} from "../refusals"

export type {AdoptionMatch, CataloguedChannel}

export const listCatalogue = (): Promise<CataloguedChannel[]> => readOr(listCataloguedChannels(), [])

export const listMatches = (): Promise<AdoptionMatch[]> => readOr(listDiscordMatches(), [])

/** Links the confirmed matches by their cohort's key; answers how many were linked. */
export async function adoptMatches(keys: string[]): Promise<Saved<number> | Refused> {
  const answer = await refusable(adoptDiscordMatches({body: {keys}}), "The matches could not be linked.")
  return answer.ok ? {ok: true, saved: answer.saved.linked} : answer
}
