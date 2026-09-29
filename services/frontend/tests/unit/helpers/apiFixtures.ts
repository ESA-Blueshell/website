/**
 * Whole api records for unit tests, typed from the generated SDK. Each builder fills every field
 * the api always sends, so a test names only what it is about and the rest still type-checks.
 */
import type {
  AddressResponse,
  AnswerResponse,
  AssociationStatisticsResponse,
  BlogResponse,
  CasualGameResponse,
  CommitteePageResponse,
  CommitteeResponse,
  ContributionPeriodResponse,
  EventBannerResponse,
  EventResponse,
  EventSignUpResponse,
  GuestResponse,
  Image,
  JobExecution,
  MemberProfileResponse,
  MembershipResponse,
  QuestionResponse,
  RosterEntryResponse,
  SeasonResponse,
  SurveyResponse,
  TeamRosterResponse,
  TeamResponse,
  UserDetailResponse,
} from "@/services/api"
import {EventSignUpKind, JobExecutionStatus, MemberType, QuestionType, Role, TeamRole} from "@/services/api"

const stamped = {createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z", version: 0}

export const anImage = (over: Partial<Image> = {}): Image => ({
  url: "/files/poster.webp",
  path: "files/poster.webp",
  width: 1600,
  height: 900,
  renditions: [{url: "/files/poster-800.webp", width: 800}],
  ...over,
})

export const aBanner = (over: Partial<EventBannerResponse> = {}): EventBannerResponse => ({
  ...stamped,
  eventId: 7,
  fileId: 70,
  image: anImage(),
  ...over,
})

export const anEvent = (over: Partial<EventResponse> = {}): EventResponse => ({
  ...stamped,
  id: 7,
  title: "Ye Olde Quest for the Eleven Ales",
  startTime: "2026-10-03T12:00:00Z",
  endTime: "2026-10-03T21:59:00Z",
  location: "Witbreuksweg 401B",
  description: "Hear ye, hear ye",
  membersOnly: true,
  approved: true,
  awaitingReapproval: false,
  gameCodes: [],
  pingedRoles: [],
  signUp: false,
  signUpCount: 0,
  banner: aBanner(),
  ...over,
})

export const aContributionPeriod = (over: Partial<ContributionPeriodResponse> = {}): ContributionPeriodResponse => ({
  ...stamped,
  id: 4,
  startDate: "2026-09-01",
  endDate: "2027-08-31",
  halfYearCutoffDate: "2027-02-01",
  fullYearFee: 20,
  halfYearFee: 12,
  alumniFee: 10,
  ...over,
})

export const associationNumbers = (over: Partial<AssociationStatisticsResponse> = {}): AssociationStatisticsResponse => ({
  boards: 12,
  committees: 9,
  eventsLastYear: 48,
  gamesPlayed: 14,
  seasonsPlayed: 20,
  teamsThisSeason: 6,
  ...over,
})

export const aBlog = (over: Partial<BlogResponse> = {}): BlogResponse => ({
  ...stamped,
  id: 7,
  title: "January update",
  html: "<h1>Blog</h1>",
  url: "https://newsletter.test/7",
  publishedAt: "2026-01-15T12:00:00Z",
  ...over,
})

export const aMembership = (over: Partial<MembershipResponse> = {}): MembershipResponse => ({
  ...stamped,
  id: 1,
  userId: 42,
  memberType: MemberType.REGULAR,
  incasso: false,
  startDate: "2026-09-01",
  ...over,
})

export const anAddress = (over: Partial<AddressResponse> = {}): AddressResponse => ({
  ...stamped,
  id: 3,
  userId: 1,
  street: "Hengelosestraat",
  houseNumber: "1",
  zipCode: "7521 AA",
  city: "Enschede",
  country: "NL",
  ...over,
})

export const aUser = (over: Partial<UserDetailResponse> = {}): UserDetailResponse => ({
  ...stamped,
  id: 7,
  username: "roos",
  email: "roos@esa.test",
  firstName: "Roos",
  initials: "R.",
  lastName: "Kruk",
  fullName: "Roos Kruk",
  roles: [Role.MEMBER],
  enabled: true,
  locked: false,
  awaitingReenrolment: false,
  twoFactorOn: false,
  nameOnRosters: true,
  newsletter: false,
  photoConsent: false,
  ...over,
})

export const aMemberProfile = (over: Partial<MemberProfileResponse> = {}): MemberProfileResponse => ({
  ...stamped,
  id: 1,
  userId: 42,
  bhv: false,
  ehbo: false,
  nameOnRosters: true,
  ...over,
})

export const aSignUp = (over: Partial<EventSignUpResponse> = {}): EventSignUpResponse => ({
  ...stamped,
  id: 5,
  eventId: 500,
  kind: EventSignUpKind.MEMBER,
  answers: [],
  ...over,
})

export const aQuestion = (over: Partial<QuestionResponse> = {}): QuestionResponse => ({
  ...stamped,
  id: 3,
  idx: 0,
  surveyId: 1,
  type: QuestionType.OPEN,
  label: "Comment",
  ...over,
})

export const anAnswer = (over: Partial<AnswerResponse> = {}): AnswerResponse => ({
  ...stamped,
  id: 1,
  questionId: 3,
  ...over,
})

export const aGuest = (over: Partial<GuestResponse> = {}): GuestResponse => ({
  ...stamped,
  id: 60,
  name: "Bob",
  email: "b@x",
  discord: "",
  ...over,
})

export const aSurvey = (over: Partial<SurveyResponse> = {}): SurveyResponse => ({
  ...stamped,
  id: 1,
  questions: [],
  responseCount: 0,
  ...over,
})

export const aCommittee = (over: Partial<CommitteeResponse> = {}): CommitteeResponse => ({
  ...stamped,
  id: 1,
  name: "LanCie",
  slug: "lancie",
  description: "LANs",
  listed: true,
  archived: false,
  gameCodes: [],
  ...over,
})

export const aCommitteePage = (over: Partial<CommitteePageResponse> = {}): CommitteePageResponse => ({
  id: 1,
  name: "LanCie",
  slug: "lancie",
  description: "LANs",
  listed: true,
  archived: false,
  gameCodes: [],
  members: [],
  ...over,
})

export const aCasualGame = (over: Partial<CasualGameResponse> = {}): CasualGameResponse => ({
  code: "CS2",
  name: "Counter-Strike 2",
  slug: "cs2",
  sortIndex: 0,
  archived: false,
  inCompetition: false,
  channels: [],
  esportsChannels: [],
  ...over,
})

/** A game the association plays in competition, as the esports pages read it. */
export const aGame = (over: Partial<CasualGameResponse> = {}): CasualGameResponse =>
  aCasualGame({code: "VALORANT", name: "Valorant", slug: "valorant", inCompetition: true, ...over})

export const aSeason = (over: Partial<SeasonResponse> = {}): SeasonResponse => ({
  id: 19,
  name: "Season 19",
  startDate: "2026-09-01",
  endDate: "2027-01-31",
  played: false,
  ...over,
})

export const aTeam = (over: Partial<TeamResponse> = {}): TeamResponse => ({
  id: 1,
  name: "BS Main",
  ...over,
})

export const aRosterEntry = (over: Partial<RosterEntryResponse> = {}): RosterEntryResponse => ({
  id: 1,
  teamId: 1,
  seasonId: 19,
  handle: "ace",
  role: TeamRole.PLAYER,
  sortIndex: 0,
  ...over,
})

export const aTeamRoster = (over: Partial<TeamRosterResponse> = {}): TeamRosterResponse => ({
  id: 1,
  name: "BS Waterboarders",
  members: [],
  ...over,
})

export const aJob = (over: Partial<JobExecution> = {}): JobExecution => ({
  id: 88,
  jobType: "SYNC_CONTACT",
  status: JobExecutionStatus.QUEUED,
  attempts: 0,
  forced: false,
  foldedTriggers: [],
  relatedEntities: [],
  ...over,
})
