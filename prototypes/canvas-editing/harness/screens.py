"""Every screen the canvas shows live, grouped by the navbar tab it sits under."""
# (page id, page name, [(board stem, row label, route, viewer, steps)])
PAGES = [
    ("home", "Home", [
        ("Home", "Home", "/", "visitor", []),
    ]),
    ("membership", "Membership", [
        ("Membership", "Membership", "/membership", "visitor", []),
        ("MembershipSignUp", "Becoming a member", "/membership/signup", "visitor", []),
    ]),
    ("association", "Association", [
        ("AboutUs", "About us", "/aboutus", "visitor", []),
        ("Board", "Board", "/board", "board", []),
        ("Newsletters", "Newsletters", "/blogs", "visitor", []),
        ("Documents", "Documents", "/documents", "visitor", []),
    ]),
    ("committees", "Committees", [
        ("Committees", "All committees", "/committees", "board", []),
        ("Committee", "One committee", "/committees/nintenco", "board", []),
    ]),
    ("events", "Events", [
        ("Events", "Upcoming events", "/events", "board", []),
        ("Event", "One event", "/events/108206", "visitor", []),
        ("Past", "Past events", "/events/past", "visitor", []),
        ("CircuitShowdown", "Circuit Showdown", "/events/circuitShowdown", "visitor", []),
        ("NewEvent", "Add an event", "/events/create", "board", []),
        ("EditEvent", "Edit event", "/events/edit/108206", "board", []),
        ("SignUps", "Sign-ups", "/events/signups/108200", "board", []),
    ]),
    ("casual", "Casual", [
        ("Casual", "Casual", "/casual", "board", []),
        ("CasualGame", "One casual game", "/casual/valorant", "board", []),
    ]),
    ("competition", "Competition", [
        ("Competition", "Competition", "/competition", "board", []),
        ("CompetitionGame", "Competition game", "/competition/valorant", "board", []),
    ]),
    ("partners", "Partners", [
        ("Partners", "Become a partner", "/partners/become-a-partner", "visitor", []),
    ]),
    ("contact", "Contact", [
        ("Contact", "Contact", "/contact", "visitor", []),
    ]),
]

# Edit pages, added under their tab once the strands that design them are in.
EDITING = {
    "committees": [
        ("CommitteeEdit", "Edit a committee", "/committees/nintenco/edit", "board", []),
        ("CommitteeNew", "Add a committee", "/committees/new", "board", []),
    ],
    "association": [
        ("BoardEdit", "Edit a board", "/board/9/edit", "board", []),
        ("BoardNew", "Add a board", "/board/new", "board", []),
        ("BoardMemberEdit", "Edit a board member", "/board/9/members/91/edit", "board", []),
        ("BoardMemberNew", "Add a board member", "/board/9/members/new", "board", []),
    ],
    "casual": [
        ("GameEdit", "Edit a game (one page for casual and competition)", "/games/valorant/edit", "board", []),
        ("GameNew", "Add a game", "/games/new", "board", []),
    ],
    "competition": [
        ("GameEditFromCompetition", "Edit a game, from competition", "/competition/valorant/edit", "board", []),
        ("GameNewFromSeason", "Add a game, from a season", "/competition/new?season=20", "board", []),
        ("SeasonEdit", "Edit a season", "/competition/seasons/20/edit", "board", []),
        ("SeasonNew", "Add a season", "/competition/seasons/new", "board", []),
        ("TeamEdit", "Edit a team in a season", "/competition/valorant/teams/1/edit?season=20", "board", []),
        ("TeamNewExisting", "Add a team that played before", "/competition/valorant/teams/new?season=20", "board", []),
        ("TeamNewTeam", "Add a new team", "/competition/valorant/teams/new?season=20", "board", [{"click": "lineup-kind-new-team"}]),
    ],
}

VIEWS = [("", 1440, "dark", "desktop dark"), ("Phone", 390, "dark", "phone dark"),
         ("Light", 1440, "light", "desktop light"), ("PhoneLight", 390, "light", "phone light")]
