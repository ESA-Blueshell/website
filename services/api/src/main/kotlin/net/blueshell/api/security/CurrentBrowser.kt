package net.blueshell.api.security

import org.springframework.http.HttpHeaders
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/** The browser the request being served came from, or null outside a request. */
object CurrentBrowser {
    fun get(): Browser? {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return null
        return Browser.of(request.getHeader(HttpHeaders.USER_AGENT))
    }
}
