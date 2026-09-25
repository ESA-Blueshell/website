/** What every security task page's spec replaces: the frame, and the step-up dialog it opens. */
export const frameStub = {
  name: "AccountFrame",
  props: ["heading", "crumb", "islandContent", "tabs", "eyebrow", "body"],
  template: "<div><slot /><slot name=\"actions\" /></div>",
}

export const stepUpStub = {
  name: "StepUpDialog",
  props: ["modelValue", "twoFactorOn"],
  emits: ["proved", "update:modelValue"],
  template: "<div />",
}

export const refused = (reason: string, needsStepUp = false) => ({ok: false, reason, needsStepUp})
export const ok = <T>(value?: T) => ({ok: true, value})
