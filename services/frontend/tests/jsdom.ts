import {vi} from "vitest"

// Loaded before the setup file that installs Vuetify, because Vuetify reads what the
// browser supports once at import and never asks again — a shim defined after that is a
// shim it will never see.

// jsdom implements no media queries, so anything asking for one needs an answer
// here. A plain function rather than a mock: mockReset is on, which strips a
// mock's implementation before every test, and a matchMedia that returns
// undefined is worse than none at all — it passes a typeof check and then throws
// on the property access. Nothing needs to assert on these calls.
Object.defineProperty(globalThis, "matchMedia", {
  configurable: true,
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

// jsdom has no visual viewport, and the overlay positioning reads it bare rather
// than guarding it. A plain object for the same reason matchMedia is a plain
// function; nothing here is laid out, so the numbers only have to agree with the
// window they describe.
Object.defineProperty(globalThis, "visualViewport", {
  configurable: true,
  writable: true,
  value: {
    width: globalThis.innerWidth,
    height: globalThis.innerHeight,
    scale: 1,
    offsetLeft: 0,
    offsetTop: 0,
    pageLeft: 0,
    pageTop: 0,
    onresize: null,
    onscroll: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  },
})

// jsdom never intersects anything, so content held back until it scrolls into view
// would stay collapsed for the whole run. Reporting one intersection per observed
// element lets it resolve; nothing here waits for a second.
Object.defineProperty(globalThis, "IntersectionObserver", {
  configurable: true,
  value: class {
    constructor(
      private readonly callback: (
        entries: IntersectionObserverEntry[],
        observer: IntersectionObserver,
      ) => void,
    ) {}

    observe(target: Element) {
      this.callback(
        [{isIntersecting: true, intersectionRatio: 1, target} as IntersectionObserverEntry],
        this as unknown as IntersectionObserver,
      )
    }

    unobserve() {}
    disconnect() {}
    takeRecords(): IntersectionObserverEntry[] {
      return []
    }
  },
})

Object.defineProperty(globalThis.HTMLElement.prototype, "scrollIntoView", {
  configurable: true,
  value: vi.fn(),
})

Object.defineProperty(globalThis, "ResizeObserver", {
  configurable: true,
  value: class {
    observe() {}
    unobserve() {}
    disconnect() {}
  },
})

Object.defineProperty(globalThis.URL, "createObjectURL", {
  configurable: true,
  value: vi.fn(() => "blob:mock"),
})

Object.defineProperty(globalThis.URL, "revokeObjectURL", {
  configurable: true,
  value: vi.fn(),
})

const clipboard = {
  writeText: vi.fn().mockResolvedValue(undefined),
}

if (!("clipboard" in globalThis.navigator)) {
  Object.defineProperty(globalThis.navigator, "clipboard", {
    configurable: true,
    value: clipboard,
  })
} else {
  vi.spyOn(globalThis.navigator.clipboard, "writeText").mockResolvedValue(undefined)
}
