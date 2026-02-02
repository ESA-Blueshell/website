package net.blueshell.api.common.event.jpa

import lombok.Getter
import org.springframework.core.ResolvableType
import org.springframework.core.ResolvableTypeProvider

class PrePersistEvent<T>(@field:Getter private val source: T?) : ResolvableTypeProvider {
    override fun getResolvableType(): ResolvableType? {
        return ResolvableType.forClassWithGenerics(
            javaClass, ResolvableType.forInstance(getSource())
        )
    }
}