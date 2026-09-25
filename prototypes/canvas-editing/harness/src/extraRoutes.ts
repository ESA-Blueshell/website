import type {Router} from "vue-router"
import {routes as boards} from "./routes/boards"
import {routes as committees} from "./routes/committees"
import {routes as competition} from "./routes/competition"
import {routes as games} from "./routes/games"

/** Pages the canvas shows that the site does not route in a production build. */
export function addCanvasRoutes(router: Router) {
  router.addRoute({path: "/design/fields", name: "design/fields", component: () => import("@/pages/design/FieldGallery.vue")})
  for (const route of [...games, ...competition, ...committees, ...boards]) {
    if (route.name && router.hasRoute(route.name)) router.removeRoute(route.name)
    router.addRoute(route)
  }
}
