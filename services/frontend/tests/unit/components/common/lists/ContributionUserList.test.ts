import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ContributionUserList from "@/components/common/lists/ContributionUserList.vue"

const users = [
  {
    id: 1,
    fullName: "Emma Dokter",
    firstName: "Emma",
    username: "lyndisluna",
    discord: "emma-filter",
    enabled: false,
    roles: ["MEMBER"],
  },
  {
    id: 2,
    fullName: "Viktor Petrov",
    firstName: "Viktor",
    username: "ariosfury",
    discord: "viktor-filter",
    enabled: true,
    roles: ["USER"],
  },
]

const vuetifyStubs = {
  VCard: {template: "<div><slot /></div>"},
  VBadge: {template: "<div><slot /></div>"},
  VIcon: {template: "<span><slot /></span>"},
  VExpandTransition: {template: "<div><slot /></div>"},
  VList: {template: "<div><slot /></div>"},
  VDivider: {template: "<hr />"},
  VTextField: {
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: "<input :value=\"modelValue\" @input=\"$emit('update:modelValue', $event.target.value)\" />",
  },
}

describe("ContributionUserList", () => {
  it("renders list and forwards contribution update event and period membership set", async () => {
    const wrapper = mount(ContributionUserList, {
      props: {
        title: "Contribution paid",
        users,
        contributionPeriodId: 2,
        contributions: [],
        periodMemberUserIds: new Set([1]),
        startOpen: true,
      },
      global: {
        stubs: {
          ...vuetifyStubs,
          ContributionUserRow: {
            props: ["user", "periodMemberUserIds"],
            template: `
              <button
                class="row"
                :data-username="user.username"
                :data-is-period-member="String(periodMemberUserIds.has(user.id))"
                @click="$emit('update:contribution', { id: 99, userId: user.id, contributionPeriodId: 2 })"
              >
                row
              </button>
            `,
          },
        },
      },
    })

    expect(wrapper.text()).toContain("Contribution paid")

    const rows = wrapper.findAll("button.row")
    expect(rows).toHaveLength(2)
    expect(rows[0].attributes("data-username")).toBe("lyndisluna")
    expect(rows[0].attributes("data-is-period-member")).toBe("true")
    expect(rows[1].attributes("data-username")).toBe("ariosfury")
    expect(rows[1].attributes("data-is-period-member")).toBe("false")

    await rows[0].trigger("click")
    expect(wrapper.emitted("update:contribution")?.length).toBe(1)
  })

  it("filters users by multiple fields", async () => {
    const wrapper = mount(ContributionUserList, {
      props: {
        title: "Contribution unpaid",
        users,
        contributionPeriodId: 2,
        contributions: [],
        startOpen: true,
      },
      global: {
        stubs: {
          ...vuetifyStubs,
          ContributionUserRow: {
            props: ["user", "periodMemberUserIds"],
            template: "<div class='row-username'>{{ user.username }}</div>",
          },
        },
      },
    })

    expect(wrapper.text()).toContain("lyndisluna")
    expect(wrapper.text()).toContain("ariosfury")

    const search = wrapper.find("input")
    await search.setValue("Emma emma-filter")

    expect(wrapper.text()).toContain("lyndisluna")
    expect(wrapper.text()).not.toContain("ariosfury")
  })
})
