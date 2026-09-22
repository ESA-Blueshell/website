/**
 * Contribution period adapter: one of the few files in this domain that reaches the generated
 * client (frontend ADR-001). Everything else comes through the door.
 */
import {
  type ContributionPeriodResponse,
  createContributionPeriod,
  type CreateContributionPeriodRequest,
  deleteContributionPeriodById,
  findContributionPeriods,
  findCurrentContributionPeriod,
  updateContributionPeriod,
  type UpdateContributionPeriodRequest,
} from "@/services/api"

/**
 * The period contributions are charged over right now, or nothing where there is no open one.
 * Throws on a refusal, so a period that could not be read is not drawn as no period at all.
 */
export async function readCurrentPeriod(): Promise<ContributionPeriodResponse | null> {
  const res = await findCurrentContributionPeriod({throwOnError: true})
  return res.data ?? null
}

/**
 * Every period on file. Throws on a refusal rather than answering with an empty listing: a
 * listing that could not be read is not an association that has never charged anything.
 */
export async function listPeriods(): Promise<ContributionPeriodResponse[]> {
  const {data} = await findContributionPeriods({throwOnError: true})
  return data ?? []
}

/** Records a new period. Throws with the refusal the form reads its fields from. */
export async function saveNewPeriod(
  body: CreateContributionPeriodRequest,
): Promise<ContributionPeriodResponse> {
  const {data} = await createContributionPeriod({body, throwOnError: true})
  return data!
}

/** Records a change to a period. Throws with the refusal the form reads its fields from. */
export async function savePeriod(
  id: number,
  body: UpdateContributionPeriodRequest,
): Promise<ContributionPeriodResponse> {
  const {data} = await updateContributionPeriod({path: {id}, body, throwOnError: true})
  return data!
}

/** Removes the period, throwing on a refusal so the caller reports it rather than reading on. */
export async function deletePeriod(id: number): Promise<void> {
  await deleteContributionPeriodById({path: {id}, throwOnError: true})
}
