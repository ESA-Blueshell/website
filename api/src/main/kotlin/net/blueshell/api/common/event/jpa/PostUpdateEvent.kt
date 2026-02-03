package net.blueshell.api.common.event.jpa

import org.springframework.core.ResolvableType
import org.springframework.core.ResolvableTypeProvider

class PostUpdateEvent<T>(val source: T) : ResolvableTypeProvider {
    override fun getResolvableType(): ResolvableType? {
        return ResolvableType.forClassWithGenerics(
            javaClass, ResolvableType.forInstance(source)
        )
    }
}