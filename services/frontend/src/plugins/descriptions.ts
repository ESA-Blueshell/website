/**
 * The most a description holds (architecture ADR-010). An event's Discord post shows it whole but
 * for the last few hundred characters, which share the post with its details. DESCRIPTION_MAX in
 * the api is the same number; change one, change the other.
 */
export const DESCRIPTION_CAP = 4096
