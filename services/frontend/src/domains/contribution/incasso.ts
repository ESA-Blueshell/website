/** What an account carries of its IBAN without a reveal: the country code and the last two characters. */
export type IbanMask = {ibanCountry?: string | null; ibanLastTwo?: string | null}

/** An account shown by its country code and last two characters only, as `NL•• … ••34`. */
export const maskedIban = (account: IbanMask | null | undefined, none = "None recorded"): string =>
  (account?.ibanCountry && account.ibanLastTwo ? `${account.ibanCountry}•• … ••${account.ibanLastTwo}` : none)
