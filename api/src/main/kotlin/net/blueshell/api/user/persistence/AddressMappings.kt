package net.blueshell.api.user.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.user.web.dto.AddressDTO
import net.blueshell.api.user.web.dto.AddressKonverter

private val addressKonverter = Konverter.get<AddressKonverter>()

fun Address.asDto(): AddressDTO = addressKonverter.toDTO(this)
