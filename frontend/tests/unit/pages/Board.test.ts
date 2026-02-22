import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import {nextTick} from "vue"
import Board from "@/pages/Board.vue"

describe("Board page", () => {
  it("keeps first board expanded and toggles older boards", async () => {
    const wrapper = shallowMount(Board, {
      global: {
        stubs: {
          BoardMemberRow: true,
        },
      },
    })

    expect((wrapper.vm as any).expandedBoards[0]).toBe(true)
    expect((wrapper.vm as any).expandedBoards[1]).toBe(false)

    ;(wrapper.vm as any).toggleBoard(1)
    await nextTick()
    expect((wrapper.vm as any).expandedBoards[1]).toBe(true)

    ;(wrapper.vm as any).toggleBoard(1)
    await nextTick()
    expect((wrapper.vm as any).expandedBoards[1]).toBe(false)
  })
})
