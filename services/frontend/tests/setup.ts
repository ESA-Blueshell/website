import {afterEach, beforeEach, vi} from "vitest"
import {config} from "@vue/test-utils"

config.global.config = {
  warnHandler: (msg: string) => {
    if (msg.startsWith("Failed to resolve component:")) return
    console.warn("[Vue warn]:", msg)
  },
}

config.global.stubs = {
  RouterLink: {
    template: "<a><slot /></a>",
  },
  RouterView: {
    template: "<div><slot /></div>",
  },
}

// The unit suite runs WITHOUT the Vuetify plugin: v-* tags render as unknown
// elements whose children pass through, which is enough for most components.
// VDataTableVirtual is the exception — its content lives in named slots
// (#headers / #item / #no-data) that an unknown element never invokes, so
// full-mount tests would see zero rows. Register a slot-rendering stand-in
// that renders ALL items (unit tests assert behavior, not virtualization;
// virtualization itself is covered by VirtualizedFilterPerf.test.ts).
config.global.components = {
  VDataTableVirtual: {
    props: {
      items: {type: Array, default: () => []},
    },
    template: `
      <div class="v-data-table-virtual-stub">
        <table>
          <thead><slot name="headers" /></thead>
          <tbody>
            <template v-for="(item, index) in items" :key="index">
              <slot name="item" :item="item" :index="index" />
            </template>
            <tr v-if="items.length === 0"><td><slot name="no-data" /></td></tr>
          </tbody>
        </table>
      </div>
    `,
  },
}

config.global.mocks = {
  $vuetify: {
    display: {
      mdAndUp: true,
      smAndDown: false,
      smAndUp: true,
      sm: false,
      xs: false,
    },
    theme: {
      global: {
        name: "light",
        current: {
          dark: false,
        },
      },
      computedThemes: {
        light: {
          colors: {
            wallpaper: "#101010",
          },
        },
      },
    },
  },
}

Object.defineProperty(globalThis, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
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

beforeEach(() => {
  document.cookie = ""
})

afterEach(() => {
  vi.clearAllMocks()
})
