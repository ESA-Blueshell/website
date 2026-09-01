import type {Call} from "@/components/island/CallBand.vue"

/** Where the board answers, and the invite the rest of the site uses. */
const DISCORD = "https://discord.gg/23YMFQy"
const EMAIL = "board@blueshell.utwente.nl"

/**
 * What the last band of the board page asks for.
 *
 * The history is an invitation rather than a museum: somebody who has just read nine years of
 * other people's board years is the person most likely to stand for one. The band itself is the
 * island's; what it says is the board's.
 *
 * DRAFT — the wording is the author's to settle (#930).
 */
export const BOARD_CALL: Call = {
  headline: "Stand for the board",
  body: "A board year is a year of running Blueshell: the events, the money, the members and the "
    + "games. Elections are in the spring and the handover is in the autumn, so the board in "
    + "office is the one to ask what the year is really like.",
  testid: "board-join",
  actions: [
    // Membership is what puts somebody in the room where a board is elected, so it leads.
    {label: "Become a member", href: "/membership", tone: "solid", testid: "board-join-member"},
    {label: "Ask on Discord", href: DISCORD, away: true, testid: "board-join-discord"},
    {label: "Ask over email", href: `mailto:${EMAIL}`, tone: "quiet", testid: "board-join-mail"},
  ],
}
