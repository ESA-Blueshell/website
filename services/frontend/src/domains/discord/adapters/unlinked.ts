import {listMyUnlinkedTargets, TargetSystem} from "@/services/api"
import {readOr} from "@/utils/answers"

/** The Discord roles the reader belongs on and reaches only once their Discord account is linked. */
export const listMyUnlinkedRoles = async (): Promise<string[]> =>
  (await readOr(listMyUnlinkedTargets(), [])).filter((one) => one.system === TargetSystem.DISCORD).map((one) => one.label)
