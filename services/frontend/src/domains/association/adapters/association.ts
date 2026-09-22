/**
 * Association domain adapter: the only file in this domain that imports from @/services/api
 * (per frontend ADR-002). Everything else imports from here.
 */
import {apiUrl, associationStatistics, findCurrentContributionPeriod, findEventById, findEvents} from "@/services/api"
import type {AssociationStatisticsResponse, ContributionPeriodResponse} from "@/services/api"
import type {Picture} from "@/components/island/pictures"

/** What the association can say about itself in numbers. */
export type AssociationNumbers = AssociationStatisticsResponse

/** A year's contribution, as the association records it. */
export type ContributionPeriod = ContributionPeriodResponse

/**
 * The association's own numbers, or nothing where the api would not say.
 *
 * Nothing rather than a throw, and nothing rather than zeroes: the page these feed shows honest
 * floors until real figures land, and a zero would read as a fact. The generated client resolves
 * on 4xx and 5xx with an `error` instead of throwing, so the error is checked rather than caught.
 */
export async function loadAssociationNumbers(): Promise<AssociationNumbers | null> {
  const res = await associationStatistics()
  if (res.error || !res.data) return null
  return res.data
}

/**
 * The contribution period the association is charging for, or nothing where none is recorded.
 *
 * The same read the signup form's fee component makes, so both pages quote one source. An
 * empty answer is a period nobody has written down yet, which is a fact the page can state.
 */
export async function loadCurrentContributionPeriod(): Promise<ContributionPeriod | null> {
  const res = await findCurrentContributionPeriod()
  if (res.error || !res.data) return null
  return res.data
}

/** One event worth showing off, reduced to what a band draws. */
export interface EventOnShow {
  id: number
  title: string
  startTime: string
  endTime?: string
  location?: string
  description?: string
  membersOnly: boolean
  /** The poster somebody made for it, where one was made. */
  banner?: Picture
}

/** One event by its own id, for a link that names an event the strip has not read yet. */
export async function loadEventOnShow(id: number): Promise<EventOnShow | undefined> {
  const answered = await findEventById({path: {id}})
  const one = answered.data
  if (!one?.id) return undefined
  const art = one.banner?.image
  return {
    id: one.id,
    title: one.title,
    startTime: one.startTime,
    endTime: one.endTime,
    location: one.location ?? undefined,
    description: one.description ?? undefined,
    membersOnly: one.membersOnly,
    banner: art?.url === undefined ? undefined : {
      url: apiUrl(art.url),
      path: art.path ?? "",
      width: art.width ?? undefined,
      height: art.height ?? undefined,
      renditions: (art.renditions ?? []).map(copy => ({url: apiUrl(copy.url), width: copy.width})),
    },
  }
}

/**
 * Recent events that have a banner, newest first.
 *
 * The api answers only the events the caller may see, so nothing here filters for that.
 * `hasBanner` is asked of the api rather than of the answer: without it a page would have to
 * over-fetch and throw most of it away to find [wanted] with art. An event whose banner record
 * has lost its file is passed over - there is nothing to draw for it.
 */
export async function loadEventsOnShow(wanted: number, page = 0): Promise<EventOnShow[]> {
  const answered = await findEvents({
    query: {
      approved: true,
      // Only the ones somebody made a poster for: a band that sells the association on its
      // art reads worse with a plate in the middle of it than with fewer events.
      hasBanner: true,
      to: new Date().toISOString(),
      page,
      size: wanted,
      sort: ["startTime,desc"],
    },
  })

  return (answered.data?.content ?? []).flatMap(one => {
    if (one.id === undefined) return []
    const art = one.banner?.image
    return [{
      id: one.id,
      title: one.title,
      startTime: one.startTime,
      endTime: one.endTime,
      location: one.location ?? undefined,
      description: one.description ?? undefined,
      membersOnly: one.membersOnly,
      banner: art?.url === undefined ? undefined : {
        url: apiUrl(art.url),
        path: art.path ?? "",
        width: art.width ?? undefined,
        height: art.height ?? undefined,
        renditions: (art.renditions ?? []).map(copy => ({url: apiUrl(copy.url), width: copy.width})),
      },
    }]
  })
}
