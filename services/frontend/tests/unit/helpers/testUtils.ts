import {type Component, h, nextTick} from "vue"
import {flushPromises, mount, type VueWrapper} from "@vue/test-utils"
import {vi} from "vitest"
import {VApp} from "vuetify/components"

type ImportOriginal = <T = unknown>() => Promise<T>

export async function settle(): Promise<void> {
  await flushPromises()
  await nextTick()
}

/**
 * Tears down every wrapper a suite mounted, keeping one bad teardown to itself: a throw
 * in `afterEach` fails every test after it, so one broken wrapper reads as a whole file
 * collapsing under a stack pointing into Vue Test Utils.
 */
export function unmountAll(wrappers: VueWrapper[], suite: string): void {
  while (wrappers.length > 0) {
    try {
      wrappers.pop()?.unmount()
    } catch (error) {
      console.warn(
        `${suite}: a wrapper could not be torn down, which is a symptom rather than the `
          + "fault — look for what broke the test before this one",
        error,
      )
    }
  }
}

export function hrefs(wrapper: VueWrapper<any>): string[] {
  return wrapper.findAll("a[href]").map((node) => node.attributes("href"))
}

export function createTestStore<
  G extends Record<string, unknown> = Record<string, unknown>,
>(
  getters: G,
): {
  getters: G
  state: Record<string, unknown>
  commit: ReturnType<typeof vi.fn>
  dispatch: ReturnType<typeof vi.fn>
} {
  return {
    getters,
    state: {},
    commit: vi.fn(),
    dispatch: vi.fn(),
  }
}

export async function withVuexUseStore(
  importOriginal: ImportOriginal,
  store: unknown,
): Promise<Record<string, unknown>> {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...(actual as Record<string, unknown>),
    useStore: () => store,
  }
}

// Spread rather than replaced: the setup file builds a real instance out of this module, so a
// stub that drops the rest of it takes every mount in the file down with it.
export async function withVuetify(
  importOriginal: ImportOriginal,
  overrides: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const actual = await importOriginal<typeof import("vuetify")>()
  return {
    ...(actual as Record<string, unknown>),
    ...overrides,
  }
}

export async function withVueRouter(
  importOriginal: ImportOriginal,
  options: {
    route?: unknown
    router?: unknown
  },
): Promise<Record<string, unknown>> {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...(actual as Record<string, unknown>),
    ...(options.route ? {useRoute: () => options.route} : {}),
    ...(options.router ? {useRouter: () => options.router} : {}),
  }
}

/**
 * Mounts a component under a `v-app`, and hands back the component's own wrapper.
 *
 * Anything rooted in `v-main`, `v-app-bar` or another layout child injects from the layout
 * `v-app` provides, and a mount without one renders nothing. The wrapper is the component's,
 * not the layout's, so `vm` and every query read the component under test; `setProps` is the
 * one thing it cannot do, Vue Test Utils reserving that for a root it mounted itself.
 */
export function mountInApp(
  component: Component,
  options: Record<string, unknown> = {},
): VueWrapper<any> {
  const {props, ...rest} = options
  const root = mount(VApp, {
    ...rest,
    slots: {default: () => h(component, props as Record<string, unknown>)},
  })

  const page = root.findComponent(component) as VueWrapper<any>
  // Tearing down the layout tears the component down with it, which is what a caller asking
  // to unmount means; the component's own wrapper refuses, not being the root.
  page.unmount = () => root.unmount()
  return page
}
