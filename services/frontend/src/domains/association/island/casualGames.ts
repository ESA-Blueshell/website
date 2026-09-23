import dota from "@/assets/dota2.png"
import dotaArt from "@/assets/dota2bg.jpg"
import minecraft from "@/assets/minecraft.png"
import minecraftArt from "@/assets/minecraftbg.jpg"
import overwatch from "@/assets/overwatch.png"
import overwatchArt from "@/assets/overwatchbg.jpg"
import smash from "@/assets/smash.png"
import smashArt from "@/assets/smashbg.jpg"
import trackmania from "@/assets/trackmania.png"
import trackmaniaArt from "@/assets/trackmaniabg.jpg"

/** A game members play together outside any team, and the channel it is played from. */
export interface CasualGame {
  name: string
  /** What playing it with the association is like, in a few words. */
  meta: string
  channel: string
  accent: string
  banner: string
  icon: string
}

/** Written here rather than read: nothing records casual play, and the list changes rarely. */
export const CASUAL_GAMES: CasualGame[] = [
  {name: "Minecraft", meta: "the association server", channel: "#minecraft", accent: "#6cbf3f", banner: minecraftArt, icon: minecraft},
  {name: "Dota 2", meta: "stacks most weeknights", channel: "#dota", accent: "#c23c2a", banner: dotaArt, icon: dota},
  {name: "Overwatch", meta: "casual queues, no roster", channel: "#overwatch", accent: "#f99e1a", banner: overwatchArt, icon: overwatch},
  {name: "Super Smash Bros", meta: "couch nights", channel: "#smash", accent: "#c8963c", banner: smashArt, icon: smash},
  {name: "Trackmania", meta: "weekly track", channel: "#trackmania", accent: "#1183d6", banner: trackmaniaArt, icon: trackmania},
]
