import {MemberType} from "@/services/api"

/**
 * Human-readable label for every member type in the generated OpenAPI enum.
 *
 * This map is keyed by `MemberType`, so it is EXHAUSTIVE by construction:
 * if the backend enum gains or renames a member, TypeScript fails to compile
 * until this map is updated. That is intentional — the contract enforces
 * completeness of the display mapping.
 */
const MEMBER_TYPE_LABELS: Record<MemberType, string> = {
  [MemberType.REGULAR]: "Regular",
  [MemberType.ALUMNI]: "Alumni",
  [MemberType.HONORARY]: "Honorary",
  [MemberType.NONE]: "None",
}

/**
 * Formats a member type for display to the user.
 *
 * - Known enum values are looked up in the exhaustive, contract-driven map.
 * - null/undefined renders as "—".
 * - Any unknown string (defensive; should not happen given the typed contract)
 *   falls back to Title-case (first letter upper, rest lower).
 */
export function memberTypeLabel(type: MemberType | string | null | undefined): string {
  if (type == null) return "—"

  const known = MEMBER_TYPE_LABELS[type as MemberType]
  if (known != null) return known

  // Defensive fallback for any value outside the generated enum.
  return type.charAt(0).toUpperCase() + type.slice(1).toLowerCase()
}
