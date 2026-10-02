package net.blueshell.api.user.domain.sealing

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate
import java.util.Base64

/**
 * Seals through Vault Transit, so the key never reaches the api and Vault's audit log records
 * every opening. Each key is a derived key: the context of each value goes along with it.
 */
@Component
@ConditionalOnProperty("privacy.sealing", havingValue = "vault")
class VaultTransitSealer(
    private val vault: VaultTemplate,
) : Sealer {
    override fun seal(
        key: String,
        values: List<Sealed>,
    ): List<String> =
        batch("transit/encrypt/$key", values, "ciphertext") { mapOf("plaintext" to encode(it.value), "context" to encode(it.context)) }

    override fun open(
        key: String,
        values: List<Sealed>,
    ): List<String> =
        batch("transit/decrypt/$key", values, "plaintext") { mapOf("ciphertext" to it.value, "context" to encode(it.context)) }
            .map { String(Base64.getDecoder().decode(it)) }

    private fun batch(
        path: String,
        values: List<Sealed>,
        field: String,
        item: (Sealed) -> Map<String, String>,
    ): List<String> {
        if (values.isEmpty()) return emptyList()
        val response =
            try {
                vault.write(path, mapOf("batch_input" to values.map(item)))
            } catch (refused: RuntimeException) {
                throw SealingUnavailable(refused)
            }

        @Suppress("UNCHECKED_CAST")
        val results = response?.data?.get("batch_results") as? List<Map<String, Any?>> ?: throw SealingUnavailable()
        return results.map { answerOf(it, field) }
    }

    private fun answerOf(
        result: Map<String, Any?>,
        field: String,
    ): String {
        if (result["error"] != null) throw SealedValueUnopenable(IllegalStateException(result["error"].toString()))
        return result[field]?.toString() ?: throw SealingUnavailable()
    }

    private fun encode(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())
}
