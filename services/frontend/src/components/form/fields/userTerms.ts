import type {UserDetailResponse} from "@/domains/user"

/* What a person is found by beyond the name on the row: a handle, a number, an address. */
export const termsFor = (user: UserDetailResponse): string[] =>
  [
    user.fullName,
    user.firstName,
    user.lastName,
    user.prefix ?? "",
    user.initials,
    user.username,
    user.email,
    user.discord ?? "",
    user.phoneNumber ?? "",
  ].filter(said => said !== "")

/** What the row says: the name, and the handle where there is one to tell two names apart. */
export const nameOf = (user: UserDetailResponse): string =>
  user.fullName || user.email || `User #${user.id}`
