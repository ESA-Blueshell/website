import elnino from "@/assets/elnino.png"
import maatwerk from "@/assets/association/partner-marketing-maatwerk.webp"

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
  "marketing-maatwerk": {
    name: "Marketing Maatwerk",
    logo: {src: maatwerk, alt: "Marketing Maatwerk logo", invertInDark: true},
    site: "https://marketingmaatwerk.nl/",
    facts: {
      heading: "Contact",
      items: [
        {label: "Website", value: "marketingmaatwerk.nl", href: "https://marketingmaatwerk.nl/"},
        {label: "Email", value: "info@marketingmaatwerk.nl", href: "mailto:info@marketingmaatwerk.nl"},
        {label: "Phone", value: "+31 6 34218964", href: "tel:+31634218964"},
        {label: "Contact form", value: "marketingmaatwerk.nl/contact", href: "https://marketingmaatwerk.nl/contact/"},
      ],
    },
    sections: [
      {
        heading: "About Marketing Maatwerk",
        paragraphs: [
          "Marketing Maatwerk specializes in building and strengthening the online presence of entrepreneurs. With a "
          + "deep passion for web technology, we translate complex digital challenges into clear, effective solutions. "
          + "The goal is simple: ensure entrepreneurs get found online, make a professional impression, and achieve "
          + "sustainable growth.",
        ],
      },
      {
        heading: "What Marketing Maatwerk does",
        paragraphs: ["Our services focus on the three pillars of online success:"],
        offers: [
          {
            title: "Professional Websites",
            body: "From fully custom builds to the 'Website in 1 Day' concept: fast, user-friendly and "
              + "conversion-focused websites.",
            href: "https://marketingmaatwerk.nl/website-maatwerk/",
          },
          {
            title: "Search Engine Optimization (SEO)",
            body: "Technical audits, continuous optimization and clear reporting to rank for the keywords that truly "
              + "matter.",
            href: "https://marketingmaatwerk.nl/seo/",
          },
          {
            title: "Reliable Web Hosting",
            body: "Optimized hosting with WordPress core and plugin maintenance included for a fast, secure "
              + "foundation.",
            href: "https://marketingmaatwerk.nl/webhosting/",
          },
        ],
      },
      {
        heading: "Focus on a strong foundation",
        paragraphs: [
          "The philosophy of Marketing Maatwerk is that true online success starts with a rock-solid technical "
          + "foundation. Instead of quick fixes, we invest in a sustainable strategy. The approach is expert yet "
          + "accessible: technical subjects are explained in understandable language, so the entrepreneur knows "
          + "exactly what is happening and why it matters.",
        ],
      },
      {
        heading: "Who is Marketing Maatwerk for?",
        paragraphs: [
          "Marketing Maatwerk helps ambitious entrepreneurs and SMEs who understand that a professional online "
          + "presence is essential. We often work with business owners who want to focus on their own expertise and "
          + "confidently leave the technical aspects to a proactive specialist.",
        ],
      },
    ],
    actions: [
      {label: "Visit website", href: "https://marketingmaatwerk.nl/"},
      {label: "Go to contact form", href: "https://marketingmaatwerk.nl/contact/"},
    ],
  },
} as const satisfies Record<string, PartnerContent>

export type PartnerSlug = keyof typeof PARTNER_PAGES
