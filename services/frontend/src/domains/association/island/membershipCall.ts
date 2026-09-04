import type {Call} from "@/components/island/CallBand.vue"

/** Where the board answers, and the invite the rest of the site uses. */
const DISCORD = "https://discord.gg/23YMFQy"
const EMAIL = "board@blueshell.utwente.nl"

/**
 * The last band of the membership page, which is the second time it asks.
 *
 * The hero asks over the photograph and this asks again at the end, because the page between
 * them is the answer to "why". Signing up leads; the two ways of asking a person first stand
 * beside it, since somebody still deciding is not helped by a louder button.
 */
export const MEMBERSHIP_CALL: Call = {
  headline: "Ready when you are",
  eyebrow: "Sign up",
  body: "Signing up takes a few minutes and the board reads every application. If you would "
    + "rather ask something first, the board answers on Discord and by mail.",
  testid: "membership-join",
  actions: [
    {label: "Become a member", href: "/membership/signup", tone: "solid", testid: "membership-join-signup"},
    {label: "Ask on Discord", href: DISCORD, away: true, testid: "membership-join-discord"},
    {label: "Ask over email", href: `mailto:${EMAIL}`, tone: "quiet", testid: "membership-join-mail"},
  ],
}
