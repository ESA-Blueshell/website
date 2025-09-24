package net.blueshell.api.service;

import jakarta.ws.rs.NotFoundException;
import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.File;
import net.blueshell.api.model.Membership;
import net.blueshell.api.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService extends BaseModelService<Membership, MemberRepository> {

    @Autowired
    public MembershipService(MemberRepository repository, ApplicationEventPublisher events) {
        super(repository);
    }

    @Transactional(readOnly = true)
    public Membership findBySignature(File signature) {
        return repository.findBySignature(signature).orElseThrow(() ->
                new NotFoundException("Member not found for signature: " + signature.getName()));
    }

    public boolean existsByUserId(Long userId) {
        return repository.existsByUserId(userId);
    }
}
