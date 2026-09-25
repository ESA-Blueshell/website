/**
 * The user domain's public API — everything another domain, a page or a shared component may
 * reach for, and nothing else. Frontend ADR-001 puts one of these on every domain: a domain's
 * own files import each other directly, but anything outside it comes through here, so the
 * folders below stay free to move without the rest of the site noticing.
 *
 * This is the first one in the codebase, so it is also the pattern: re-export by name rather
 * than with `export *`, because the list of names is the promise being made.
 */
export {
  deleteAddress,
  deleteUser,
  readMemberProfile,
  saveAddressChange,
  saveNewAddress,
  saveNewUser,
  saveSignupAddress,
  saveSignupDetails,
  saveUser,
  startSignup,
  listAddresses,
  listDeletedUsers,
  listMemberships,
  listUsers,
  loadMemberAccounts,
  readAddress,
  readUser,
  saveNameOnRosters,
  searchMemberAccounts,
  type MemberAccount,
} from "./adapters/users"
export {resumeSignupSession} from "./adapters/signup"
export type {
  AddressResponse,
  BoardCreateMembershipRequest,
  CreateAddressRequest,
  CreateUserRequest,
  MemberProfileResponse,
  MembershipResponse,
  SignupAddressRequest,
  SignupDetailsRequest,
  SignupOutcomeResponse,
  SignupResumeResponse,
  SignupSessionResponse,
  UpdateAddressRequest,
  UpdateMembershipRequest,
  UpdateUserRequest,
  UpsertMemberProfileRequest,
  UserDetailResponse,
} from "@/services/api"
export {
  applyForMembership,
  deleteOneMembership,
  endOneMembership,
  endTheMemberships,
  listDeletedMembershipsFor,
  listMembershipsFor,
  readMembershipEnd,
  readMembershipStart,
  reopenOneMembership,
  restoreOneMembership,
  saveMembership,
  startMembershipAsBoard,
  startOwnMembership,
  startTheMemberships,
} from "./adapters/memberships"
export type {BulkActionResult, BulkMembershipPreview} from "@/services/api"
export {
  listRoleChanges,
  readRoleStanding,
  type RoleChange,
  type RoleStanding,
  saveRolesOrReason,
  type SaveRolesResult,
} from "./adapters/roles"
export {highestRole, highestRoleLabel} from "./roles"
export {MemberType, Role, RoleSource} from "@/services/api"
