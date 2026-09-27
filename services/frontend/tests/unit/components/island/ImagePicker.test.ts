import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import ImagePicker from "@/components/island/ImagePicker.vue"
import type {Picture, PictureStore} from "@/components/island/pictures"

const held: Picture = {path: "art/1.webp", url: "/art/1.webp", width: 800, height: 450, renditions: [{url: "/art/1-400.webp", width: 400}]}
const stored: Picture = {path: "art/2.webp", url: "/art/2.webp", renditions: []}

const picker = (props: Record<string, unknown> = {}) => mount(ImagePicker, {
  props: {label: "Banner", testid: "art", store: vi.fn<PictureStore>(async () => ({ok: true, picture: stored})), ...props},
})

const choose = async (wrapper: ReturnType<typeof picker>, file: File) => {
  const input = wrapper.get("[data-testid=art-file]")
  Object.defineProperty(input.element, "files", {value: [file], configurable: true})
  await input.trigger("change")
  await flushPromises()
}

describe("ImagePicker", () => {
  it("cuts its frame to the shape the picture is used in", () => {
    const ratios = Object.fromEntries((["banner", "icon", "portrait", "poster"] as const)
      .map(shape => [shape, (picker({shape}).get("[data-testid=art-press]").element as HTMLElement).style.aspectRatio]))

    expect(ratios).toEqual({banner: "16 / 9", icon: "1 / 1", portrait: "2 / 3", poster: "1 / 1.414"})
  })

  it("fits an icon whole and crops the other shapes, unless told otherwise", () => {
    const fitOf = (props: Record<string, unknown>) =>
      (picker({picture: held, ...props}).get("[data-testid=art-preview]").element as HTMLElement).style.objectFit

    expect(fitOf({shape: "icon"})).toBe("contain")
    expect(fitOf({shape: "banner"})).toBe("cover")
    expect(fitOf({shape: "portrait"})).toBe("cover")
    expect(fitOf({shape: "icon", fit: "cover"})).toBe("cover")
    expect(fitOf({shape: "banner", fit: "contain"})).toBe("contain")
  })

  it("asks for the picture by its name while empty, and names it once one is held", () => {
    expect(picker().get("[data-testid=art-empty]").text()).toBe("Choose a banner")
    expect(picker({label: "Icon"}).get("[data-testid=art-empty]").text()).toBe("Choose an icon")
    expect(picker({say: "Choose a poster"}).get("[data-testid=art-empty]").text()).toBe("Choose a poster")
    expect(picker().text()).toContain("Pick one from this machine")

    const full = picker({picture: held})
    expect(full.get("[data-testid=art-replace]").text()).toBe("Banner")
    expect(full.text()).toContain("Pick another from this machine")
    expect(full.get("[data-testid=art-preview]").attributes("srcset")).toContain("/art/1-400.webp 400w")
  })

  it("admits a vector or a moving picture only where the use allows one", () => {
    const accept = (props: Record<string, unknown>) => picker(props).get("[data-testid=art-file]").attributes("accept")

    expect(accept({})).toBe("image/png,image/jpeg,image/webp")
    expect(accept({mayBeVector: true})).toBe("image/png,image/jpeg,image/webp,image/svg+xml")
    expect(accept({mayBeAnimated: true})).toBe("image/png,image/jpeg,image/webp,image/gif")
  })

  it("stores a chosen file and hands on the picture it became", async () => {
    const store = vi.fn<PictureStore>(async () => ({ok: true, picture: stored}))
    const wrapper = picker({store})
    const file = new File(["art"], "art.png", {type: "image/png"})

    await choose(wrapper, file)

    expect(store).toHaveBeenCalledWith(file)
    expect(wrapper.emitted("update:picture")).toEqual([[stored]])
    expect(wrapper.find("[data-testid=art-failure]").exists()).toBe(false)
  })

  it("says why a file is refused, in the field's own wrong colour, and keeps what it held", async () => {
    const wrapper = picker({store: vi.fn<PictureStore>(async () => ({ok: false, reason: "That is not a picture."}))})

    await choose(wrapper, new File(["x"], "x.png", {type: "image/png"}))

    expect(wrapper.get("[data-testid=art-failure]").text()).toBe("That is not a picture.")
    expect(wrapper.get("[data-testid=art-failure]").attributes("role")).toBe("alert")
    expect(wrapper.classes()).toContain("picture--wrong")
    expect(wrapper.emitted("update:picture")).toBeUndefined()
  })

  it("refuses a file over 15 MB before storing it", async () => {
    const store = vi.fn<PictureStore>()
    const wrapper = picker({store})
    const big = new File(["x"], "big.png", {type: "image/png"})
    Object.defineProperty(big, "size", {value: 16 * 1024 * 1024})

    await choose(wrapper, big)

    expect(store).not.toHaveBeenCalled()
    expect(wrapper.get("[data-testid=art-failure]").text()).toBe("That file is larger than 15 MB.")
  })

  it("does nothing when the chooser closes with no file", async () => {
    const store = vi.fn<PictureStore>()
    const wrapper = picker({store})
    const input = wrapper.get("[data-testid=art-file]")
    Object.defineProperty(input.element, "files", {value: [], configurable: true})

    await input.trigger("change")

    expect(store).not.toHaveBeenCalled()
  })

  it("shows it is working while the bytes go up, and while something outside is busy", async () => {
    let finish: (value: Awaited<ReturnType<PictureStore>>) => void = () => {}
    const wrapper = picker({store: vi.fn<PictureStore>(() => new Promise(done => { finish = done }))})
    const input = wrapper.get("[data-testid=art-file]")
    Object.defineProperty(input.element, "files", {value: [new File(["a"], "a.png")], configurable: true})
    await input.trigger("change")

    expect(wrapper.text()).toContain("Uploading")
    expect(input.attributes("disabled")).toBeDefined()

    finish({ok: true, picture: stored})
    await flushPromises()
    expect(wrapper.text()).not.toContain("Uploading")

    expect(picker({busy: true}).get("[data-testid=art-file]").attributes("disabled")).toBeDefined()
  })

  it("takes the picture off with its cross, which a required picture does not offer", async () => {
    const wrapper = picker({picture: held})
    await wrapper.get("[data-testid=art-clear]").trigger("click")

    expect(wrapper.emitted("update:picture")).toEqual([[null]])
    expect(picker({picture: held, mayClear: false}).find("[data-testid=art-clear]").exists()).toBe(false)
    expect(picker().find("[data-testid=art-clear]").exists()).toBe(false)
  })

  it("draws the frame alone as a tile, for a tight row", () => {
    expect(picker({layout: "tile"}).classes()).toContain("picture--tile")
    expect(picker().classes()).toContain("picture--row")
  })
})
