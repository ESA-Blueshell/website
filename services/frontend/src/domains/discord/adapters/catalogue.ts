/** What the Discord page reads: every channel with who it opens to, and the roles and channels committees and teams could adopt. */
import {type AdoptionMatch, type CataloguedChannel, adoptDiscordMatches, listCataloguedChannels, listDiscordMatches} from "@/services/api"
import {readOr} from "@/utils/answers"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {refusable} from "../refusals"

export type {AdoptionMatch, CataloguedChannel}

export const listCatalogue = (): Promise<CataloguedChannel[]> => readOr(listCataloguedChannels(), [])

export const listMatches = (): Promise<AdoptionMatch[]> => readOr(listDiscordMatches(), [])

/** What linking the confirmed matches came to: how many were linked, and the ones Discord refused with why. */
export type Adopted = {linked: number; refused: {label: string; reason: string}[]}

/** Links the confirmed matches by their cohort's key. A match Discord refuses does not stop the others. */
export async function adoptMatches(keys: string[]): Promise<Saved<Adopted> | Refused> {
  const answer = await refusable(adoptDiscordMatches({body: {keys}}), "The matches could not be linked.")
  return answer.ok ? {ok: true, saved: {linked: answer.saved.linked, refused: answer.saved.refused}} : answer
}
