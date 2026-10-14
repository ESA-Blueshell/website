import {beforeEach, describe, expect, it, vi} from "vitest"
import {effectScope, nextTick, ref} from "vue"
import {flushPromises} from "@vue/test-utils"

const {readOwnContributions} = vi.hoisted(() => ({readOwnContributions: vi.fn()}))
vi.mock("@/domains/contribution/adapters/ownContributions", () => ({readOwnContributions}))

const fresh = async () => {
  vi.resetModules()
  return import("@/domains/contribution/heldMembership")
}

describe("whether the reader holds a membership", () => {
  beforeEach(() => readOwnContributions.mockReset())

  it("is read once per signed-in person, whoever asks, and is false for nobody", async () => {
    const {useHeldMembership} = await fresh()
    readOwnContributions.mockResolvedValue({held: true, paysByIncasso: false, periods: []})
    const userId = ref<number | null>(7)
    const scope = effectScope()

    const [bar, tabs] = scope.run(() => [useHeldMembership(() => userId.value), useHeldMembership(() => userId.value)])!
    await flushPromises()
    expect(bar.value).toBe(true)
    expect(tabs.value).toBe(true)
    expect(readOwnContributions).toHaveBeenCalledTimes(1)

    userId.value = null
    await nextTick()
    expect(bar.value).toBe(false)

    readOwnContributions.mockResolvedValue(null)
    userId.value = 8
    await flushPromises()
    expect(bar.value).toBe(false)
    expect(readOwnContributions).toHaveBeenCalledTimes(2)
    scope.stop()
  })

  it("follows a fresh read of the page", async () => {
    const {noteHeld, useHeldMembership} = await fresh()
    readOwnContributions.mockResolvedValue({held: false, paysByIncasso: false, periods: []})
    const scope = effectScope()
    const held = scope.run(() => useHeldMembership(() => 7))!
    await flushPromises()

    noteHeld({held: true, paysByIncasso: false, periods: []})

    expect(held.value).toBe(true)
    scope.stop()
  })
})
