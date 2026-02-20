import {nextTick} from "vue"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {vi} from "vitest"

type ImportOriginal = <T = unknown>() => Promise<T>

export async function settle(): Promise<void> {
  await flushPromises()
  await nextTick()
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
