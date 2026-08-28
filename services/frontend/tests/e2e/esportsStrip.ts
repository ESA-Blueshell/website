/**
 * Eight seasons of one game, for the specs that are about the strip itself.
 *
 * Both the strip's own behaviour and its choreography need more seasons than fit the window,
 * which the two the rest of the suite gets never do. They live here rather than in either
 * spec because the deterministic half and the motion half both read them.
 */
export const eightSeasons = Array.from({length: 8}, (_, i) => ({
  id: 60 + i,
  name: `${i % 2 === 0 ? "Autumn" : "Spring"} ${2018 + i}/${19 + i}`,
  startDate: `${2018 + i}-09-01`,
  endDate: `${2019 + i}-01-31`,
}))

const pageOf = (index: number, teamId: number, teamName: string) => ({
  game: "VALORANT",
  season: eightSeasons[index],
  seasons: eightSeasons,
  teams: [{
    id: teamId,
    name: teamName,
    image: "valorantesports1.jpg",
    members: [{role: "PLAYER", handle: "AriosFury"}],
  }],
})

/** The newest season is what the api answers with when none was asked for. */
const newest = pageOf(7, 51, "BS Waterboarders")

/** Ids: 67 is the newest season, 64 an older one with a team of its own. */
export const eightSeasonFixtures = {
  esportsSeasons: eightSeasons,
  esportsPages: {
    "20": newest,
    "67": newest,
    "64": pageOf(4, 52, "BS Tempra"),
  },
}
