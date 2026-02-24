# Cookie Policy of Blueshell E-Sports Association Enschede

Last updated: February 23, 2026

## Interpretation and Definitions

### Interpretation

The words of which the initial letter is capitalized have meanings defined under the following conditions.  
The following definitions shall have the same meaning regardless of whether they appear in singular or in plural.

### Definitions

For the purposes of this Cookie Policy:

- `Company` (referred to as either "the Company", "We", "Us" or "Our" in this Cookie Policy) refers to Blueshell
  E-Sports Association Enschede, Enschede.
- `Cookies` means small files that are placed on your computer, mobile device, or any other device by a website.
- `Website` refers to Blueshell, accessible from [esa-blueshell.nl](https://esa-blueshell.nl).
- `You` means the individual accessing or using the Website, or a company/legal entity on behalf of such individual.
- `Local Storage` means browser-based storage (for example `localStorage`) used to persist key/value data on your
  device.
- `Session Storage` means browser-based storage (for example `sessionStorage`) cleared when the browser session ends.

## The Use of Cookies

### Type of Cookies We Use

Cookies can be "Persistent" or "Session" cookies. Persistent cookies remain on your device when you go offline, while
Session cookies are deleted when you close your browser.

We currently use session and persistent cookies for essential security and functionality.

## Cookie and Storage Inventory

### Cookies set by Blueshell application components

| Name            | Type   | Set by      | Purpose                                                                                                            | Typical retention                                        | Essential                      |
|--------------|--------|-----------|--------------------------------------|-------------------|------------|
| BSH_<br/>AUTH   | Cookie | API backend | Authenticated session fallback for <br/> JWT token handling.                                                       | Until token expiry (configured to about 24h by default). | Yes                            |
| XSRF-<br/>TOKEN | Cookie | API backend | CSRF protection for state-changing <br/> requests.                                                                 | Session-like / regenerated with security flow.           | Yes                            |
| login           | Cookie | Frontend    | Stores non-sensitive UI login/session <br/> state (userId, username, roles, <br/> expiry metadata; no auth token). | 30 days (default frontend setting).                      | Functional                     |
| guestData       | Cookie | Frontend    | Stores guest event-signup edit context <br/> (name, email, discord, phone, <br/> guest access token).              | 30 days (default frontend setting).                      | Functional for guest edit flow |

### Browser storage used by the website

| Storage key                          | Storage type    | Purpose                                                   | Typical retention                                 | Essential                     |
|--------------------------------------|------------|----------------------|------------------|------------|
| esa-blueshell.nl:darkMode   | Local Storage   | Saves theme preference.                                   | Until removed by user/browser.                    | Functional                    |
| esa-blueshell.nl:cookiesAccepted | Local Storage   | Saves dismissal state of cookie snackbar.                 | Until removed by user/browser.                    | Functional                    |
| auth:ping                            | Local Storage   | Cross-tab login-state synchronization signal.             | Updated during auth sync; persists until cleared. | Functional                    |
| recovery:password-reset:token   | Session Storage | Temporary recovery-token handling in password reset flow. | Session duration.                                 | Essential for recovery flow   |
| recovery:user-activation:token  | Session Storage | Temporary token handling in user activation flow.         | Session duration.                                 | Essential for activation flow |
| recovery:member-activation:token | Session Storage | Temporary token handling in member activation flow.       | Session duration.                                 | Essential for activation flow |

## Third-Party Services and Potential Third-Party Cookies

When you use specific features, your browser may connect directly to third-party services (for example Discord, Google
Maps, Google Calendar, or social-media destinations).  
Those third parties may set their own cookies or process your IP/device metadata under their own policies.

Examples in current website features:

- Google Maps embed on the contact page.
- Discord widget API request for public server presence.
- Google Calendar subscription links.
- External social-media links.

## Necessary / Essential Cookies

Necessary cookies are used to provide core services such as login/session security, CSRF protection, and account/event
workflow integrity.

Without these cookies/storage items, parts of requested functionality cannot be provided (for example login, secure form
submission, recovery, and guest signup editing).

## Functionality Cookies and Storage

Functionality cookies/storage are used to remember preferences and improve usability, such as dark-mode selection and
cross-tab auth UI synchronization.

## Cookies We Do Not Set by Default

The application does not set first-party analytics or advertising cookies by default.

## Your Choices Regarding Cookies

If you prefer to avoid cookies, you can disable cookies in your browser and delete existing cookies for this website.  
You can also clear local/session storage via browser settings.

Please note: disabling essential cookies/storage can prevent authentication, secure request handling, account recovery
flows, and certain event-signup features from working correctly.

If you would like to delete cookies or instruct your web browser to delete/refuse cookies, please visit your browser
help pages:

- Chrome: [Google support](https://support.google.com/accounts/answer/32050)
- Internet Explorer: [Microsoft support](http://support.microsoft.com/kb/278835)
- Firefox: [Mozilla support](https://support.mozilla.org/en-US/kb/delete-cookies-remove-info-websites-stored)
- Safari: [Apple support](https://support.apple.com/guide/safari/manage-cookies-and-website-data-sfri11471/mac)

For any other browser, please visit your browser's official support pages.

## Contact Us

If you have any questions about this Cookie Policy, you can contact us:

- By email: `board@blueshell.utwente.nl`
