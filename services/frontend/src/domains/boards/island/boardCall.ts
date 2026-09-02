import type {Call} from "@/components/island/CallBand.vue"

/** Where the board answers, and the invite the rest of the site uses. */
const DISCORD = "https://discord.gg/23YMFQy"
const EMAIL = "board@blueshell.utwente.nl"

/**
 * What the last band of the board page asks for.
 *
 * The history is an invitation rather than a museum: somebody who has just read nine years of
 * other people's board years is the person most likely to ask for one. The band itself is the
 * island's; what it says is the board's.
 *
 * There is no election and no route to earn: anybody in the association can ask, and asking is
 * a conversation rather than an application. So the way in leads with talking to the board in
 * office rather than with joining anything, and nothing here suggests a season to wait for.
 */
export const BOARD_CALL: Call = {
  headline: "Put your name forward",
  body: "Anyone in the association can ask to be on a board. There is no election and no waiting "
    + "your turn. A year on the board is a year of running the place: the events, the money, the "
    + "lounge and the games, and the board in office is the one to ask what that is really like.",
  testid: "board-join",
  actions: [
    // Asking is the act, so the two ways of asking lead and nothing is put in front of them.
    {label: "Ask on Discord", href: DISCORD, away: true, tone: "solid", testid: "board-join-discord"},
    {label: "Ask over email", href: `mailto:${EMAIL}`, testid: "board-join-mail"},
  ],
}
