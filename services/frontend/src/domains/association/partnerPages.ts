import elnino from "@/assets/elnino.png"

/** One line of a partner's facts: a location, or a way to reach them when it has an `href`. */
export interface PartnerFact {
  label: string
  value: string
  href?: string
}

/** Something a partner does for people, leading to where they describe it. */
export interface PartnerOffer {
  title: string
  body: string
  href: string
}

export interface PartnerSection {
  heading: string
  paragraphs: readonly string[]
  offers?: readonly PartnerOffer[]
}

/**
 * A partner's page as data, so a third partner is an entry here rather than a page.
 *
 * Kept in the frontend: `SponsorResponse` carries no logo or url, and its read needs a role.
 */
export interface PartnerContent {
  name: string
  /** The heading's second line, where the partner has one. */
  tagline?: string
  logo: {src: string, alt: string, invertInDark: boolean}
  site: string
  facts: {heading: string, items: readonly PartnerFact[]}
  sections: readonly PartnerSection[]
  actions: readonly {label: string, href: string}[]
}

export const PARTNER_PAGES = {
  "el-nino": {
    name: "El Niño",
    tagline: "Digital Development",
    logo: {src: elnino, alt: "El Niño logo", invertInDark: true},
    site: "https://www.elnino.tech/",
    facts: {
      heading: "Locations",
      items: [
        {label: "Enschede", value: "Kuipersdijk 6C"},
        {label: "The Hague", value: "Waldorpstraat 17Q"},
      ],
    },
    sections: [
      {
        heading: "Something about us",
        paragraphs: [
          "We’re a group of technology enthusiasts working on challenging projects for customers all around the "
          + "world and active in different sectors. We’re dedicated to building custom web applications, e-commerce "
          + "platforms, mobile apps and connected hardware devices. We have an international team of professionals "
          + "(partly still students) eager to learn new things and love the challenge on working on projects that "
          + "haven’t been done before.",
        ],
      },
      {
        heading: "What we offer",
        paragraphs: [
          "We offer everybody flexible working hours, a gym you can use for free at the office in Enschede, your own "
          + "MacBook Pro and desk, yearly learning budget, takeout meals, movie nights at the office and many more "
          + "activities we organise for you and sometimes for your partner. If you’re a student, you can easily "
          + "combine work with your studies due to our flexible working hours and allowing you to work remotely when "
          + "necessary.",
        ],
      },
      {
        heading: "What we’re looking for",
        paragraphs: [
          "We’re looking for talented people in many areas: web development, hardware development, design (UX + UI), "
          + "testing, devops and data science. If you’ve experience in any of these areas and are interested to join "
          + "our team, let us know!",
        ],
      },
      {
        heading: "Join us!",
        paragraphs: [
          "We are always in the look out of new talent to help us build cool stuff! Whether you’re a student who just "
          + "started, a master student or a starter, we have always a special place for you.",
          "The vacancies are linked above, in Dutch and in English. You can always contact Michael on WhatsApp: "
          + "+31626978392. Don’t worry he won’t bite!",
        ],
      },
    ],
    actions: [
      {label: "Vacancies (Dutch)", href: "https://www.elnino.tech/vacatures"},
      {label: "Get a job (English)", href: "https://www.elnino.tech/getajob"},
      {label: "WhatsApp Michael", href: "https://wa.me/31626978392"},
    ],
  },
} as const satisfies Record<string, PartnerContent>

export type PartnerSlug = keyof typeof PARTNER_PAGES
