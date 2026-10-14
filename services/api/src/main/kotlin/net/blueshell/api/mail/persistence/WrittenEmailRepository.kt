package net.blueshell.api.mail.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WrittenEmailRepository : JpaRepository<WrittenEmail, Long>
