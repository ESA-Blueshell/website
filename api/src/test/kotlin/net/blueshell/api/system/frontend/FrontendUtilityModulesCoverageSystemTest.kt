package net.blueshell.api.system.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class FrontendUtilityModulesCoverageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `datetime utility handles valid and invalid input combinations`() {
        withPage { page ->
            page.navigate("$frontendUrl/")

            @Suppress("UNCHECKED_CAST")
            val result = page.evaluate(
                """
                async () => {
                  const datetime = await import('/src/utils/datetime.ts')

                  const formattedValid = datetime.safeFormatISO('2026-01-02T03:04:00', 'yyyy-MM-dd')
                  const formattedInvalid = datetime.safeFormatISO('definitely-not-iso', 'yyyy-MM-dd')
                  const emptyInput = datetime.toISO({})
                  const dateAndTime = datetime.toISO({ date: '2026-01-02', time: '03:04' })
                  const dateOnlyWithBase = datetime.toISO({
                    date: '2026-01-02',
                    dateTime: '2025-12-31T10:11:00'
                  })
                  const timeOnlyWithBase = datetime.toISO({
                    time: '14:15',
                    dateTime: '2025-12-31T10:11:00'
                  })
                  const dateOnly = datetime.toISO({ date: '2026-01-02' })
                  const timeOnly = datetime.toISO({ time: '07:30' })
                  const timeMissingMinutes = datetime.toISO({ time: '07' })
                  const invalidBase = datetime.toISO({ dateTime: 'bad-input' })

                  return {
                    formattedValid,
                    formattedInvalid,
                    emptyInput,
                    dateAndTimeIncludesTime: dateAndTime.includes('T03:04'),
                    dateOnlyWithBaseIncludesTime: dateOnlyWithBase.includes('T10:11'),
                    timeOnlyWithBaseIncludesTime: timeOnlyWithBase.includes('T14:15'),
                    dateOnlyHasMidnight: dateOnly.includes('T00:00'),
                    timeOnlyIncludesTime: timeOnly.includes('T07:30'),
                    timeMissingMinutesDefaultsToZero: timeMissingMinutes.includes('T07:00'),
                    invalidBase,
                  }
                }
                """.trimIndent()
            ) as Map<String, Any?>

            assertThat(result["formattedValid"]).isEqualTo("2026-01-02")
            assertThat(result["formattedInvalid"]).isEqualTo("")
            assertThat(result["emptyInput"]).isEqualTo("")
            assertThat(result["dateAndTimeIncludesTime"]).isEqualTo(true)
            assertThat(result["dateOnlyWithBaseIncludesTime"]).isEqualTo(true)
            assertThat(result["timeOnlyWithBaseIncludesTime"]).isEqualTo(true)
            assertThat(result["dateOnlyHasMidnight"]).isEqualTo(true)
            assertThat(result["timeOnlyIncludesTime"]).isEqualTo(true)
            assertThat(result["timeMissingMinutesDefaultsToZero"]).isEqualTo(true)
            assertThat(result["invalidBase"]).isEqualTo("")
        }
    }

    @Test
    fun `validation parser and apply helper handle varied backend payloads`() {
        withPage { page ->
            page.navigate("$frontendUrl/")

            @Suppress("UNCHECKED_CAST")
            val result = page.evaluate(
                """
                async () => {
                  const validation = await import('/src/plugins/validation.ts')

                  const errorObject = {
                    response: {
                      status: 400,
                      data: {
                        status: 400,
                        detail: 'validation failed',
                        traceId: 'trace-123',
                        errors: [
                          { objectName: 'CreateUserRequest', field: 'username', message: 'Username is taken.' },
                          { objectName: 'CreateUserRequest', field: 'username', message: 'Username format is invalid.' },
                          { objectName: 'CreateUserRequest', field: '', message: 'ignore me' },
                        ],
                      },
                    },
                  }

                  const parsed = validation.parseApiValidation(errorObject)
                  const noData = validation.parseApiValidation({ response: { status: 400 } })
                  const non400EmptyErrors = validation.parseApiValidation({
                    response: { status: 409, data: { status: 409, errors: [] } },
                  })
                  const empty400Errors = validation.parseApiValidation({
                    response: { status: 400, data: { status: 400, errors: [] } },
                  })
                  const unknown = validation.parseApiValidation({ foo: 'bar' })

                  const collected = {}
                  const applied = validation.apply(
                    {
                      setFieldError: (field, messages) => {
                        collected[field] = messages
                      },
                    },
                    errorObject
                  )
                  const applyUnknown = validation.apply(
                    {
                      setFieldError: () => {},
                    },
                    { foo: 'bar' }
                  )

                  return {
                    parsed,
                    noDataIsNull: noData === null,
                    non400EmptyErrorsIsNull: non400EmptyErrors === null,
                    empty400ErrorsIsNull: empty400Errors === null,
                    unknownIsNull: unknown === null,
                    isEmptyNull: validation.isEmpty(null),
                    isEmptyUndefined: validation.isEmpty(undefined),
                    isEmptyWhitespace: validation.isEmpty('   '),
                    isEmptyValue: validation.isEmpty('hello'),
                    applied,
                    applyUnknown,
                    collected,
                  }
                }
                """.trimIndent()
            ) as Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val parsed = result["parsed"] as Map<String, Any?>
            assertThat(parsed["objectName"]).isEqualTo("CreateUserRequest")
            assertThat(parsed["detail"]).isEqualTo("validation failed")
            assertThat(parsed["status"]).isEqualTo(400)
            assertThat(parsed["traceId"]).isEqualTo("trace-123")

            @Suppress("UNCHECKED_CAST")
            val fieldErrors = parsed["fieldErrors"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val usernameErrors = (fieldErrors["username"] as? List<Any?>).orEmpty().map { it.toString() }
            assertThat(usernameErrors).containsExactly("Username is taken.", "Username format is invalid.")

            assertThat(result["noDataIsNull"]).isEqualTo(true)
            assertThat(result["non400EmptyErrorsIsNull"]).isEqualTo(true)
            assertThat(result["empty400ErrorsIsNull"]).isEqualTo(true)
            assertThat(result["unknownIsNull"]).isEqualTo(true)
            assertThat(result["isEmptyNull"]).isEqualTo(true)
            assertThat(result["isEmptyUndefined"]).isEqualTo(true)
            assertThat(result["isEmptyWhitespace"]).isEqualTo(true)
            assertThat(result["isEmptyValue"]).isEqualTo(false)
            assertThat(result["applied"]).isEqualTo(true)
            assertThat(result["applyUnknown"]).isEqualTo(false)

            @Suppress("UNCHECKED_CAST")
            val collected = result["collected"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val appliedUsernameErrors = (collected["username"] as? List<Any?>).orEmpty().map { it.toString() }
            assertThat(appliedUsernameErrors).containsExactly("Username is taken.", "Username format is invalid.")
            assertThat(collected.keys).containsExactly("username")
        }
    }

    @Test
    fun `network error handler covers status and redirect branches`() {
        withPage { page ->
            page.navigate("$frontendUrl/events")
            page.waitForURL("**/events")

            @Suppress("UNCHECKED_CAST")
            val result = page.evaluate(
                """
                async () => {
                  const networkErrorModule = await import('/src/plugins/handleNetworkError.ts')
                  const { ['${'$'}handleNetworkError']: handleNetworkError } = networkErrorModule
                  const storeModule = await import('/src/plugins/store.ts')
                  const routerModule = await import('/src/plugins/router.ts')

                  const store = storeModule.default
                  const router = routerModule.default

                  const commits = []
                  const pushes = []

                  const originalCommit = store.commit.bind(store)
                  const originalPush = router.push.bind(router)

                  store.commit = (...args) => {
                    commits.push(args)
                    return originalCommit(...args)
                  }
                  router.push = (...args) => {
                    pushes.push(args[0])
                    return originalPush(...args)
                  }

                  const axiosError = (status) => ({
                    response: { status },
                    request: { url: '/api/test' },
                    message: 'status-' + status,
                  })

                  try {
                    handleNetworkError({ unexpected: true })
                    handleNetworkError(axiosError(400))
                    handleNetworkError(axiosError(401))
                    await new Promise((resolve) => setTimeout(resolve, 0))

                    handleNetworkError(axiosError(403))
                    await new Promise((resolve) => setTimeout(resolve, 0))
                    handleNetworkError(axiosError(404))
                    handleNetworkError(axiosError(408))
                    handleNetworkError(axiosError(409))
                    handleNetworkError(axiosError(413))
                    handleNetworkError(axiosError(500))
                    handleNetworkError(axiosError(502))
                    handleNetworkError(axiosError(418))

                    const messages = commits
                      .filter(([mutation]) => mutation === 'setStatusSnackbarMessage')
                      .map(([, payload]) => String(payload || ''))

                    return {
                      hasUnknownMessage: messages.some((message) => message.includes('An unknown error occurred')),
                      has400Message: messages.some((message) => message.includes('bad request')),
                      has404Message: messages.some((message) => message.includes('404 moment')),
                      has408Message: messages.some((message) => message.includes('request timeout')),
                      has409Message: messages.some((message) => message.includes('Woopsie daysies')),
                      has413Message: messages.some((message) => message.includes('file is too large')),
                      has500Message: messages.some((message) => message.includes('error code 500')),
                      has502Message: messages.some((message) => message.includes('error code 502')),
                      hasDefaultMessage: messages.some((message) => message.includes('error code 418')),
                    }
                  } finally {
                    store.commit = originalCommit
                    router.push = originalPush
                  }
                }
                """.trimIndent()
            ) as Map<String, Any?>

            assertThat(result["hasUnknownMessage"]).isEqualTo(true)
            assertThat(result["has400Message"]).isEqualTo(true)
            assertThat(result["has404Message"]).isEqualTo(true)
            assertThat(result["has408Message"]).isEqualTo(true)
            assertThat(result["has409Message"]).isEqualTo(true)
            assertThat(result["has413Message"]).isEqualTo(true)
            assertThat(result["has500Message"]).isEqualTo(true)
            assertThat(result["has502Message"]).isEqualTo(true)
            assertThat(result["hasDefaultMessage"]).isEqualTo(true)
        }
    }
}
