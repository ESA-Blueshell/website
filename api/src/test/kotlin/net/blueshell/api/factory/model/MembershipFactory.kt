package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.persistence.User
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Membership model test instances.
 */
@Component
class MembershipFactory(
    private val faker: Faker,
    private val userFactory: UserFactory
) {

    fun createBasic(user: User): net.blueshell.api.domain.membership.persistence.Membership {
        val membership = _root_ide_package_.net.blueshell.api.domain.membership.persistence.Membership()
        membership.user = user
        membership.startDate = LocalDate.now().minusMonths(6)
        membership.memberType = faker.options().option(MemberType::class.java)
        membership.incasso = faker.bool().bool()
        return membership
    }

    fun createFull(user: User): net.blueshell.api.domain.membership.persistence.Membership {
        val membership = createBasic(user)
        if (faker.bool().bool()) {
            membership.endDate = LocalDate.now().plusMonths(6)
        }
        return membership
    }

    fun createWithCustomizations(user: User, customizer: Consumer<net.blueshell.api.domain.membership.persistence.Membership>): net.blueshell.api.domain.membership.persistence.Membership {
        val membership = createFull(user)
        customizer.accept(membership)
        return membership
    }

    fun createActive(user: User): net.blueshell.api.domain.membership.persistence.Membership {
        return createWithCustomizations(user) { membership ->
            membership.startDate = LocalDate.now().minusMonths(3)
            membership.endDate = null
        }
    }

    fun createExpired(user: User): net.blueshell.api.domain.membership.persistence.Membership {
        return createWithCustomizations(user) { membership ->
            membership.startDate = LocalDate.now().minusYears(2)
            membership.endDate = LocalDate.now().minusYears(1)
        }
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
