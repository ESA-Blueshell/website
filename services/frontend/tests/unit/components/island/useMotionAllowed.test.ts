import {afterEach, describe, expect, it, vi} from "vitest"
import {effectScope} from "vue"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"

type Listener = (event: MediaQueryListEvent) => void

/** Stands in for the browser's answer to the reduced-motion query. */
const stubPreference = (matches: boolean) => {
  const listeners: Listener[] = []
  const media = {
    matches,
    addEventListener: (_: string, listener: Listener) => listeners.push(listener),
    removeEventListener: vi.fn(),
  }
  vi.stubGlobal("matchMedia", vi.fn(() => media))
  return {
    change: (next: boolean) => listeners.forEach(l => l({matches: next} as MediaQueryListEvent)),
    removeEventListener: media.removeEventListener,
  }
}

afterEach(() => vi.unstubAllGlobals())

describe("useMotionAllowed", () => {
  it("allows both kinds of motion for a visitor who has not asked otherwise", () => {
    stubPreference(false)

    const motion = useMotionAllowed()

    expect(motion.reduced.value).toBe(false)
    expect(motion.decorative.value).toBe(true)
    expect(motion.explanatory.value).toBe(true)
  })

  it("drops the decorative motion and keeps what explains a change", () => {
    stubPreference(true)

    const motion = useMotionAllowed()

    // Parallax, drift, tilt and counters are what the preference is about.
    expect(motion.decorative.value).toBe(false)
    // A crossfade telling you the roster changed is not decoration.
    expect(motion.explanatory.value).toBe(true)
  })

  it("shortens a duration rather than removing the transition", () => {
    stubPreference(true)

    const motion = useMotionAllowed()

    expect(motion.duration(0.6)).toBe(0.12)
    // Something already brief is left alone rather than stretched to the ceiling.
    expect(motion.duration(0.05)).toBe(0.05)
  })

  it("leaves durations alone when motion is welcome", () => {
    stubPreference(false)

    expect(useMotionAllowed().duration(0.6)).toBe(0.6)
  })

  it("follows the preference changing mid-visit", () => {
    const preference = stubPreference(false)
    const motion = useMotionAllowed()

    preference.change(true)

    // Someone turning the setting on should not have to reload to be believed.
    expect(motion.reduced.value).toBe(true)
    expect(motion.decorative.value).toBe(false)
  })

  it("stops listening when its scope goes away", () => {
    const preference = stubPreference(false)
    const scope = effectScope()
    scope.run(() => useMotionAllowed())

    scope.stop()

    expect(preference.removeEventListener).toHaveBeenCalled()
  })

  it("assumes motion is welcome where the query cannot be asked", () => {
    // Server-rendered or jsdom without matchMedia: render the animated path
    // rather than silently treating everyone as reduced-motion.
    vi.stubGlobal("matchMedia", undefined)

    const motion = useMotionAllowed()

    expect(motion.reduced.value).toBe(false)
    expect(motion.duration(0.4)).toBe(0.4)
  })
})
