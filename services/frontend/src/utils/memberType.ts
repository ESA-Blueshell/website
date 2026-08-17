/** Formats a member type for display; null and undefined render as an em dash. */
export function memberTypeLabel(type: string | null | undefined): string {
  return type ? type.charAt(0) + type.slice(1).toLowerCase() : "—"
}
