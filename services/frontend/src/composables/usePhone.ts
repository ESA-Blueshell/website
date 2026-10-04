import {onBeforeUnmount, onMounted, ref, type Ref} from "vue"
import {PHONE} from "@/styles/breakpoints"

/** Whether the window is phone width, for a part that draws different markup there. */
export function usePhone(): Ref<boolean> {
  const query = typeof globalThis.matchMedia === "function" ? globalThis.matchMedia(PHONE) : null
  const phone = ref(query?.matches ?? false)
  const follow = (event: MediaQueryListEvent) => {
    phone.value = event.matches
  }
  onMounted(() => query?.addEventListener("change", follow))
  onBeforeUnmount(() => query?.removeEventListener("change", follow))
  return phone
}
