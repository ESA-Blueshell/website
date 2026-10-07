/**
 * The addresses written email can go out from, and their SMTP logins. The login is written once
 * and never read back: the api keeps it in Vault and answers only whether one is kept.
 */
import {
  addSendingAddress,
  listSendingAddresses,
  removeSendingAddress,
  type SendingAddress,
  type SendingAddressRequest,
  setSendingAddress,
  SmtpSecurity,
} from "@/services/api"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"
import {readOr} from "@/utils/answers"
import {accepted, refusable} from "../refusals"

export type {SendingAddress, SendingAddressRequest}
export {SmtpSecurity}

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
