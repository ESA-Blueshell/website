// TWIN: HEX_COLOUR in the api's shared/web/HexColour.kt, which every colour a request carries is held to.
const HEX = /^#[0-9a-f]{6}$/i

/** Whether [said] is a colour the site can carry: a hash and six hex digits. */
export const isHexColour = (said: string): boolean => HEX.test(said)
