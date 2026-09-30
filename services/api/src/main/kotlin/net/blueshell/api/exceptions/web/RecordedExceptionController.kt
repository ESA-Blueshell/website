package net.blueshell.api.exceptions.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.exceptions.domain.RecordedExceptions
import net.blueshell.api.security.AdminOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/management/exceptions")
@Tag(name = "Exceptions", description = "The api's recorded faults")
class RecordedExceptionController(
    private val faults: RecordedExceptions,
) {
    @GetMapping
    @AdminOnly
    fun listExceptions(
        @RequestParam(required = false) resolved: Boolean?,
    ): List<RecordedExceptionDTO> = faults.list(resolved).map { it.toDto(withTrace = false) }

    @GetMapping("/{id}")
    @AdminOnly
    fun findException(
        @PathVariable id: Long,
    ): RecordedExceptionDTO = faults.find(id).toDto(withTrace = true)

    @PostMapping("/{id}/resolve")
    @AdminOnly
    fun resolveException(
        @PathVariable id: Long,
    ): RecordedExceptionDTO = faults.resolve(id).toDto(withTrace = true)
}
