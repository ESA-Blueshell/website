package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.controller.filter.MembershipFilter;
import net.blueshell.api.model.Membership;
import net.blueshell.api.repository.MemberRepository;
import net.blueshell.api.repository.spec.MembershipSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MembershipService extends BaseModelService<Membership, MemberRepository> {

    @Autowired
    public MembershipService(MemberRepository repository, ApplicationEventPublisher events) {
        super(repository);
    }

    public boolean existsByUserId(Long userId) {
        return repository.existsByUserId(userId);
    }

    public List<Membership> findByFilter(MembershipFilter filter) {
        if (filter == null) filter = new MembershipFilter();
        var spec = MembershipSpecifications.fromFilter(filter, getPrincipal());
        return repository.findAll(spec);
    }
}
