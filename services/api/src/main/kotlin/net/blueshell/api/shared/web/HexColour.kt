package net.blueshell.api.shared.web

// TWIN: isHexColour in the frontend's components/island/colour.ts, so a page refuses what a request refuses.

/** A colour as a request carries it: a hash and six hex digits, or nothing for the island's own. */
const val HEX_COLOUR = "\\s*(#[0-9a-fA-F]{6})?\\s*"

const val HEX_COLOUR_REFUSED = "Write a colour as # and six hex digits."
