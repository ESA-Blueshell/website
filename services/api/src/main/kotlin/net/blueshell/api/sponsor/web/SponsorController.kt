package net.blueshell.api.sponsor.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.sponsor.domain.SponsorUseCases
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping
@Tag(name = "Sponsors")
@BoardOnly
class SponsorController(
    private val useCases: SponsorUseCases,
) {
    @GetMapping("/sponsors")
    fun findSponsors(): List<SponsorResponse> = useCases.all().map { it.asResponse() }

    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSponsor(
        @Valid @RequestBody request: CreateSponsorRequest,
    ): SponsorResponse = useCases.create(request.name, request.description).asResponse()

    @PutMapping(value = ["/sponsors/{id}"])
    fun updateSponsor(
        @PathVariable @Positive id: Long,
        @Valid @RequestBody request: UpdateSponsorRequest,
    ): SponsorResponse = useCases.update(id, request.name, request.description, request.version).asResponse()

    @GetMapping(value = ["/sponsors/{id}"])
    fun findSponsorById(
        @PathVariable id: Long,
    ): SponsorResponse = useCases.byId(id).asResponse()

    @DeleteMapping(value = ["/sponsors/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSponsorById(
        @PathVariable id: Long,
    ) {
        useCases.remove(id)
    }
}
