import type {Call} from "@/components/island/CallBand.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import ett from "@/assets/ett.png"
import ettDark from "@/assets/ettdark.png"
import elnino from "@/assets/elnino.png"
import maatwerk from "@/assets/association/partner-marketing-maatwerk.webp"
import type {Partner} from "./PartnerWall.vue"
import type {Perk} from "./PerkBand.vue"

/** What a membership gets somebody, each said as what they get. Prices live on the membership page. */
export const HOME_PERKS: Perk[] = [
  {id: "events", title: "Every event", body: "Game nights, LANs, tournaments and pub quizzes, most of them in the Predator Esports Lounge."},
  {id: "discord", title: "The entire Discord", body: "Every channel, including the voice rooms people sit in most nights of the week."},
  {id: "casual", title: "Casual gaming", body: "A channel for what you play, and people already in it who want another player."},
  {id: "competitive", title: "Competitive gaming", body: "Try out for a team, or start one in a game the association does not field yet."},
  {id: "community", title: "A welcoming community", body: "Turn up alone on a Tuesday from Twente or Saxion; you will not leave alone."},
  {id: "merch", title: "Our merch", body: "A jersey, a hoodie, a mousepad: something new every year, and the kit is good."},
]

/** The organisations that back the association now. */
export const HOME_PARTNERS: Partner[] = [
  {name: "El Niño", href: "/partners/el-nino", light: elnino, dark: elnino, invertInDark: true},
  {name: "Marketing Maatwerk", href: "/partners/marketing-maatwerk", light: maatwerk, dark: maatwerk, invertInDark: true},
  {name: "Esports Team Twente", href: "https://esportsteamtwente.nl/", light: ett, dark: ettDark},
]

/** The last band of the home page: the page's second invitation, after the hero's. */
export const HOME_CALL: Call = {
  eyebrow: "Membership",
  headline: "Play with us this year",
  body: "Signing up takes a minute, and you can be in a lobby the same evening.",
  testid: "home-call",
  actions: [
    {label: "Become a member", href: "/membership/signup", tone: "solid", testid: "home-call-signup"},
    {label: "Ask on Discord", href: DISCORD_INVITE, away: true, testid: "home-call-discord"},
  ],
}
