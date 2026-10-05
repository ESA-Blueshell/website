import {afterEach, describe, expect, it, vi} from "vitest"
import {DOMWrapper, flushPromises, mount, type VueWrapper} from "@vue/test-utils"
import BulkAdd from "@/components/management/BulkAdd.vue"

const items = [{key: 1, name: "Sitecie", note: "@Sitecie and #sitecie"}, {key: 2, name: "Lancie", note: "@Lancie and #lancie"}]
const opened: VueWrapper[] = []
const inPage = (testid: string) => new DOMWrapper(document.body).find(`[data-testid="${testid}"]`)

const dialog = async (props: Record<string, unknown> = {}) => {
  const run = vi.fn(async (item: {key: number}) => (item.key === 2 ? {ok: false as const, reason: "Discord refused."} : {ok: true as const}))
  const wrapper = mount(BulkAdd, {
    props: {open: false, title: "Add Discord roles", each: "a Discord role", noun: ["committee", "committees"], items, run, testid: "bulk", ...props},
    attachTo: document.body,
  })
  opened.push(wrapper)
  await wrapper.setProps({open: true})
  await flushPromises()
  return {wrapper, run}
}

describe("adding to many rows at once", () => {
  afterEach(() => {
    for (const wrapper of opened.splice(0)) wrapper.unmount()
    document.body.innerHTML = ""
  })

  it("shows who gets what and who is left out, asks once more, and only then does the work", async () => {
    const {wrapper, run} = await dialog({skipped: [{name: "Oldcie", why: "Archived"}]})

    expect(inPage("bulk-preview").text()).toContain("2 committees will each get a Discord role. Nothing is added yet.")
    expect(inPage("bulk-preview").text()).toContain("@Sitecie and #sitecie")
    expect(inPage("bulk-preview").text()).toContain("OldcieArchived")
    await inPage("bulk-continue").trigger("click")
    expect(inPage("bulk-confirm").text()).toContain("Add a Discord role to 2 committees now?")
    expect(run).not.toHaveBeenCalled()

    await inPage("bulk-back").trigger("click")
    expect(inPage("bulk-preview").exists()).toBe(true)
    await inPage("bulk-continue").trigger("click")
    await inPage("bulk-go").trigger("click")
    await flushPromises()

    expect(run).toHaveBeenCalledTimes(2)
    expect(inPage("bulk-done").text()).toContain("1 of 2 added.")
    expect(inPage("bulk-failures").text()).toContain("LancieDiscord refused.")
    expect(wrapper.emitted("done")).toHaveLength(1)
    await inPage("bulk-close").trigger("click")
    expect(wrapper.emitted("update:open")?.at(-1)).toEqual([false])
  })

  it("says how far it is while it works, and starts over when opened again", async () => {
    let finish: (() => void) | undefined
    const run = vi.fn(() => new Promise<{ok: true}>((resolve) => { finish = () => resolve({ok: true}) }))
    const {wrapper} = await dialog({items: [items[0]], run})

    await inPage("bulk-continue").trigger("click")
    await inPage("bulk-go").trigger("click")
    await flushPromises()
    expect(inPage("bulk-working").text()).toContain("Adding, 0 of 1 done.")
    expect(inPage("bulk-close").exists()).toBe(false)
    finish!()
    await flushPromises()
    expect(inPage("bulk-done").text()).toContain("1 of 1 added.")

    await wrapper.setProps({open: false})
    await wrapper.setProps({open: true})
    await flushPromises()
    expect(inPage("bulk-preview").text()).toContain("1 committee will each get")
  })

  it("offers nothing to continue with where no selected row can get it", async () => {
    await dialog({items: []})

    expect(inPage("bulk-preview").text()).toContain("None of the selected committees can get a Discord role.")
    expect(inPage("bulk-continue").attributes("disabled")).toBeDefined()
  })
})
