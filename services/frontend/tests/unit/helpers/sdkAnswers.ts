/**
 * Answers for a mocked SDK call, typed from the call. hey-api's result is an Axios response union
 * that wants status, headers and config besides the body, so without these a test builds half of
 * one and casts it to never, which also stops the body being checked against the api's types.
 */
type SdkCall = (...args: never[]) => Promise<unknown>
type Result<F extends SdkCall> = Awaited<ReturnType<F>>
type Body<F extends SdkCall> = Extract<Result<F>, {error: undefined}>["data"]
type Refusal<F extends SdkCall> = Extract<Result<F>, {data: undefined}>["error"]

const envelope = {headers: {}, config: {headers: {}}}

/** What [call] answers when the api accepts, with [data] as the body and any [headers] it sent. */
export function answer<F extends SdkCall>(_call: F, data: Body<F>, headers: Record<string, string | string[]> = {}): Result<F> {
  return {...envelope, headers, status: 200, statusText: "OK", data, error: undefined} as unknown as Result<F>
}

/** What [call] answers when the api accepts but sends no body, which the types say cannot happen. */
export function emptyAnswer<F extends SdkCall>(_call: F): Result<F> {
  return {...envelope, status: 200, statusText: "OK", data: undefined, error: undefined} as unknown as Result<F>
}

/**
 * What [call] answers when the api refuses, with [error] as the body (null where there is none) and no data.
 * A refusal may carry a code and the facts its sentence reads, which the spec does not describe (api ADR-026),
 * and its [status] is on `response` as well, where Axios puts it.
 */
export function refusal<F extends SdkCall>(
  _call: F,
  error: (Refusal<F> & Record<string, unknown>) | null,
  status = 400,
): Result<F> {
  return {...envelope, status, response: {status, data: error}, data: undefined, error} as unknown as Result<F>
}
