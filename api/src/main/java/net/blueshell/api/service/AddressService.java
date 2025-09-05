package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Address;
import net.blueshell.api.repository.AddressRepository;
import org.springframework.stereotype.Service;

@Service
public class AddressService extends BaseModelService<Address, Long, AddressRepository> {

    public AddressService(AddressRepository repository) {
        super(repository);
    }
}