/**
 * The addresses the site sends from and reads, and their logins. The login is written once and
 * never read back: the api keeps it in Vault and answers only whether one is kept.
 */
import {
  addSendingAddress,
  checkSendingAddress,
  listSendingAddresses,
  removeSendingAddress,
  type SendingAddress,
  type SendingAddressRequest,
  setSendingAddress,
  MailSecurity,
} from "@/services/api"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {accepted, refusable} from "../refusals"

export type {SendingAddress, SendingAddressRequest}
export {MailSecurity}

/** Every sending address, by address; none where they could not be read. */
export const loadSendingAddresses = (): Promise<SendingAddress[]> => readOr(listSendingAddresses(), [])

/** Adds an address once its server takes the login. */
export const addAddress = (body: SendingAddressRequest): Promise<Saved<SendingAddress> | Refused> =>
  refusable(addSendingAddress({body}), "The address could not be added.")

/** Saves an address; a login left out keeps the one there is. */
export const saveAddress = (id: number, body: SendingAddressRequest): Promise<Saved<SendingAddress> | Refused> =>
  refusable(setSendingAddress({path: {id}, body}), "The address could not be saved.")

/** Removes an address and its login. */
export const removeAddress = (id: number): Promise<{ok: true} | Refused> =>
  accepted(removeSendingAddress({path: {id}}), "The address could not be removed.")

/** Tries the address's servers with the login kept; a server that refuses is an answer, not a failure. */
export const checkAddress = (id: number): Promise<Saved<SendingAddress> | Refused> =>
  refusable(checkSendingAddress({path: {id}}), "The address could not be checked.")
