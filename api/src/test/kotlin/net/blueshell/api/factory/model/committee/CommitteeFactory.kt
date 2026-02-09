package net.blueshell.api.factory.model.committee

import com.github.javafaker.Faker
import net.blueshell.api.committee.domain.model.Committee
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Committee model test instances.
 */
@Component
class CommitteeFactory(
    private val faker: Faker
) {

    fun createBasic(): Committee {
        val committee = Committee()
        committee.name = faker.company().name() + " Committee"
        committee.description = faker.lorem().paragraph(3)
        committee.members = mutableListOf()
        return committee
    }

    fun createFull(): Committee = createBasic()

    fun createWithCustomizations(customizer: Consumer<Committee>): Committee {
        val committee = createFull()
        customizer.accept(committee)
        return committee
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
