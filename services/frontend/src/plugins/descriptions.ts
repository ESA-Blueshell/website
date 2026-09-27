/**
 * The most a description holds: Discord's limit for an embed's description, so an event posted to
 * Discord shows whole (architecture ADR-010). DESCRIPTION_MAX in the api is the same number;
 * change one, change the other.
 */
export const DESCRIPTION_CAP = 4096
