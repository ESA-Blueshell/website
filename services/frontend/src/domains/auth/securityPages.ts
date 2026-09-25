/** Where the security pages live. The router and every link to them read these. */
export const SECURITY_PAGES = {
  hub: "/account/security",
  password: "/account/security/password",
  email: "/account/security/email",
  twoFactor: "/account/security/two-factor",
  setUp: "/account/security/two-factor/set-up",
  signIns: "/account/security/sign-ins",
  log: "/account/security/log",
  required: "/account/set-up-two-factor",
} as const

export const SECURITY_CRUMB = {label: "Security", to: SECURITY_PAGES.hub}
