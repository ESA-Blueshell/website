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
