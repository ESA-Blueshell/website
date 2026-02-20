import {nextTick} from "vue"
import {flushPromises, type VueWrapper} from "@vue/test-utils"

export async function settle(): Promise<void> {
  await flushPromises()
  await nextTick()
}

export function hrefs(wrapper: VueWrapper<any>): string[] {
  return wrapper.findAll("a[href]").map((node) => node.attributes("href"))
}
