package net.blueshell.api.pinger.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PingerPaintRepository : JpaRepository<PingerPaint, Long>
