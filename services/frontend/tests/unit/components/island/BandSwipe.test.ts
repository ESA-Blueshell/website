import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import BandSwipe from "@/components/island/BandSwipe.vue"

/* jsdom has no pointer events and no animations: a MouseEvent carries what the band reads, and an
   animation that finishes at once stands in for the glide. */
const pointer = (target: Element, type: string, clientX: number) =>
  target.dispatchEvent(new MouseEvent(type, {clientX, clientY: 10, bubbles: true}))

beforeEach(() => {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: query === "(pointer: coarse)",
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }))
  // jsdom has no Web Animations, and only an animation's finish and cancel are read.
  Element.prototype.animate = vi.fn(() => ({finished: Promise.resolve(), cancel: vi.fn()}) as unknown as Animation)
  Element.prototype.setPointerCapture = vi.fn()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const mountBand = (props: Record<string, unknown>) => mount(BandSwipe, {
  props: {stop: 2, stops: [1, 2, 3], testid: "swipe", ...props},
  slots: {default: `<template #default="{stop}"><p class="stop">Stop {{ stop }}</p></template>`},
  attachTo: document.body,
})

describe("a band dragged under a finger", () => {
  it("carries the band and its neighbour by writing where they stand, not by drawing them again", async () => {
    const wrapper = mountBand({})
    const shell = wrapper.get("[data-testid=swipe]").element

    pointer(shell, "pointerdown", 500)
    pointer(shell, "pointermove", 540)
    await flushPromises()
    pointer(shell, "pointermove", 600)

    const carried = wrapper.get(".band-swipe__carried").element as HTMLElement
    const aside = wrapper.get(".band-swipe__aside").element as HTMLElement
    expect(carried.style.transform).toBe("translate3d(calc(100px + 0px), 0, 0)")
    expect(aside.style.transform).toBe("translate3d(calc(100px + -100%), 0, 0)")
    expect(aside.textContent).toBe("Stop 1")
    expect(wrapper.emitted("reaching")).toEqual([[[1, 3]]])
    wrapper.unmount()
  })

  it("puts the track away at once where the page answers with the stop it already shows", async () => {
    const wrapper = mountBand({})
    const shell = wrapper.get("[data-testid=swipe]").element
    const swipe = async (from: number, to: number) => {
      pointer(shell, "pointerdown", from)
      pointer(shell, "pointermove", from + Math.sign(to - from) * 40)
      await flushPromises()
      pointer(shell, "pointermove", to)
      pointer(shell, "pointerup", to)
      await flushPromises()
      await flushPromises()
    }

    // Back to 1, which the page has not answered yet; then on again from 1, which is 2, the stop
    // still drawn behind it.
    await swipe(100, 900)
    await swipe(900, 100)

    expect(wrapper.emitted("travel")).toEqual([[1], [2]])
    expect(wrapper.find(".band-swipe__aside").exists()).toBe(false)
    expect((wrapper.get(".band-swipe__carried").element as HTMLElement).style.transform).toBe("")
    expect((shell as HTMLElement).style.height).toBe("")
    wrapper.unmount()
  })
})

describe("a committed gesture", () => {
  it("holds the band at the neighbour's height until the arrived stop stands, then lets it go", async () => {
    const wrapper = mountBand({})
    const shell = wrapper.get("[data-testid=swipe]").element as HTMLElement

    pointer(shell, "pointerdown", 100)
    pointer(shell, "pointermove", 140)
    await flushPromises()
    pointer(shell, "pointermove", 900)
    Object.defineProperty(wrapper.get(".band-swipe__aside").element, "offsetHeight", {configurable: true, get: () => 480})
    pointer(shell, "pointerup", 900)
    await flushPromises()

    expect(wrapper.emitted("travel")).toEqual([[1]])
    expect(shell.style.height).toBe("480px")

    await wrapper.setProps({stop: 1})
    await flushPromises()
    expect(shell.style.height).toBe("")
    wrapper.unmount()
  })
})

describe("a pass from one stop to the next", () => {
  it("holds the page at the leaving stop's height for the pass, then lets it go in one step", async () => {
    vi.useFakeTimers()
    const wrapper = mountBand({})
    const shell = wrapper.get("[data-testid=swipe]").element as HTMLElement
    Object.defineProperty(shell, "offsetHeight", {configurable: true, get: () => 640})

    await wrapper.setProps({stop: 1})
    expect(shell.style.height).toBe("640px")

    vi.advanceTimersByTime(2000)
    expect(shell.style.height).toBe("")
    wrapper.unmount()
    vi.useRealTimers()
  })

  it("reads the way of a pass off the line the leaving stop was on, where it has left the line", async () => {
    const wrapper = mountBand({stop: 3})
    const shell = wrapper.get("[data-testid=swipe]").element as HTMLElement
    Object.defineProperty(shell, "offsetHeight", {configurable: true, get: () => 640})

    // A season a page stood on without listing it drops off the line as the page moves on.
    await wrapper.setProps({stop: 1, stops: [1, 2]})
    expect(shell.style.height).toBe("640px")
    wrapper.unmount()
  })

  it("holds nothing where the stop is answered afresh rather than travelled to", async () => {
    const wrapper = mountBand({})
    const shell = wrapper.get("[data-testid=swipe]").element as HTMLElement

    await wrapper.setProps({stop: 9})
    expect(shell.style.height).toBe("")
    wrapper.unmount()
  })
})
