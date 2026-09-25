import {createApp, nextTick} from "vue"
import App from "@/App.vue"
import router from "@/plugins/router"
import VueSignaturePad from "vue-signature-pad"
import vuetify from "@/plugins/vuetify.ts"
import "@/plugins/validation.ts"
import store from "@/plugins/store"
import {createVPhoneInput, selectPhoneCountryInput, VPhoneCountryFlagSvg} from "v-phone-input"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/styles"
import "@/styles/island.css"
import {addCanvasRoutes} from "./extraRoutes"

type Step = {fill?: string, value?: string, click?: string, hover?: string, wait?: number}

const pause = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const byTestid = (testid: string) => document.querySelector<HTMLElement>(`[data-testid="${testid}"]`)

async function waitFor(testid: string) {
  for (let tries = 0; tries < 100; tries++) {
    const found = byTestid(testid)
    if (found) return found
    await pause(50)
  }
  return null
}

/** Starts the site on [element], at [route], and plays [steps] once it has drawn. */
export async function startApp(element: HTMLElement, route: string, steps: Step[]) {
  addCanvasRoutes(router)
  await router.push(route)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const vPhoneInput = createVPhoneInput({...(selectPhoneCountryInput as any), countryDisplayComponent: VPhoneCountryFlagSvg})
  const app = createApp(App)
  app.use(store)
  app.use(router)
  app.use(VueSignaturePad)
  app.use(vuetify)
  app.use(vPhoneInput)
  app.mount(element)
  await router.isReady()
  await nextTick()
  for (const step of steps) {
    if (step.wait) await pause(step.wait)
    const target = step.fill ?? step.click ?? step.hover
    if (!target) continue
    const found = await waitFor(target)
    if (!found) continue
    const field = found instanceof HTMLInputElement || found instanceof HTMLTextAreaElement ? found : found.querySelector("input, textarea")
    if (step.fill != null && field) {
      field.value = step.value ?? ""
      field.dispatchEvent(new Event("input", {bubbles: true}))
      field.dispatchEvent(new Event("change", {bubbles: true}))
    } else if (step.click) {
      found.click()
    } else if (step.hover) {
      found.dispatchEvent(new MouseEvent("mouseenter", {bubbles: true}))
    }
    await pause(120)
  }
}
