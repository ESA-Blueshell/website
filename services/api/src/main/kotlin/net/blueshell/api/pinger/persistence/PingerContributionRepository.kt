package net.blueshell.api.pinger.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PingerContributionRepository : JpaRepository<PingerContribution, Long>
