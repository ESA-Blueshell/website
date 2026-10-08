import {type FirstContribution, findOwnFirstContribution} from "@/services/api"
import {readOr} from "@/utils/answers"

export type {FirstContribution}

/** What the reader pays to make their pending membership active, or nothing where none is pending. */
export const readFirstContribution = (): Promise<FirstContribution | null> => readOr(findOwnFirstContribution(), null)
