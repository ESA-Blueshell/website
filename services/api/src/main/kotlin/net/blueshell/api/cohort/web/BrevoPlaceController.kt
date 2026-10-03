package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.BrevoChoice
import net.blueshell.api.cohort.domain.BrevoPlace
import net.blueshell.api.cohort.domain.CohortBrevo
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.security.BoardOnly
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Schema(name = "BrevoPlaceRequest", description = "The list a committee's people are on, where it has none yet")
data class BrevoPlaceRequest(
    @param:Schema(description = "An existing list to link")
    val listId: String? = null,
    @param:Schema(description = "Make a new list, where no list is named")
    val createList: Boolean = false,
)

/** A committee's Brevo list, set by the board on the committee's form. */
@RestController
@Tag(name = "Brevo places", description = "A committee's Brevo list")
@BoardOnly
class BrevoPlaceController(
    private val brevo: CohortBrevo,
    @param:Value($$"${brevo.committees-folder:Committees}") private val committees: String,
) {
    @GetMapping("/management/committees/{id}/brevo")
    fun findCommitteeBrevo(
        @PathVariable id: Long,
    ): BrevoPlace = brevo.read(committee(id))

    @PutMapping("/management/committees/{id}/brevo")
    fun setCommitteeBrevo(
        @PathVariable id: Long,
        @RequestBody request: BrevoPlaceRequest,
    ): BrevoPlace = brevo.apply(committee(id), BrevoChoice(request.listId, request.createList), committees)

    private fun committee(id: Long) = "${CohortType.COMMITTEE_MEMBERS}:$id"
}
