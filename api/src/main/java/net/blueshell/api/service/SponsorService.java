package net.blueshell.api.service;

import jakarta.ws.rs.NotFoundException;
import net.blueshell.api.model.File;
import net.blueshell.api.model.Sponsor;
import net.blueshell.api.repository.SponsorRepository;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SponsorService extends BaseModelService<Sponsor, Long, SponsorRepository> {

    @Autowired
    public SponsorService(SponsorRepository repository) {
        super(repository);
    }

    public Sponsor findByPicture(File picture) {
        return repository.findByPicture(picture).orElseThrow(() -> new NotFoundException("Sponsor not found for picture: " + picture.getName()));
    }
}
