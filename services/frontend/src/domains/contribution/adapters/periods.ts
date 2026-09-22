/**
 * Contribution period adapter: one of the few files in this domain that reaches the generated
 * client (frontend ADR-001). Everything else comes through the door.
 */
import {type ContributionPeriodResponse, findCurrentContributionPeriod} from "@/services/api"

/**
 * The period contributions are charged over right now, or nothing where there is no open one.
 * Throws on a refusal, so a period that could not be read is not drawn as no period at all.
 */
export async function readCurrentPeriod(): Promise<ContributionPeriodResponse | null> {
  const res = await findCurrentContributionPeriod({throwOnError: true})
  return res.data ?? null
}
