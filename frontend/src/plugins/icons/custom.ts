import {h} from "vue"
import type {IconAliases, IconSet} from "vuetify"

import Discord from "@/assets/svgs/discord.svg?component"
import AccountMultipleEdit from "@/assets/svgs/account-multiple-edit.svg?component"

const COMPONENTS = {
  discord: Discord,
  "account-multiple-edit": AccountMultipleEdit,
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
  accountMultipleEdit: "custom:account-multiple-edit",
}
