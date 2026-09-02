/**
 * The user domain's public API — everything another domain, a page or a shared component may
 * reach for, and nothing else. Frontend ADR-001 puts one of these on every domain: a domain's
 * own files import each other directly, but anything outside it comes through here, so the
 * folders below stay free to move without the rest of the site noticing.
 *
 * This is the first one in the codebase, so it is also the pattern: re-export by name rather
 * than with `export *`, because the list of names is the promise being made.
 */
export {loadMemberAccounts, type MemberAccount} from "./adapters/users"
