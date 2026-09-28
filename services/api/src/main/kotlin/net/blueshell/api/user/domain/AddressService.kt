package net.blueshell.api.user.domain

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.AddressRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AddressService(
    private val repository: AddressRepository,
) {
    // Read back after each write, so the columns the database fills are on the answer.
    @PersistenceContext
    private lateinit var em: EntityManager

    private fun written(row: Address): Address = repository.saveAndFlush(row).also(em::refresh)

    @Transactional(readOnly = true)
    fun findById(id: Long): Address =
        repository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found with id: $id")
        }

    @Transactional(readOnly = true)
    fun findAll(): List<Address> = repository.findAll()

    @Transactional
    fun update(address: Address): Address = written(address)
}
