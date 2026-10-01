/**
 * The mail domain's public API: writing an email on the site (frontend ADR-001).
 */
export {
  AddresseeKind,
  listAudiences,
  listReplyTo,
  readReach,
  Role,
  sendTest,
  sendWritten,
  type Addressee,
  type Audience,
  type ReachResponse,
  type WriteEmailRequest,
} from "./adapters/writing"
export {addresseeKey, addresseeOf} from "./addressees"
export {InboxState, loadInboxPage, readInboxCounts, type InboxCounts, type InboxEntry} from "./adapters/inbox"
export {followsOf, inboxStateWord} from "./inbox"
