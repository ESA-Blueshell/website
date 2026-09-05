package net.blueshell.api.platform.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * A listing answers unpaged when nobody asked for a page, and answers the page anybody did ask
 * for, whichever half of it they named.
 *
 * Spring's resolver reads a fallback when the request does not carry **both** `page` and `size`,
 * and this api's fallback is unpaged — so `?size=500` was answered with the whole table. A caller
 * naming a size is one that cannot hold an unbounded answer, and it was handed exactly that,
 * silently. See #1145.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
internal class PagingConfig : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(EitherHalfMeansPaged())
    }

    private class EitherHalfMeansPaged : PageableHandlerMethodArgumentResolver() {
        override fun getPageable(parameter: MethodParameter, pageString: String?, sizeString: String?): Pageable =
            if (pageString == null && sizeString == null) {
                Pageable.unpaged()
            } else {
                // The half that was named decides; the other takes the value the api documents.
                super.getPageable(parameter, pageString ?: "0", sizeString ?: DEFAULT_PAGE_SIZE.toString())
            }
    }

    private companion object {
        /** What the OpenAPI document says `size` defaults to, so the spec and the api agree. */
        const val DEFAULT_PAGE_SIZE = 20
    }
}
