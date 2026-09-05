/**
 * One thing that happened, and the year it happened in.
 *
 * The story rather than the records: none of this is in a database, and putting it in one so a
 * page could read it back would be filing a paragraph as data.
 */
export interface Milestone {
  year: string
  /** What it is called, short enough to sit under a year. */
  title: string
  /** The paragraph a reader gets when they stop here. */
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
    telling:
      "The University of Twente had associations for nearly everything and nothing at all for "
      + "gamers. Two Civil Engineering students decided that ought to be fixable, talked to the "
      + "Student Union, and pulled in three good friends to make a board.",
  },
  {
    year: "2017",
    title: "The Kick-In",
    telling:
      "They took a stand to the Kick-In market with no association behind them yet, and left "
      + "with over 120 interested students — more than associations that had existed for years.",
  },
  {
    year: "2017",
    title: "The statutes are signed",
    telling:
      "On the twelfth of December the first board signed the official statutes, and Blueshell "
      + "stopped being an idea two people had in a bar.",
  },
  {
    year: "2022",
    title: "A team of our own, in a league of our own",
    telling:
      "Blueshell fielded an all-female team in the Dutch College Esports Series, because a "
      + "community that says it is for everyone has to keep proving it rather than announcing it.",
  },
  {
    year: "2024",
    title: "Three in a row",
    telling:
      "Blueshell won the Dutch Student League for the third consecutive time — while insisting, "
      + "to anyone who asked, that it is mainly a casual gaming association.",
  },
  {
    year: "Now",
    title: "The largest in the country",
    telling:
      "Over 200 members, more than eleven hundred people on the Discord, fifteen member-run "
      + "committees, and a room in the Bastille where somebody is always playing something.",
  },
]
