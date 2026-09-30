/**
 * Whole api records for the e2e stand-in, typed as they cross the wire. Each builder fills every
 * field the api always sends, so a fixture names only what its spec is about and still breaks the
 * typecheck when the api renames or drops a field.
 */
import type {
  AddressResponse,
  AnswerResponse,
  CasualGameResponse,
  BlogResponse,
  CommitteeResponse,
  ContributionPeriodResponse,
  ContributionResponse,
  Email,
  EventResponse,
  EventSignUpResponse,
  JobExecution,
  MembershipResponse,
  QuestionResponse,
  SeasonResponse,
  UserDetailResponse,
  UserSummaryResponse,
} from "@/services/api"

/** A record as it crosses the wire, where an enum is the string it is sent as. */
export type Wire<T> = T extends string ? `${T}` : T extends object ? {[K in keyof T]: Wire<T[K]>} : T

const stamped = {createdAt: "2025-01-01T00:00:00Z", updatedAt: "2025-01-01T00:00:00Z", version: 0}

export const aUser = (over: Partial<Wire<UserDetailResponse>> = {}): Wire<UserDetailResponse> => ({
  ...stamped,
  id: 1,
  username: "member",
  email: "member@example.com",
  firstName: "Member",
  initials: "M.",
  lastName: "Example",
  fullName: "Member Example",
  roles: ["MEMBER"],
  enabled: true,
  locked: false,
  awaitingReenrolment: false,
  twoFactorOn: false,
  nameOnRosters: false,
  newsletter: false,
  photoConsent: false,
  ...over,
})

export const aMembership = (over: Partial<Wire<MembershipResponse>> = {}): Wire<MembershipResponse> => ({
  ...stamped,
  id: 100,
  userId: 1,
  memberType: "REGULAR",
  incasso: false,
  incassoStanding: over.incasso ? "ON_INCASSO_WITHOUT_BANK_DETAILS" : "NONE",
  startDate: "2025-01-01",
  ...over,
})

export const aContributionPeriod = (
  over: Partial<Wire<ContributionPeriodResponse>> = {},
): Wire<ContributionPeriodResponse> => ({
  ...stamped,
  id: 200,
  startDate: "2025-01-01",
  endDate: "2025-06-30",
  halfYearCutoffDate: "2025-04-01",
  halfYearFee: 10,
  fullYearFee: 20,
  alumniFee: 5,
  ...over,
})

export const anAddress = (over: Partial<Wire<AddressResponse>> = {}): Wire<AddressResponse> => ({
  ...stamped,
  id: 400,
  userId: 1,
  street: "Main",
  houseNumber: "1",
  zipCode: "1234AB",
  city: "Enschede",
  country: "NL",
  ...over,
})

export const aContribution = (over: Partial<Wire<ContributionResponse>> = {}): Wire<ContributionResponse> => ({
  ...stamped,
  userId: 1,
  contributionPeriodId: 201,
  ...over,
})

export const anEvent = (over: Partial<Wire<EventResponse>> = {}): Wire<EventResponse> => ({
  ...stamped,
  id: 500,
  title: "Mock Event",
  startTime: "2099-01-01T12:00:00.000Z",
  endTime: "2099-01-01T14:00:00.000Z",
  approved: true,
  awaitingReapproval: false,
  membersOnly: false,
  signUp: false,
  signUpCount: 0,
  gameCodes: [],
  pingedRoles: [],
  ...over,
})

export const aSignUp = (over: Partial<Wire<EventSignUpResponse>> = {}): Wire<EventSignUpResponse> => ({
  ...stamped,
  id: 600,
  eventId: 500,
  kind: "MEMBER",
  answers: [],
  ...over,
})

export const aCommittee = (over: Partial<Wire<CommitteeResponse>> = {}): Wire<CommitteeResponse> => ({
  ...stamped,
  id: 900,
  name: "Events Committee",
  slug: "events-committee",
  description: "Runs things.",
  archived: false,
  gameCodes: [],
  ...over,
})

export const aBlog = (over: Partial<Wire<BlogResponse>> = {}): Wire<BlogResponse> => ({
  ...stamped,
  id: 1,
  title: "Mock Newsletter",
  html: "<h1>Mock Newsletter</h1>",
  url: "https://newsletter.example.com/1",
  publishedAt: "2025-01-01T12:00:00.000Z",
  ...over,
})

export const aJob = (over: Partial<Wire<JobExecution>> = {}): Wire<JobExecution> => ({
  id: 700,
  jobType: "SYNC_DISCORD",
  status: "QUEUED",
  attempts: 0,
  forced: false,
  foldedTriggers: [],
  relatedEntities: [],
  ...over,
})

export const anEmail = (over: Partial<Wire<Email>> = {}): Wire<Email> => ({
  id: 800,
  recipientEmail: "alice@example.com",
  subject: "Welcome to Blueshell",
  deliveryStatus: "DELIVERED",
  previewable: true,
  ...over,
})

/** A season the association played, unless the fixture says not. */
export const aSeason = (over: Partial<Wire<SeasonResponse>> & {id: number}): Wire<SeasonResponse> => ({
  name: `Season ${over.id}`,
  startDate: "2025-09-01",
  endDate: "2026-01-31",
  played: true,
  ...over,
})

/** A game the association plays in competition, which the esports pages read. */
export const anEsportsGame = (over: Partial<Wire<CasualGameResponse>> & {code: string}): Wire<CasualGameResponse> => ({
  name: over.code,
  slug: over.code.toLowerCase(),
  accent: null,
  intro: null,
  banner: null,
  icon: null,
  sortIndex: 0,
  archived: false,
  inCompetition: true,
  channels: [],
  esportsChannels: [],
  ...over,
})

export const aQuestion = (over: Partial<Wire<QuestionResponse>> = {}): Wire<QuestionResponse> => ({
  ...stamped,
  id: 10,
  idx: 0,
  surveyId: 1,
  type: "OPEN",
  label: "Comment",
  ...over,
})

export const anAnswer = (over: Partial<Wire<AnswerResponse>> = {}): Wire<AnswerResponse> => ({
  ...stamped,
  id: 1,
  questionId: 10,
  ...over,
})

export const aUserSummary = (over: Partial<Wire<UserSummaryResponse>> = {}): Wire<UserSummaryResponse> => ({
  ...stamped,
  id: 1,
  fullName: "Member Example",
  email: "member@example.com",
  ...over,
})
