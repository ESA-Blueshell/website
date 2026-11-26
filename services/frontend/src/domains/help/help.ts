/** Who a help topic is for; `everyone` is anybody signed in. */
export type Audience = "everyone" | "member" | "committee" | "board" | "treasurer" | "admin"

/** Every audience, in the order a person grows into them. */
const AUDIENCES: Audience[] = ["everyone", "member", "committee", "board", "treasurer", "admin"]

export interface HelpLink {
  label: string
  to: string
}

/** One thing a reader wants to do or know: what it is, the steps where it has any, and where to do it. */
export interface HelpTopic {
  id: string
  title: string
  audience: Audience
  body: string[]
  steps?: string[]
  links?: HelpLink[]
}

export interface HelpSection {
  id: string
  title: string
  topics: HelpTopic[]
}

/**
 * The roles in force for the reader. A login carries inherited roles, so an admin holds them all.
 * No rule of the api names the treasurer apart from the board, so `treasurer` is whether the
 * reader may do the treasurer's work, which every board member may.
 */
export interface HelpReader {
  member: boolean
  committee: boolean
  board: boolean
  treasurer: boolean
  admin: boolean
}

const sectionsWith = (sections: HelpSection[], keeps: (topic: HelpTopic, section: HelpSection) => boolean): HelpSection[] =>
  sections
    .map(section => ({...section, topics: section.topics.filter(topic => keeps(topic, section))}))
    .filter(section => section.topics.length > 0)

/** The topics a reader may act on. What they could not use is left out rather than shown as closed. */
export const helpFor = (sections: HelpSection[], reader: HelpReader): HelpSection[] =>
  sectionsWith(sections, topic => topic.audience === "everyone" || reader[topic.audience])

/** The audiences these sections have a topic for. */
export const audiencesIn = (sections: HelpSection[]): Audience[] =>
  AUDIENCES.filter(audience => sections.some(section => section.topics.some(topic => topic.audience === audience)))

/** The topics written for one audience, or all of them where none is picked. */
export const helpAbout = (sections: HelpSection[], audience: Audience | null): HelpSection[] =>
  (audience === null ? sections : sectionsWith(sections, topic => topic.audience === audience))

/** The topics holding every word typed, in the section's name, the topic's own text or the pages it links to. */
export function searchHelp(sections: HelpSection[], term: string): HelpSection[] {
  const words = term.toLowerCase().split(/\s+/u).filter(Boolean)
  if (words.length === 0) return sections
  return sectionsWith(sections, (topic, section) => {
    const text = [section.title, topic.title, ...topic.body, ...(topic.steps ?? []), ...(topic.links ?? []).map(link => link.label)]
      .join(" ")
      .toLowerCase()
    return words.every(word => text.includes(word))
  })
}
