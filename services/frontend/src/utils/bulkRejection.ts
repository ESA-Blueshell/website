import type {ApiError, FieldValidationError} from "@/services/api"

/**
 * Codes a bulk endpoint returns when it refuses a request. The api never applies a
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
  /** The same user named twice, which means the client has lost count of its own rows. */
  duplicateUsers: "DuplicateUserIds",
  /** A user ticked back in that the action still does not write to. */
  nonRecipientForced: "NonRecipientForcedUserIds",
  /** A user ticked back in that the selection does not name at all. */
  unknownForced: "UnknownForcedUserIds",
  /** A date somebody in the batch is getting an email about, left out of the request. */
  dateRequired: "DateRequired",
  /** A date before the contribution period starts, or too long after it ends. */
  dateOutsidePeriod: "DateOutsideContributionPeriod",
  /** The chosen contribution period has gone. */
  unknownPeriod: "UnknownContributionPeriodId",
  /** External targets the system no longer has; the catalogue is stale. */
  unknownTargets: "UnknownTargetIds",
  /** The destination folder is not one the system has. Refused rather than created. */
  unknownFolder: "UnknownFolder",
} as const

/** The codes above, as a union. Named apart from the const so both can be exported. */
export type BulkRejectionCodeValue = (typeof BulkRejectionCode)[keyof typeof BulkRejectionCode]

/** One reason a selection was refused, with the ids it refers to. */
export interface BulkRejectionReason {
  code: BulkRejectionCodeValue | string
  field: string
  message: string
  userIds: number[]
  /**
   * The offending identifiers when they are not numeric. A user is a number; a list in an
   * external system is whatever that system calls it. A reason carries one or the other.
   */
  refs: string[]
}

export interface BulkRejection {
  reasons: BulkRejectionReason[]
  /** 409 means the client's table is stale; 400 that a field of the request is wrong. */
  status: number
  /** Every user id the api named, whatever the reason. */
  namedUserIds: number[]
  /** Every non-numeric identifier the api named, whatever the reason. */
  namedRefs: string[]
  /** True when a reason means the table is out of date rather than the choice invalid. */
  requiresReload: boolean
}

// A stale view of the data, which a reload fixes. `unknownFolder` is deliberately absent:
// the destination was wrong, and reloading the page will not make it right.
const RELOAD_CODES: readonly string[] = [
  BulkRejectionCode.unknownUsers,
  BulkRejectionCode.deletedUsers,
  BulkRejectionCode.unknownPeriod,
  BulkRejectionCode.unknownTargets,
]

/**
 * Sentences composed here from the code rather than taken from the api's `message`, per
 * ADR-026. The older codes keep the message the api composes for them.
 */
const COMPOSED_MESSAGES: Record<string, string> = {
  [BulkRejectionCode.duplicateUsers]: "The selection names the same member more than once.",
  [BulkRejectionCode.nonRecipientForced]:
    "Some of the members ticked back in are ones this send does not write to.",
  [BulkRejectionCode.unknownForced]:
    "Some of the members ticked back in are not in the selection.",
  [BulkRejectionCode.dateRequired]:
    "This date is required: somebody in this batch gets an email that states it.",
  [BulkRejectionCode.dateOutsidePeriod]:
    "This date must fall within the contribution period, or shortly after it ends.",
}

// Both advices answer in the same `errors[]` shape: 409 for a stale selection, 400 for a
// field a rule refused. One parser covers both.
const REFUSAL_STATUSES: readonly number[] = [400, 409]

function isApiError(value: unknown): value is ApiError {
  return typeof value === "object" && value !== null && "errors" in value
}

/**
 * Reads a refusal out of a generated-client result. Returns null for anything that is
 * not a refused request, so a caller can fall through to its ordinary error handling
 * rather than reporting a misleading reason.
 */
export function parseBulkRejection(result: {
  error?: unknown
  response?: {status?: number}
}): BulkRejection | null {
  const status = result.response?.status
  if (status == null || !REFUSAL_STATUSES.includes(status)) return null
  if (!isApiError(result.error)) return null

  const errors: FieldValidationError[] = result.error.errors ?? []
  const reasons = errors
    .filter((entry): entry is FieldValidationError & {code: string} => typeof entry.code === "string")
    .map((entry) => ({
      code: entry.code,
      field: entry.field ?? "",
      message: COMPOSED_MESSAGES[entry.code] ?? entry.message ?? "",
      userIds: (entry.values ?? []).filter((id): id is number => typeof id === "number"),
      refs: (entry.refs ?? []).filter((ref): ref is string => typeof ref === "string"),
    }))

  if (reasons.length === 0) return null

  return {
    reasons,
    status,
    namedUserIds: [...new Set(reasons.flatMap((reason) => reason.userIds))],
    namedRefs: [...new Set(reasons.flatMap((reason) => reason.refs))],
    requiresReload: reasons.some((reason) => RELOAD_CODES.includes(reason.code)),
  }
}
