declare module "*.svg?component" {
  import type {DefineComponent} from "vue"
  const component: DefineComponent<object, object, never>
}
