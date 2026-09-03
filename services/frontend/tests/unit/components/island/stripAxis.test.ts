import {describe, expect, it} from "vitest"
import {bands, litAt, STRIP, stripAxis, type Stop} from "@/components/island/stripAxis"

/**
 * The stops arrive in the order they are to be read: what makes one older than another is
 * knowledge about what a stop stands for, and the arithmetic here has none.
 */
const stop = (id: number, label: string, sublabel = ""): Stop =>
  ({id, name: sublabel ? `${label} ${sublabel}` : label, label, sublabel})

describe("bands", () => {
  it("reads the stops left to right in the order it was given them", () => {
    const laid = bands([stop(1, "Autumn", "2025"), stop(2, "Spring", "2026")])

    expect(laid.map(b => b.stop.id)).toEqual([1, 2])
  })

  it("gives every stop an equal share of the width", () => {
    const laid = bands([
      stop(1, "Autumn", "2024"),
      stop(2, "Spring", "2025"),
      stop(3, "Autumn", "2025"),
      stop(4, "Spring", "2026"),
    ])

    expect(laid.map(b => b.from)).toEqual([0, 0.25, 0.5, 0.75])
    expect(laid.map(b => b.to)).toEqual([0.25, 0.5, 0.75, 1])
  })

  it("sits a node in the middle of its own stop, not on its edge", () => {
    const laid = bands([stop(1, "Autumn", "2024"), stop(2, "Spring", "2025")])

    expect(laid.map(b => b.at)).toEqual([0.25, 0.75])
    // Which puts the division between them exactly halfway between the two nodes.
    expect(laid[0]!.to).toBe((laid[0]!.at + laid[1]!.at) / 2)
  })

  it("fills the whole strip with a single stop", () => {
    const laid = bands([stop(1, "Autumn", "2025")])

    expect(laid[0]!.from).toBe(0)
    expect(laid[0]!.to).toBe(1)
    expect(laid[0]!.at).toBe(0.5)
  })

  it("alternates which side of the middle a node sits, so the line has to bend", () => {
    const laid = bands([
      stop(1, "Autumn", "2024"),
      stop(2, "Spring", "2025"),
      stop(3, "Autumn", "2025"),
    ])

    expect(laid.map(b => b.high)).toEqual([true, false, true])
  })
})

describe("stripAxis", () => {
  const four = [
    stop(1, "Autumn", "2024"),
    stop(2, "Spring", "2025"),
    stop(3, "Autumn", "2025"),
    stop(4, "Spring", "2026"),
  ]

  it("fills the strip's width when the stops are few", () => {
    const strip = stripAxis(four, {width: 1200, trailing: 0})

    expect(strip.track).toBe(1200)
  })

  it("grows past the strip once a band would be narrower than it can be read at", () => {
    const many = Array.from({length: 12}, (_, i) => stop(i + 1, "Autumn", `${2010 + i}`))
    const strip = stripAxis(many, {width: 1200, trailing: 0})

    // Twelve bands at the width six of them would have: the strip scrolls rather than
    // shrinking a band to a sliver.
    expect(strip.track).toBe(2400)
  })

  /*
   * A band's floor is the reader's rather than the strip's.
   *
   * `minBand` is a figure for a strip with a pointer over it: a node wants to be hittable and
   * its two labels want to fit. A thumb and a phone want a great deal more of both, and at the
   * shared figure a 390px screen fitted four bands, so every label was clipped at one end of the
   * window or the other and the outermost sat under the arrows that pan the strip.
   *
   * The floors themselves are the strip's own business and are read off it. What is asserted is
   * which of the two a given width gets, and that the phone's is the wider — the day either
   * figure is retuned is not a day this should have an opinion.
   */
  it("holds a phone's bands to a floor of their own, wider than the pointer's", () => {
    const strip = stripAxis(four, {width: 390, trailing: 0})

    expect(STRIP.minBandStacked).toBeGreaterThan(STRIP.minBand)
    // Four bands at the phone's floor, so the strip scrolls and a reader gets one stop and a
    // little of its neighbours rather than four slivers.
    expect(strip.track).toBe(4 * STRIP.minBandStacked)
    expect(strip.track).toBeGreaterThan(390)
  })

  /*
   * And it lets go of that floor at the one width that decides it, which is the width the bands
   * stack at: what a strip is being read on is how much room it has, not what kind of device it
   * is, and a phone turned on its side is a strip with a pointer's worth of room.
   *
   * Above the line the floor stops being the thing that decides, because six bands sharing 768px
   * are 128 wide and that is already past the pointer's own floor. So what is asserted there is
   * the share, which is what the strip falls back to on any width a reader has room on.
   */
  it("lets go of the phone's floor at the width the bands stop stacking at", () => {
    const many = Array.from({length: 12}, (_, i) => stop(i + 1, "Autumn", `${2010 + i}`))

    const phone = stripAxis(many, {width: STRIP.stacks - 1, trailing: 0})
    const desktop = stripAxis(many, {width: STRIP.stacks, trailing: 0})

    expect(phone.track).toBe(12 * STRIP.minBandStacked)
    expect(desktop.track).toBe(12 * (STRIP.stacks / STRIP.tiles))
    expect(desktop.track).toBeLessThan(phone.track)
  })

  /*
   * And an unmeasured strip is not a phone.
   *
   * A width of nothing is the strip before it has been laid out rather than a narrow one, and
   * read as narrow it would reserve the phone's floor for every band and lay a track four times
   * the width the strip turns out to have.
   */
  it("reserves nothing for a phone before the strip has been measured", () => {
    const strip = stripAxis(four, {width: 0, trailing: 0})

    expect(strip.track).toBe(4 * STRIP.minBand)
  })

  it("sits each node in the middle of its own band", () => {
    const strip = stripAxis(four, {width: 1200, trailing: 0})

    expect(strip.nodes.map(n => n.x)).toEqual([150, 450, 750, 1050])
  })

  it("keeps a node in the middle of its own band when a band is reserved on the end", () => {
    const strip = stripAxis(four, {width: 1000, trailing: 1})

    // Five shares of 200: the four stops take the first four, and their nodes sit in the
    // middle of those rather than being squeezed by the share the plus takes.
    strip.nodes.forEach((node, index) => expect(node.x).toBeCloseTo(100 + index * 200, 6))
  })

  it("alternates the nodes above and below the middle of the strip", () => {
    const strip = stripAxis(four, {width: 1200, trailing: 0})
    const middle = STRIP.height / 2

    expect(strip.nodes.map(n => n.y)).toEqual([
      middle - STRIP.amplitude, middle + STRIP.amplitude,
      middle - STRIP.amplitude, middle + STRIP.amplitude,
    ])
  })

  it("starts the line left of the strip, so its beginning is never in view", () => {
    const strip = stripAxis(four, {width: 1200, trailing: 0})

    expect(strip.from).toBeLessThan(0)
    expect(strip.path.startsWith(`M ${strip.from},`)).toBe(true)
  })

  it("runs the line past the strip where nothing bounds it", () => {
    const strip = stripAxis(four, {width: 1200, trailing: 0})

    expect(strip.seam).toBeNull()
    expect(strip.to).toBeGreaterThan(strip.track)
  })

  it("stops the line on the slant that divides the last stop from the block that adds one", () => {
    const strip = stripAxis(four, {width: 1000, trailing: 1})
    const last = strip.nodes[strip.nodes.length - 1]!

    // Four shares of 200 for the stops, the fifth left for the plus.
    expect(strip.seam).toBe(800)
    // The division is drawn as a slanted rule, so a line that stopped at the upright
    // boundary would cross it. It stops where the rule actually is at its own height.
    const lean = Math.tan((STRIP.seam * Math.PI) / 180) * (STRIP.height / 2 - last.y)
    expect(strip.to).toBeCloseTo(800 + lean, 6)
    // The last node sits below the middle, so the rule leans away to the left of the
    // boundary there and the line has to stop short of it.
    expect(strip.to).toBeLessThan(800)
  })

  it("lights the line to the middle of the node it is asked about", () => {
    const strip = stripAxis(four, {width: 1000, trailing: 1})

    // Not near it, and not a share of some other box: the node's own centre, in pixels.
    expect(litAt(strip.nodes, 1)).toBeCloseTo(100, 6)
    expect(litAt(strip.nodes, 3)).toBeCloseTo(500, 6)
    expect(litAt(strip.nodes, 4)).toBeCloseTo(700, 6)
  })

  it("lights nothing when it is asked about no stop at all", () => {
    const strip = stripAxis(four, {width: 1000, trailing: 1})

    expect(litAt(strip.nodes, null)).toBe(0)
    expect(litAt(strip.nodes, 99)).toBe(0)
  })

  it("draws a stop on its own as one straight run", () => {
    const strip = stripAxis([stop(1, "Autumn", "2025")], {width: 800, trailing: 0})

    expect(strip.nodes).toHaveLength(1)
    expect(strip.path).toBe(`M ${strip.from},${strip.nodes[0]!.y} L ${strip.to},${strip.nodes[0]!.y}`)
  })

  it("has nothing to draw before the strip has been measured", () => {
    const strip = stripAxis(four, {width: 0, trailing: 0})

    expect(strip.path).toBe("")
  })

  it("has nothing to draw for no stops at all", () => {
    const strip = stripAxis([], {width: 1200, trailing: 1})

    expect(strip.path).toBe("")
    expect(strip.nodes).toEqual([])
  })
})
