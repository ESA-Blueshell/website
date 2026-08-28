package net.blueshell.api.email.persistence

import net.blueshell.api.email.domain.EmailQuery
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import org.springframework.data.jpa.domain.Specification
import java.util.Locale

object EmailSpecifications {

    fun deliveryStatus(status: EmailDeliveryStatus?): Specification<Email> {
        if (status == null) return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.equal(root.get<EmailDeliveryStatus>("deliveryStatus"), status)
        }
    }

    fun emailType(emailType: String?): Specification<Email> {
        val value = emailType?.trim()?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
            ?: return Specification { _, _, cb -> cb.conjunction() }
        return Specification { root, _, cb ->
            cb.like(cb.lower(root.get<String>("emailType")), "%$value%")
        }
    }

    fun search(value: String?): Specification<Email> {
        val raw = value?.trim()?.takeIf { it.isNotBlank() }
            ?: return Specification { _, _, cb -> cb.conjunction() }
        val normalized = "%${raw.lowercase(Locale.getDefault())}%"
        return Specification { root, _, cb ->
            cb.or(
                cb.like(cb.lower(root.get<String>("recipientEmail")), normalized),
                cb.like(cb.lower(root.get<String>("subject")), normalized)
            )
        }
    }

    fun fromQuery(query: EmailQuery): Specification<Email> {
        var spec = Specification<Email> { _, _, cb -> cb.conjunction() }
        query.deliveryStatus?.let { spec = spec.and(deliveryStatus(it)) }
        query.emailType?.let { spec = spec.and(emailType(it)) }
        query.search?.let { spec = spec.and(search(it)) }
        return spec
    }
}
