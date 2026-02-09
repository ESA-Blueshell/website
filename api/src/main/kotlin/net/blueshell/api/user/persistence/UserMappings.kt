package net.blueshell.api.user.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.user.web.dto.AdvancedUserDTO
import net.blueshell.api.user.web.dto.AdvancedUserKonverter
import net.blueshell.api.user.web.dto.SimpleUserDTO
import net.blueshell.api.user.web.dto.SimpleUserKonverter

private val advancedUserKonverter = Konverter.get<AdvancedUserKonverter>()
private val simpleUserKonverter = Konverter.get<SimpleUserKonverter>()

fun User.asAdvancedDto(): AdvancedUserDTO = advancedUserKonverter.toDTO(this)

fun User.asSimpleDto(): SimpleUserDTO = simpleUserKonverter.toDTO(this)
