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
 * A motivation letter over email is the way in, and the open window runs March to May. The
 * Discord line stands all year, so the band leads with talking to the board in office:
 * somebody wondering in October should not read this as a door that is shut.
 */
export const BOARD_CALL: Call = {
  headline: "Put your name forward",
  body: "If you want to improve the association and help build a welcoming, inclusive "
    + "community, the board may be for you. Applications are generally open March to May, by "
    + "motivation letter over email, and you can ask on Discord any time of year.",
  testid: "board-join",
  actions: [
    // Asking is the act, so the two ways of asking lead and nothing is put in front of them.
    {label: "Ask on Discord", href: DISCORD, away: true, tone: "solid", testid: "board-join-discord"},
    {label: "Ask over email", href: `mailto:${EMAIL}`, testid: "board-join-mail"},
  ],
}
