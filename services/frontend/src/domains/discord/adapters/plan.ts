/** What a bulk add of Discord roles and channels will do, row by row, as the api works it out from the server now. */
import {type DiscordPlanRow, planDiscord} from "@/services/api"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {refusable} from "../refusals"

export type {DiscordPlanRow}

/** The plan for each row: the cohort's key and the channel it would get by name, or none. */
export const planBulkAdd = (rows: Array<{key: string; channel: string | null}>): Promise<Saved<DiscordPlanRow[]> | Refused> =>
  refusable(planDiscord({body: {rows}}), "What Discord would get could not be worked out.")
