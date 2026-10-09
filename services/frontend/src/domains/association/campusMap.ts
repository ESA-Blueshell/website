/**
 * The campus map on the contact page: where things are on the drawing, and how far it pans and
 * zooms.
 *
 * The drawing is a square [CAMPUS.reach] metres either side of the Lounge, made from
 * OpenStreetMap data by scripts/campusMap.mjs and served with the site, so showing it asks
 * nobody else for anything. On the page the square is twice the plate's width across at zoom 1.
 */

export interface Place {
  lat: number
  lon: number
}

export const CAMPUS = {
  /** The Bastille's middle: the Lounge is inside it. The drawing is centred here. */
  lounge: {lat: 52.243214, lon: 6.851979},
  /** Bus stop UT/Bastille, the one towards the Bastille. */
  busStop: {lat: 52.2425426, lon: 6.8502068},
  /** Metres from the Lounge to each edge of the drawing. scripts/campusMap.mjs draws the same. */
  reach: 700,
} as const

const METRES_PER_DEGREE = 111_320

/** Where a place sits on the drawing, in percent from its top left. */
export function spotOf(place: Place): {left: number, top: number} {
  const {lounge, reach} = CAMPUS
  const east = (place.lon - lounge.lon) * METRES_PER_DEGREE * Math.cos(lounge.lat * Math.PI / 180)
  const south = (lounge.lat - place.lat) * METRES_PER_DEGREE
  const percent = (metres: number) => Math.round((50 + metres / (2 * reach) * 100) * 100) / 100
  return {left: percent(east), top: percent(south)}
}

/** A way to the Lounge in whatever maps app the visitor has, and one that needs none. */
export const LOUNGE_IN_MAPS_APP =
  `geo:${CAMPUS.lounge.lat},${CAMPUS.lounge.lon}?q=${CAMPUS.lounge.lat},${CAMPUS.lounge.lon}(Esports%20Lounge%20Twente)`
/** The Lounge marked on openstreetmap.org, for a visitor without a maps app to hand. */
export const LOUNGE_ON_MAP =
  `https://www.openstreetmap.org/?mlat=${CAMPUS.lounge.lat}&mlon=${CAMPUS.lounge.lon}#map=18/${CAMPUS.lounge.lat}/${CAMPUS.lounge.lon}`
export const LOUNGE_ROUTE =
  `https://www.openstreetmap.org/directions?route=%3B${CAMPUS.lounge.lat}%2C${CAMPUS.lounge.lon}`

/** How the drawing sits on the plate: its zoom, and how far its middle is moved, in pixels. */
export interface MapView {
  zoom: number
  x: number
  y: number
}

export const HOME: MapView = {zoom: 1, x: 0, y: 0}
/** A phone's plate is narrow enough that the names would be too small to read at zoom 1. */
export const PHONE_HOME: MapView = {zoom: 1.8, x: 0, y: 0}
export const MIN_ZOOM = 0.6
export const MAX_ZOOM = 3

/** Zoomed by [factor] about the plate's middle, so what was in the middle stays there. */
export function zoomed(view: MapView, factor: number): MapView {
  const zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, view.zoom * factor))
  const ratio = zoom / view.zoom
  return {zoom, x: view.x * ratio, y: view.y * ratio}
}

/** Moved by [by] pixels, kept from leaving the plate empty at any edge. */
export function panned(view: MapView, by: {x: number, y: number}, plate: {width: number, height: number}): MapView {
  const side = 2 * plate.width * view.zoom
  const clamp = (value: number, room: number) => Math.min(room, Math.max(-room, value))
  return {
    zoom: view.zoom,
    x: clamp(view.x + by.x, Math.max(0, (side - plate.width) / 2)),
    y: clamp(view.y + by.y, Math.max(0, (side - plate.height) / 2)),
  }
}

/**
 * Zoomed by [factor] about [at], a point given in pixels from the plate's middle, so what is
 * under it stays there; then kept from leaving the plate empty, as a drag is.
 */
export function zoomedAt(view: MapView, factor: number, at: {x: number, y: number}, plate: {width: number, height: number}): MapView {
  const next = zoomed(view, factor)
  const ratio = next.zoom / view.zoom
  const moved = {zoom: next.zoom, x: at.x - (at.x - view.x) * ratio, y: at.y - (at.y - view.y) * ratio}
  return panned(moved, {x: 0, y: 0}, plate)
}
