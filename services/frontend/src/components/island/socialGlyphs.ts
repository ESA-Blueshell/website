import {discordInvite} from "@/domains/discord"

/**
 * The association's social accounts as filled glyphs: one `currentColor` fill with the details
 * cut out, never outlined and never brand-coloured, so a row of them reads as one set.
 */
export interface SocialGlyph {
  label: string
  href: string
  viewBox: string
  paths: string[]
  /** The details are holes in the fill, which only an even-odd fill rule cuts out. */
  evenOdd?: boolean
}

/** The invite into #welcome, through the api's bot. */
export const DISCORD_INVITE = discordInvite("welcome")

export const SOCIAL_GLYPHS = {
  discord: {
    label: "Discord",
    href: DISCORD_INVITE,
    viewBox: "0 0 24 24",
    paths: ["M20.317 4.3698a19.7913 19.7913 0 0 0-4.8851-1.5152.0741.0741 0 0 0-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 0 0-.0785-.037 19.7363 19.7363 0 0 0-4.8852 1.515.0699.0699 0 0 0-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 0 0 .0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 0 0 .0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 0 0-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 0 1-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 0 1 .0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 0 1 .0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 0 1-.0066.1276 12.2986 12.2986 0 0 1-1.873.8914.0766.0766 0 0 0-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 0 0 .0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 0 0 .0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 0 0-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"],
  },
  instagram: {
    label: "Instagram",
    href: "https://www.instagram.com/esablueshell/",
    viewBox: "0 0 20 20",
    evenOdd: true,
    paths: ["M6.2 2h7.6A4.2 4.2 0 0 1 18 6.2v7.6a4.2 4.2 0 0 1-4.2 4.2H6.2A4.2 4.2 0 0 1 2 13.8V6.2A4.2 4.2 0 0 1 6.2 2Zm3.8 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm0 1.7a2.3 2.3 0 1 1 0 4.6 2.3 2.3 0 0 1 0-4.6Zm4.4-3.2a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2Z"],
  },
  twitch: {
    label: "Twitch",
    href: "https://www.twitch.tv/blueshellesports",
    viewBox: "0 0 20 20",
    evenOdd: true,
    paths: [
      "M4.3 2 2.5 5.6V16h4v2.5h2.4l2.5-2.5h3.3l3.8-3.8V2Zm1.6 1.6h10.5v7.9l-2.4 2.4h-3.4l-2.3 2.3v-2.3H5.9Z",
      "M8.7 6.3h1.7v4.5H8.7Zm4.2 0h1.7v4.5h-1.7Z",
    ],
  },
  linkedin: {
    label: "LinkedIn",
    href: "https://www.linkedin.com/company/blueshell-esports",
    viewBox: "0 0 20 20",
    evenOdd: true,
    paths: ["M3.3 2h13.4c.7 0 1.3.6 1.3 1.3v13.4c0 .7-.6 1.3-1.3 1.3H3.3c-.7 0-1.3-.6-1.3-1.3V3.3C2 2.6 2.6 2 3.3 2ZM5 8.2v6.8h2.2V8.2Zm1.1-3.6a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6Zm2.8 3.6V15h2.2v-3.6c0-1 .4-1.6 1.2-1.6s1.1.6 1.1 1.6V15h2.2v-4.2c0-1.9-.9-2.8-2.4-2.8-1 0-1.7.5-2.1 1.1v-.9Z"],
  },
  facebook: {
    label: "Facebook",
    href: "https://www.facebook.com/BlueshellEsports/",
    viewBox: "0 0 20 20",
    paths: ["M18 10a8 8 0 1 0-9.25 7.9v-5.59H6.72V10h2.03V8.24c0-2 1.2-3.1 3.02-3.1.87 0 1.79.15 1.79.15v1.97h-1.01c-.99 0-1.3.62-1.3 1.25V10h2.22l-.36 2.31h-1.86v5.59A8 8 0 0 0 18 10Z"],
  },
  x: {
    label: "X",
    href: "https://twitter.com/BlueshellESA",
    viewBox: "0 0 24 24",
    paths: ["M18.9 1.2h3.7l-8.04 9.19L24 22.85h-7.4l-5.8-7.58-6.64 7.58H.47l8.6-9.83L0 1.15h7.6l5.24 6.93Zm-1.3 19.44h2.04L6.49 3.24H4.3Z"],
  },
  email: {
    label: "Email the board",
    href: "mailto:board@blueshell.utwente.nl",
    viewBox: "0 0 20 20",
    evenOdd: true,
    // A closed envelope, its flap a line cut through the fill: one path, so the even-odd rule
    // can cut it.
    paths: ["M3.6 4h12.8A1.6 1.6 0 0 1 18 5.6v8.8a1.6 1.6 0 0 1-1.6 1.6H3.6A1.6 1.6 0 0 1 2 14.4V5.6A1.6 1.6 0 0 1 3.6 4Zm.3 2.1v1.8l6.1 4.5 6.1-4.5V6.1L10 10.6Z"],
  },
} as const satisfies Record<string, SocialGlyph>

/** Every account, in the one order every row of them is drawn in. */
export const SOCIAL_ROW: readonly SocialGlyph[] = [
  SOCIAL_GLYPHS.discord, SOCIAL_GLYPHS.instagram, SOCIAL_GLYPHS.twitch, SOCIAL_GLYPHS.linkedin,
  SOCIAL_GLYPHS.facebook, SOCIAL_GLYPHS.x, SOCIAL_GLYPHS.email,
]

/** A mail link opens the mail app; anything else is somewhere else, so it gets a tab. */
export const opensTab = (href: string): boolean => !href.startsWith("mailto:")
