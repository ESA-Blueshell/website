/**
 * One thing that happened, and the year it happened in.
 *
 * The story rather than the records: none of this is in a database, and putting it in one so a
 * page could read it back would be filing a paragraph as data.
 *
 * Two lengths, because a reader passing through wants a different thing from one who stopped.
 * The [summary] is always drawn and has to stand on its own; the [telling] is what they get
 * for stopping, and is a sentence or two more rather than an essay.
 */
export interface Milestone {
  year: string
  /** What it is called, short enough to sit under a year. */
  title: string
  /** One line, always visible: what happened, for somebody who reads nothing else. */
  summary: string
  /** The rest of it, drawn once this is the milestone being read. */
  telling: string
}

/**
 * The association's history, oldest first.
 *
 * Sourced from the association's own article in De Appel 47.1-2 and its 2025 partnership
 * overview. Each entry is a thing that happened, not a thing that is true now: "the largest
 * gaming association in the Netherlands" belongs on the page, not on the line.
 */
export const MILESTONES: readonly Milestone[] = [
  {
    year: "2017",
    title: "Two students, a few beers",
    summary: "Twente had an association for almost everything, and nothing for gamers.",
    telling:
      "Two Civil Engineering students decided that ought to be fixable. It began with a few "
      + "conversations at the Student Union, and three good friends pulled in to make up a "
      + "first board of five.",
  },
  {
    year: "2017",
    title: "The Kick-In",
    summary: "A stand with no association behind it yet drew over 120 students.",
    telling:
      "They took a stand to the Kick-In market before there was anything to join — more than "
      + "120 students signed up as interested, beating associations that had run for years. It "
      + "settled whether Twente actually wanted this.",
  },
  {
    year: "2017",
    title: "The statutes are signed",
    summary: "On 12 December the first board signed, and Blueshell became real.",
    telling:
      "What followed was the unglamorous part: a regular calendar, a place to meet people who "
      + "play what you play, and a standing invitation to anyone in Twente who games.",
  },
  {
    year: "2022",
    title: "A team of our own, in a league of our own",
    summary: "The first all-female team fielded in the Dutch College Esports Series.",
    telling:
      "A community that says it is for everyone has to keep proving it rather than announcing "
      + "it. A team is a more convincing argument than a paragraph.",
  },
  {
    year: "2022",
    title: "A room of our own",
    summary: "On 15 October the Predator Esports Lounge opened in the Bastille.",
    telling:
      "Ten gaming PCs across two booths, a sim-rig and a bar. Competitions are played in the "
      + "room rather than from six separate bedrooms, and our own teams' matches are showcased "
      + "and streamed from it.",
  },
  {
    year: "2024",
    title: "Three in a row",
    summary: "A third consecutive Dutch Student League title.",
    telling:
      "Won while insisting, to anyone who asked, that this is mainly a casual gaming "
      + "association. Both halves of that are true, and neither is an accident.",
  },
  {
    year: "Now",
    title: "The largest in the country",
    summary: "Over 200 members, 1,100 on Discord, fifteen member-run committees.",
    telling:
      "The largest gaming association in the Netherlands — a strange thing to be able to say "
      + "about something that started with two people deciding there ought to be somewhere to "
      + "play. Still run by the people who turn up.",
  },
]
