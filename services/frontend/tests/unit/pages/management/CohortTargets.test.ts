import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import CohortTargets from "@/pages/management/CohortTargets.vue"
import ManagerCard from "@/components/common/cards/ManagerCard.vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import {fetchTargetDescriptors, fetchTargetFolders, fetchTargetOptions} from "@/domains/cohorts/adapters/cohorts"
import {mountInApp} from "../helpers"

vi.mock("@/domains/cohorts/adapters/cohorts", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  fetchTargetDescriptors: vi.fn(),
  fetchTargetOptions: vi.fn(),
  fetchTargetFolders: vi.fn(),
}))

const mountPage = async () => {
  vi.mocked(fetchTargetDescriptors).mockResolvedValue([{system: "BREVO", kind: "LIST"}])
  vi.mocked(fetchTargetOptions).mockResolvedValue([{
    system: "BREVO", externalId: "7", kind: "LIST", label: "Guests", folderLabel: "Newsletter",
    memberCount: 3, linkedCohortId: null, path: ["Brevo", "Newsletter"],
  }])
  vi.mocked(fetchTargetFolders).mockResolvedValue(["Newsletter"])
  const wrapper = mountInApp(CohortTargets)
  await flushPromises()
  return wrapper
}

describe("CohortTargets", () => {
  it("heads the page with the system's name and offers every target a move", async () => {
    const wrapper = await mountPage()

    expect(wrapper.findAllComponents(ManagerCard)[0]!.props("title")).toBe("Brevo")
    expect(wrapper.find("[data-testid=cohort-targets-selection-bar]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=cohort-target-select-7]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=cohort-target-move-7]").exists()).toBe(true)
  })

  it("titles a move of the selection by what Brevo holds", async () => {
    const wrapper = await mountPage()

    await wrapper.get("[data-testid=cohort-target-select-7] input").setValue(true)
    await wrapper.get("[data-testid=cohort-targets-move-selected]").trigger("click")
    await flushPromises()

    expect(wrapper.getComponent(BaseModal).props("title")).toBe("Move 1 brevo list")
  })
})
