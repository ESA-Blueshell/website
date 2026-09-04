import {describe, expect, it, vi} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import {unmountAll} from "./testUtils"

/** A wrapper that tears down cleanly. */
const clean = () => ({unmount: vi.fn()}) as unknown as VueWrapper

/** A wrapper whose instance is already gone; Vue Test Utils throws on those. */
const broken = () =>
  ({
    unmount: vi.fn(() => {
      throw new TypeError("Cannot read properties of null (reading '$')")
    }),
  }) as unknown as VueWrapper

describe("unmountAll", () => {
  it("unmounts every wrapper and empties the list", () => {
    const a = clean()
    const b = clean()
    const wrappers = [a, b]

    unmountAll(wrappers, "example")

    expect(a.unmount).toHaveBeenCalledOnce()
    expect(b.unmount).toHaveBeenCalledOnce()
    expect(wrappers).toHaveLength(0)
  })

  /**
   * The point of the helper. Teardown runs in `afterEach`, so a throw there fails every
   * test after it — one upstream problem used to present as most of a file collapsing,
   * pointing at Vue Test Utils internals rather than at whatever actually broke.
   */
  it("does not let a wrapper that cannot be torn down fail the next test", () => {
    const wrappers = [clean(), broken(), clean()]

    expect(() => unmountAll(wrappers, "example")).not.toThrow()
    expect(wrappers).toHaveLength(0)
  })

  it("still tears down the wrappers either side of a broken one", () => {
    const first = clean()
    const last = clean()
    const wrappers = [first, broken(), last]

    unmountAll(wrappers, "example")

    expect(first.unmount).toHaveBeenCalledOnce()
    expect(last.unmount).toHaveBeenCalledOnce()
  })

  it("says what it was tearing down, rather than leaving the failure silent", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined)

    unmountAll([broken()], "JobManager")

    expect(warn).toHaveBeenCalledWith(
      expect.stringContaining("JobManager"),
      expect.any(Error),
    )
    warn.mockRestore()
  })

  it("has nothing to say when everything tears down", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined)

    unmountAll([clean(), clean()], "JobManager")

    expect(warn).not.toHaveBeenCalled()
    warn.mockRestore()
  })
})
