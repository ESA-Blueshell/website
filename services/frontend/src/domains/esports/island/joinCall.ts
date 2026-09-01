import type {Call} from "@/components/island/CallBand.vue"

/** The invite to where the board answers questions. */
const DISCORD = "https://discord.gg/cauRtRaqh"
const EMAIL = "esports-affairs@blueshell.utwente.nl"

/**
 * What the last band of an esports page asks for, and the three ways to answer it.
 *
 * Both esports pages end on this, so the copy is written once and read twice rather than
 * kept in step by hand. The band itself is the island's; what it says is esports'.
 */
export const JOIN_CALL: Call = {
  headline: "Want in?",
  body: "Membership is what puts you on a roster. If you would rather ask first, the board "
    + "answers on Discord and esports affairs answers by mail.",
  testid: "esports-join",
  actions: [
    // Joining is the one that puts somebody on a roster, so it leads.
    {label: "Become a member", href: "/membership", tone: "solid", testid: "esports-join-member"},
    {label: "Ask on Discord", href: DISCORD, away: true, testid: "esports-join-discord"},
    {label: "Ask over email", href: `mailto:${EMAIL}`, tone: "quiet", testid: "esports-join-mail"},
  ],
}
