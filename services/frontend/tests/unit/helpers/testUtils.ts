import {nextTick} from "vue"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {vi} from "vitest"

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
