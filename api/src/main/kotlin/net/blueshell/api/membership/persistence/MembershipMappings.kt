package net.blueshell.api.membership.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.membership.web.dto.MembershipDTO
import net.blueshell.api.membership.web.dto.MembershipKonverter

private val membershipKonverter = Konverter.get<MembershipKonverter>()

fun Membership.asDto(): MembershipDTO = membershipKonverter.toDTO(this)
