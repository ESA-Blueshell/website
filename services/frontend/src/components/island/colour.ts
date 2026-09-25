// TWIN: the accent patterns of CasualGameRequest and the board requests, so a page refuses what the api refuses.
const HEX = /^#[0-9a-f]{6}$/i

/** Whether [said] is a colour the site can carry: a hash and six hex digits. */
export const isHexColour = (said: string): boolean => HEX.test(said)
