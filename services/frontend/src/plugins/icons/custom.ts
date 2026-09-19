import {h} from "vue"
import type {IconAliases, IconSet} from "vuetify"

import Discord from "@/assets/svgs/discord.svg?component"

/*
 * Discord's mark alone. The management icon lived here too, and left with the bar that drew it:
 * the bar draws its own marks now, so they all inherit `currentColor` instead of one of them
 * carrying a white fill of its own and reading as a different shade from its neighbours.
 */
const COMPONENTS = {
  discord: Discord,
} as const

export const customIconSet: IconSet = {
  component: (props) => {
    const iconName = props.icon as keyof typeof COMPONENTS
    const IconComponent = COMPONENTS[iconName]

    if (!IconComponent) {
      if (import.meta.env.DEV) {
        console.warn(`[Vuetify custom icons] Unknown icon "${props.icon}"`)
      }
      return null
    }

    return h(IconComponent, {...props})
  },
}

export const customAliases: Partial<IconAliases> = {
  discord: "custom:discord",
}
