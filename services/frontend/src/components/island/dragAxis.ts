import type {BandDirection} from "./BandSwipe.vue"

/**
 * The axis a drag runs along: which way a finger went, how far the band follows it, and whether
 * letting go finishes the journey or hands it back.
 *
 * Where the gesture's arithmetic meets the band that draws it, the way `stripAxis` holds the
 * strip's geometry and the board and season axes hold their domains' ordering. Everything here
 * is a number and a decision about numbers, which is what makes it the one part of the gesture
 * that can be proved by assertion: a touch synthesized in a browser at a chosen velocity is a
 * flake waiting to happen, and a threshold nobody can test is a threshold nobody can change.
 *
 * Named for the shape rather than for what is being travelled between, per the island's naming
 * rule: this is a drag along an axis, and it knows nothing about boards or seasons.
 */

/** How the gesture is measured, in one place, because these are the numbers being argued about. */
export const DRAG = {
  /**
   * How far a finger must go sideways before the band takes the gesture as its own.
   *
   * Under this it is a tap that wobbled, or the start of a scroll. It is also the distance that
   * decides whether the press that started the drag still counts as a press: a finger that
   * travelled this far was going somewhere, so the thing under it is not what was wanted.
   */
  slop: 10,
  /**
   * How fast a finger has to still be going, in pixels per millisecond, for its release to be
   * read as a flick.
   *
   * A flick is how most people actually swipe, and distance alone punishes it: a quick, short
   * throw of the thumb is as clear an intention as a slow haul and covers a third of the ground.
   */
  pace: 0.5,
  /** How far a slow drag has to get, as a share of the width it is measured against. */
  share: 0.25,
  /**
   * How much of a finger's travel the band follows where there is nothing that way.
   *
   * Not nothing, because a band that ignores the finger says only that the gesture is broken; and
   * not all of it, because a band that follows a finger to a stop that does not exist promises an
   * arrival it cannot make. A fraction of the travel is the answer both ways: the band moves, so
   * the gesture is alive, and it moves grudgingly, so the end of the line is felt rather than read.
   */
  lean: 1 / 3,
  /** And no further than this, in rem, however far the finger goes. */
  leanCap: 2.5,
} as const

/**
 * Which way a travel of [travel] pixels is going.
 *
 * The line runs oldest to newest from left to right, so a finger dragged rightwards pulls the
 * page back down it: what is on screen leaves by the right edge and the older stop comes in from
 * the left, which is exactly the pass a click on an earlier node plays. No travel at all is no
 * direction, the same answer the domains give for travelling from nowhere.
 */
export function directionOf(travel: number): BandDirection {
  if (travel > 0) return "past"
  if (travel < 0) return "future"
  return "same"
}

/**
 * How fast the finger was going over its last stretch, in pixels per millisecond, keeping the
 * sign so the direction of the flick is not lost.
 *
 * Two samples rather than the whole gesture, because what decides a flick is where the finger
 * was going when it left the glass, not the average of a drag that paused halfway. A stretch
 * with no time in it is no speed rather than an infinite one: browsers do coalesce two moves
 * onto the same timestamp.
 */
export function paceOf(travel: number, elapsed: number): number {
  if (elapsed <= 0) return 0
  return travel / elapsed
}

/** A finger leaving the glass: what it did, what it was measured against, what lies that way. */
export interface Release {
  /** How far it travelled altogether, positive rightwards. */
  travel: number
  /** How fast it was still going over its last stretch, signed the same way. */
  pace: number
  /** The width the distance is judged against, which is the width the band is drawn across. */
  width: number
  /** Whether there is a stop the way it went. */
  onward: boolean
}

/**
 * Whether letting go here finishes the journey.
 *
 * Either intention counts: still moving, or already far enough. A flick and a haul are both
 * somebody asking for the next stop, and requiring both would mean asking for it twice.
 *
 * The flick has to be going the same way as the drag. A finger that pushed the band across and
 * then pulled it back before lifting has changed its mind in the plainest way there is, and
 * reading the speed unsigned would take that retreat for an arrival.
 *
 * A release at the end of the line never commits, whatever it did: there is nowhere to commit to,
 * which is what the lean above has been saying all along.
 */
export function commits({travel, pace, width, onward}: Release): boolean {
  if (!onward || travel === 0) return false
  const flicked = Math.sign(pace) === Math.sign(travel) && Math.abs(pace) >= DRAG.pace
  return flicked || Math.abs(travel) >= width * DRAG.share
}

/** A finger on the glass: where it has got to, and what it is dragging towards. */
export interface Reach {
  travel: number
  width: number
  /** Whether there is a stop the way it is going. */
  onward: boolean
  /** How far the band may lean where there is not, in pixels. */
  cap: number
}

/**
 * How far the band actually stands from home, which is not always how far the finger has gone.
 *
 * Where there is a stop that way the band follows the finger exactly, because that is the whole
 * of direct manipulation, and no further than a full width, because a full width is the arrival:
 * past it the neighbour would be dragged off the far side of the screen.
 *
 * Where there is not, it leans and no more.
 */
export function follow({travel, width, onward, cap}: Reach): number {
  if (!onward) return Math.sign(travel) * Math.min(Math.abs(travel) * DRAG.lean, cap)
  return Math.sign(travel) * Math.min(Math.abs(travel), width)
}
