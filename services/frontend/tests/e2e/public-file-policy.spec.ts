import {expect, test, type Page} from "./test"

/**
 * What a browser does with a stored file somebody opens directly.
 *
 * An icon may be an SVG, which is a document rather than a bitmap: it can carry script, and the
 * file's own url is served inline from the api's origin beside the api's cookies. The upload
 * refuses a document like the one below, but a check against a parser is a check somebody can be
 * cleverer than, so the api serves every public file under a `Content-Security-Policy` that runs
 * no script whatever got through.
 *
 * Asserted in a browser, because a header is only a claim about what a browser will do. The file
 * really does carry a script — a sanitised sample would prove nothing — and the first test is
 * what makes the second one mean anything: it shows the script does run, and therefore that this
 * test can tell the difference.
 *
 * The policy is stated here as the api states it in `FileResponses.kt` — change one, change the
 * other. The route is the api's, answered here rather than proxied: what is under test is the
 * browser's response to those bytes and that header.
 */
const AT = "/files/public/game-icons/hostile.svg"

/** Where the script goes if it runs. Any path will do; the SPA answers all of them. */
const IF_IT_RAN = "/the-script-ran"

const HOSTILE = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
     onload="location.replace('${IF_IT_RAN}')">
  <script>location.replace('${IF_IT_RAN}')</script>
  <rect width="24" height="24" fill="#0af"/>
</svg>`

const POLICY = "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; font-src data:; sandbox"

const serve = (page: Page, headers: Record<string, string>) =>
  page.route(AT, route => route.fulfill({
    status: 200,
    contentType: "image/svg+xml",
    headers: {"cache-control": "no-store", ...headers},
    body: HOSTILE,
  }))

const where = (page: Page) => new URL(page.url()).pathname

test.describe("a stored vector opened at its own url", () => {
  test("carries a script that a browser given no policy really does run", async ({page}) => {
    await serve(page, {})
    await page.goto(AT)

    await page.waitForURL(url => url.pathname === IF_IT_RAN, {timeout: 5000})
    expect(where(page)).toBe(IF_IT_RAN)
  })

  test("runs none of it under the policy the api serves a public file with", async ({page}) => {
    await serve(page, {"content-security-policy": POLICY})
    await page.goto(AT)

    // Long enough that the test above has finished navigating twice over: the script runs while
    // the document is parsed, so there is nothing later to wait for.
    await page.waitForTimeout(2000)
    expect(where(page)).toBe(AT)
  })
})
