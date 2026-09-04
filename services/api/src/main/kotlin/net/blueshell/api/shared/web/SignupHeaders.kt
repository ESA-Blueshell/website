package net.blueshell.api.shared.web

/**
 * The header a signup presents its continuation token in.
 *
 * In the kernel because three layers name it and none of them owns the other two: the
 * controller reads it, the CORS configuration admits it, and the rate limiter counts by
 * it. `security` may not reach `auth :: web`, so a constant on the controller would have
 * been copied into the filter and drifted from there.
 */
object SignupHeaders {
    const val SIGNUP_TOKEN = "X-Signup-Token"
}
