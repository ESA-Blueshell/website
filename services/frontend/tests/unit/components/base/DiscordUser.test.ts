import {describe, expect, it} from "vitest"
import {mountInApp} from "../../helpers/testUtils"
import DiscordUser from "@/components/base/DiscordUser.vue"

describe("DiscordUser", () => {
  it("names the member and marks what they are doing", () => {
    const wrapper = mountInApp(DiscordUser, {
      props: {username: "roos", status: "online", avatarUrl: "https://cdn.test/roos.png"},
    })

    expect(wrapper.text()).toContain("roos")
    expect(wrapper.find(".discord-membership-status").classes()).toContain("discord-membership-online")
  })

  it.each([
    ["idle", "discord-membership-idle"],
    ["dnd", "discord-membership-dnd"],
  ])("marks a member who is %s", (status, marker) => {
    const wrapper = mountInApp(DiscordUser, {props: {username: "lena", status}})

    expect(wrapper.find(".discord-membership-status").classes()).toContain(marker)
  })

  // Half the width is what a member gets when the voice channels take the other half.
  it("takes half the row when the channels have the rest of it", () => {
    const wrapper = mountInApp(DiscordUser, {props: {username: "roos", halfWidth: true}})

    expect(wrapper.get(".discord-membership-entry").classes()).toContain("v-col--cols-md-6")
  })

  // The last tile stands for everybody the widget did not name, so it has no member behind it.
  it.each([
    [false, "v-col--cols-md-3"],
    [true, "v-col--cols-md-6"],
  ])("stands for the members the widget did not name, at half width %s", (halfWidth, marker) => {
    const wrapper = mountInApp(DiscordUser, {props: {customText: "+40 more", halfWidth}})

    expect(wrapper.get(".discord-membership-entry").classes()).toContain(marker)
    expect(wrapper.text()).toContain("+40 more")
  })

  it("has no member behind the tile it draws for the rest of them", () => {
    const wrapper = mountInApp(DiscordUser, {props: {customText: "+40 more"}})

    expect(wrapper.text()).toContain("+40 more")
    expect(wrapper.find(".discord-membership-image-wrapper").exists()).toBe(false)
  })
})
