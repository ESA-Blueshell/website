// Draws the campus around the Esports Lounge Twente for the contact page, as two SVGs (dark and
// light) in src/assets/contact. The data is OpenStreetMap's (ODbL; the page credits it), read
// once from its API here, so the page itself asks nobody else for anything.
//
// Run it again when the campus changes: node scripts/campusMap.mjs
// The middle and the reach match CAMPUS in src/domains/association/campusMap.ts.
import {writeFileSync} from 'node:fs'

const LOUNGE = {lat: 52.243214, lon: 6.851979}
const REACH = 700
const M_PER_DEG_LAT = 111_320
const M_PER_DEG_LON = 111_320 * Math.cos(LOUNGE.lat * Math.PI / 180)
const OUT = new URL('../src/assets/contact/', import.meta.url)

const xy = (lat, lon) => [(lon - LOUNGE.lon) * M_PER_DEG_LON, (LOUNGE.lat - lat) * M_PER_DEG_LAT]

// Douglas-Peucker on an open line.
const simplify = (points, tolerance) => {
    if (points.length < 3) return points
    const [[x1, y1], [x2, y2]] = [points[0], points.at(-1)]
    const [dx, dy] = [x2 - x1, y2 - y1]
    const norm = Math.hypot(dx, dy) || 1e-9
    let [far, index] = [0, 0]
    for (let i = 1; i < points.length - 1; i++) {
        const [x, y] = points[i]
        const d = Math.abs(dy * x - dx * y + x2 * y1 - y2 * x1) / norm
        if (d > far) [far, index] = [d, i]
    }
    if (far <= tolerance) return [points[0], points.at(-1)]
    return [...simplify(points.slice(0, index + 1), tolerance).slice(0, -1), ...simplify(points.slice(index), tolerance)]
}

// A closed ring has no chord to measure from, so it is halved at its farthest point first.
const simplifyRing = (points, tolerance) => {
    if (points.length < 4) return points
    let far = 0
    points.forEach((p, i) => { if (Math.hypot(p[0] - points[0][0], p[1] - points[0][1]) > Math.hypot(points[far][0] - points[0][0], points[far][1] - points[0][1])) far = i })
    return [...simplify(points.slice(0, far + 1), tolerance).slice(0, -1), ...simplify(points.slice(far), tolerance)]
}

const near = points => points.some(([x, y]) => Math.abs(x) < REACH * 1.15 && Math.abs(y) < REACH * 1.15)
const path = (points, closed) => points.length < 2 ? '' :
    `M${points.map(([x, y]) => `${Math.round(x)} ${Math.round(y)}`).join('L')}${closed ? 'Z' : ''}`
const escape = text => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
const length = points => points.slice(1).reduce((sum, p, i) => sum + Math.hypot(p[0] - points[i][0], p[1] - points[i][1]), 0)

// Joins open member ways of a multipolygon into rings, as far as their ends meet.
const joinRings = segments => {
    const same = (a, b) => a[0] === b[0] && a[1] === b[1]
    const rings = []
    const pending = segments.filter(s => s.length).map(s => [...s])
    while (pending.length) {
        let ring = pending.pop()
        let grew = true
        while (!same(ring[0], ring.at(-1)) && grew) {
            grew = false
            for (let i = 0; i < pending.length; i++) {
                const other = pending[i]
                if (same(other[0], ring.at(-1))) ring = [...ring, ...other.slice(1)]
                else if (same(other.at(-1), ring.at(-1))) ring = [...ring, ...other.slice(0, -1).reverse()]
                else if (same(other.at(-1), ring[0])) ring = [...other.slice(0, -1), ...ring]
                else if (same(other[0], ring[0])) ring = [...other.slice(1).reverse(), ...ring]
                else continue
                pending.splice(i, 1)
                grew = true
                break
            }
        }
        rings.push(ring)
    }
    return rings
}

const AREAS = [
    ['water', t => t.natural === 'water' || t.water || t.waterway === 'riverbank' || ['basin', 'reservoir'].includes(t.landuse)],
    ['wood', t => t.natural === 'wood' || t.landuse === 'forest'],
    ['pitch', t => ['pitch', 'track', 'sports_centre'].includes(t.leisure) && !t.building],
    ['green', t => ['grass', 'meadow', 'recreation_ground', 'village_green', 'farmland'].includes(t.landuse)
        || ['park', 'garden'].includes(t.leisure) || ['grassland', 'scrub', 'heath'].includes(t.natural)],
    ['parking', t => t.amenity === 'parking' && !t.building],
]
const areaOf = tags => AREAS.find(([, test]) => test(tags))?.[0]

const ROADS = {
    primary: 'major', secondary: 'major', tertiary: 'major', primary_link: 'major', secondary_link: 'major',
    tertiary_link: 'major', residential: 'minor', unclassified: 'minor', living_street: 'minor', service: 'service',
    footway: 'path', path: 'path', pedestrian: 'path', steps: 'path', cycleway: 'cycle',
}

const PAINT = {
    dark: {
        land: 'fill="#1d1f22"', green: 'fill="#1f2721"', wood: 'fill="#1b241d"', pitch: 'fill="#222c25"',
        parking: 'fill="#25282c"', water: 'fill="#16283f"',
        building: 'fill="#2b2e33" stroke="#383c42" stroke-width="0.8"',
        bastille: 'fill="#3387fa" fill-opacity="0.32" stroke="#3387fa" stroke-width="2.4"',
        major: 'stroke="#4a5058" stroke-width="9"', minor: 'stroke="#3b4047" stroke-width="6"',
        service: 'stroke="#33373d" stroke-width="3.5"', path: 'stroke="#3d4249" stroke-width="1.6" stroke-dasharray="4 3"',
        cycle: 'stroke="#2c4d78" stroke-width="2.2"',
        roadName: 'fill="#a0a6ac" stroke="#1d1f22"', place: 'fill="#858b91" stroke="#1d1f22"',
    },
    light: {
        land: 'fill="#e8edf2"', green: 'fill="#d5e3d3"', wood: 'fill="#c6dac4"', pitch: 'fill="#cfe1ce"',
        parking: 'fill="#dde2e7"', water: 'fill="#a9c9ef"',
        building: 'fill="#cdd6df" stroke="#b4bfca" stroke-width="0.8"',
        bastille: 'fill="#3387fa" fill-opacity="0.3" stroke="#1b56b8" stroke-width="2.4"',
        major: 'stroke="#ffffff" stroke-width="9"', minor: 'stroke="#fbfdff" stroke-width="6"',
        service: 'stroke="#f2f5f8" stroke-width="3.5"', path: 'stroke="#97a3af" stroke-width="1.4" stroke-dasharray="4 3"',
        cycle: 'stroke="#7fa7da" stroke-width="2"',
        roadName: 'fill="#3f4750" stroke="#e8edf2"', place: 'fill="#5b6570" stroke="#e8edf2"',
    },
}

const span = REACH * 1.2 / M_PER_DEG_LAT
const bbox = [LOUNGE.lon - span * 1.65, LOUNGE.lat - span, LOUNGE.lon + span * 1.65, LOUNGE.lat + span]
const response = await fetch(`https://api.openstreetmap.org/api/0.6/map.json?bbox=${bbox.map(n => n.toFixed(5)).join(',')}`,
    {headers: {'User-Agent': 'blueshell-website campus map (board@blueshell.utwente.nl)'}})
if (!response.ok) throw new Error(`OpenStreetMap answered ${response.status}`)
const {elements} = await response.json()

const nodes = new Map(elements.filter(e => e.type === 'node').map(e => [e.id, xy(e.lat, e.lon)]))
const ways = new Map(elements.filter(e => e.type === 'way').map(e => [e.id, e]))
const pointsOf = way => way.nodes.filter(n => nodes.has(n)).map(n => nodes.get(n))

const areas = Object.fromEntries(AREAS.map(([kind]) => [kind, []]))
const roads = Object.fromEntries(Object.values(ROADS).map(kind => [kind, []]))
const buildings = []
const places = []
const roadNames = new Map()
let bastille = ''

for (const way of ways.values()) {
    const tags = way.tags ?? {}
    const points = pointsOf(way)
    if (points.length < 2 || !near(points)) continue
    const closed = way.nodes[0] === way.nodes.at(-1)
    if (tags.building && closed) {
        const d = path(simplifyRing(points, 0.6), true)
        if (tags.name === 'Bastille') bastille = d
        else buildings.push(d)
        if (tags.name && points.length > 6) {
            const xs = points.map(p => p[0])
            places.push({
                name: tags.name,
                width: Math.max(...xs) - Math.min(...xs),
                x: xs.reduce((a, b) => a + b) / xs.length,
                y: points.reduce((a, p) => a + p[1], 0) / points.length,
            })
        }
        continue
    }
    const area = areaOf(tags)
    if (area && closed) {
        areas[area].push(path(simplifyRing(points, 1), true))
        continue
    }
    const road = ROADS[tags.highway]
    if (road && tags.area !== 'yes') {
        roads[road].push(path(simplify(points, 0.8), false))
        if (tags.name && (road === 'major' || road === 'minor')) {
            const best = roadNames.get(tags.name)
            if (!best || length(points) > length(best)) roadNames.set(tags.name, points)
        }
    }
}

for (const relation of elements.filter(e => e.type === 'relation' && e.tags?.type === 'multipolygon')) {
    const kind = relation.tags.building ? 'building' : areaOf(relation.tags)
    if (!kind) continue
    const outers = relation.members
        .filter(m => m.type === 'way' && m.role === 'outer' && ways.has(m.ref))
        .map(m => pointsOf(ways.get(m.ref)))
    for (const ring of joinRings(outers)) {
        const closed = ring.length > 2 && ring[0][0] === ring.at(-1)[0] && ring[0][1] === ring.at(-1)[1]
        if (closed && near(ring)) (kind === 'building' ? buildings : areas[kind]).push(path(simplifyRing(ring, 1), true))
    }
}

// Road names follow their longest stretch, written left to right.
const named = [...roadNames].sort(([, a], [, b]) => length(b) - length(a)).slice(0, 16)
    .filter(([, points]) => length(points) >= 150)
    .map(([name, points], i) => {
        let line = simplify(points, 6)
        if (line[0][0] > line.at(-1)[0]) line = line.reverse()
        return {id: `road-${i}`, name, d: path(line, false)}
    })

// The larger named buildings, away from the Lounge's own card.
const shown = new Set()
const labelled = places.sort((a, b) => b.width - a.width).slice(0, 16).filter(place => {
    if (shown.has(place.name) || place.name === 'Bastille' || Math.hypot(place.x, place.y) < 90 || place.width < 45) return false
    shown.add(place.name)
    return true
})

const draw = paint => [
    `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="${-REACH} ${-REACH} ${2 * REACH} ${2 * REACH}"`
    + ` width="${2 * REACH}" height="${2 * REACH}" font-family="Barlow Semi Condensed, Barlow, Helvetica Neue, Arial, sans-serif"`
    + ' stroke-linecap="round" stroke-linejoin="round">',
    `<defs>${named.map(road => `<path id="${road.id}" d="${road.d}"/>`).join('')}</defs>`,
    `<rect x="${-2 * REACH}" y="${-2 * REACH}" width="${4 * REACH}" height="${4 * REACH}" ${paint.land}/>`,
    ...['green', 'wood', 'pitch', 'parking', 'water'].filter(kind => areas[kind].length)
        .map(kind => `<path ${paint[kind]} d="${areas[kind].join(' ')}"/>`),
    ...['service', 'path', 'cycle', 'minor', 'major'].filter(kind => roads[kind].length)
        .map(kind => `<path fill="none" ${paint[kind]} d="${roads[kind].join(' ')}"/>`),
    `<path ${paint.building} d="${buildings.join(' ')}"/>`,
    bastille && `<path ${paint.bastille} d="${bastille}"/>`,
    ...named.map(road => `<text font-size="11" font-weight="600" letter-spacing="0.9" stroke-width="3" paint-order="stroke" ${paint.roadName}>`
        + `<textPath xlink:href="#${road.id}" startOffset="50%" text-anchor="middle">${escape(road.name)}</textPath></text>`),
    ...labelled.map(place => `<text x="${Math.round(place.x)}" y="${Math.round(place.y)}" text-anchor="middle" font-size="10.5"`
        + ` font-weight="500" stroke-width="3" paint-order="stroke" ${paint.place}>${escape(place.name)}</text>`),
    '</svg>',
].filter(Boolean).join('\n')

for (const [theme, paint] of Object.entries(PAINT)) {
    const file = new URL(`campus-${theme}.svg`, OUT)
    const svg = draw(paint)
    writeFileSync(file, `${svg}\n`)
    console.log(`${file.pathname}: ${Math.round(svg.length / 1024)} KiB`)
}
