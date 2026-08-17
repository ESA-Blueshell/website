import {MemberType} from "@/services/api"

// Keyed by MemberType, so a backend enum change fails the typecheck until
// this map is updated.
const MEMBER_TYPE_LABELS: Record<MemberType, string> = {
  [MemberType.REGULAR]: "Regular",
  [MemberType.ALUMNI]: "Alumni",
  [MemberType.HONORARY]: "Honorary",
  [MemberType.NONE]: "None",
}

/** Formats a member type for display; null/undefined renders as an em dash. */
export function memberTypeLabel(type: MemberType | string | null | undefined): string {
  if (type == null) return "—"

  const known = MEMBER_TYPE_LABELS[type as MemberType]
  if (known != null) return known

  // Defensive: a value outside the generated enum still renders readably.
  return type.charAt(0).toUpperCase() + type.slice(1).toLowerCase()
}
