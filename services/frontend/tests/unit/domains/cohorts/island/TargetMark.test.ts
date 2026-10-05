import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import TargetMark from "@/domains/cohorts/island/TargetMark.vue"
import {type SummaryTarget, TargetSystem} from "@/services/api"

const stubs = {RouterLink: {props: ["to"], template: '<a :to="to"><slot /></a>'}}
const both: SummaryTarget[] = [
  {system: TargetSystem.DISCORD, label: "Sitecie", made: true, externalId: "900"},
  {system: TargetSystem.BREVO, label: "Sitecie list", made: true, externalId: "31"},
]
const mark = (system: TargetSystem, targets: SummaryTarget[] = both, quiet = false) =>
  mount(TargetMark, {props: {system, targets, quiet, testid: "mark"}, global: {stubs}})

describe("a role or a list in a list", () => {
  it("names the role as Discord does and the list by its name, each leading to its own page", () => {
    const role = mark(TargetSystem.DISCORD).get("[data-testid=mark]")
    const list = mark(TargetSystem.BREVO).get("[data-testid=mark]")

    expect([role.text(), role.attributes("to")]).toEqual(["@Sitecie", "/management/platforms/discord/roles/900"])
    expect([list.text(), list.attributes("to")]).toEqual(["Sitecie list", "/management/platforms/brevo/lists/31"])
  })

  it("names one it cannot address without a link, and says when there is none", () => {
    const unaddressed = mark(TargetSystem.BREVO, [{system: TargetSystem.BREVO, label: "Old list", made: true}])
    const unmade = mark(TargetSystem.DISCORD, [{system: TargetSystem.DISCORD, label: "Sitecie", made: false}])
    const quiet = mark(TargetSystem.BREVO, [], true)

    expect(unaddressed.get("[data-testid=mark]").element.tagName).toBe("SPAN")
    expect(unaddressed.text()).toBe("Old list")
    expect(unmade.text()).toBe("No role")
    expect(quiet.text()).toBe("No list")
  })
})
