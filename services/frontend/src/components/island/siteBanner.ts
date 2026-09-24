/**
 * The association's banner photograph, served from `public/` at the widths it is drawn at.
 *
 * Served rather than bundled, because `index.html` preloads it and TopBanner lays it as a
 * background, and neither can import a fingerprinted asset. Both write out these same widths,
 * so a width added here is added there too. `/banner.webp` itself stays the 3840px original,
 * which is also what a link preview is sent.
 */
const WIDTHS = [640, 960, 1280, 1920, 2560]

/** The copy a browser without `srcset` gets. */
export const SITE_BANNER = "/banner-1920.webp"

export const SITE_BANNER_SRCSET = [...WIDTHS.map(width => `/banner-${width}.webp ${width}w`), "/banner.webp 3840w"].join(", ")
