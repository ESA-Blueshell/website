import type {ApiError, FieldValidationError} from "@/services/api"

/**
 * Codes a bulk endpoint returns when it refuses a selection. The api never applies a
 * selection partly, so a refusal means nothing was written and the offending rows are
 * named in `values`.
 */
export const BulkRejectionCode = {
  /** Ids that were never users. */
  unknownUsers: "UnknownUserIds",
  /** Users that have been deleted; deletion anonymises the account and keeps the row. */
  deletedUsers: "DeletedUserIds",
  /** Honorary members, who owe no contribution. */
  honoraryUsers: "HonoraryUserIds",
  /** The chosen contribution period has gone. */
  unknownPeriod: "UnknownContributionPeriodId",
} as const

/** The codes above, as a union. Named apart from the const so both can be exported. */
export type BulkRejectionCodeValue = (typeof BulkRejectionCode)[keyof typeof BulkRejectionCode]

/** One reason a selection was refused, with the ids it refers to. */
export interface BulkRejectionReason {
  code: BulkRejectionCodeValue | string
  field: string
  message: string
  userIds: number[]
}

export interface BulkRejection {
  reasons: BulkRejectionReason[]
  /** Every user id the api named, whatever the reason. */
  namedUserIds: number[]
  /** True when a reason means the table is out of date rather than the choice invalid. */
  requiresReload: boolean
}

const RELOAD_CODES: readonly string[] = [
  BulkRejectionCode.unknownUsers,
  BulkRejectionCode.deletedUsers,
  BulkRejectionCode.unknownPeriod,
]

function isApiError(value: unknown): value is ApiError {
  return typeof value === "object" && value !== null && "errors" in value
}

/**
 * Reads a refusal out of a generated-client result. Returns null for anything that is
 * not a refused selection, so a caller can fall through to its ordinary error handling
 * rather than reporting a misleading reason.
 */
export function parseBulkRejection(result: {
  error?: unknown
  response?: {status?: number}
}): BulkRejection | null {
  if (result.response?.status !== 409) return null
  if (!isApiError(result.error)) return null

  const errors: FieldValidationError[] = result.error.errors ?? []
  const reasons = errors
    .filter((entry): entry is FieldValidationError & {code: string} => typeof entry.code === "string")
    .map((entry) => ({
      code: entry.code,
      field: entry.field ?? "",
      message: entry.message ?? "",
      userIds: (entry.values ?? []).filter((id): id is number => typeof id === "number"),
    }))

  if (reasons.length === 0) return null

  return {
    reasons,
    namedUserIds: [...new Set(reasons.flatMap((reason) => reason.userIds))],
    requiresReload: reasons.some((reason) => RELOAD_CODES.includes(reason.code)),
  }
}
