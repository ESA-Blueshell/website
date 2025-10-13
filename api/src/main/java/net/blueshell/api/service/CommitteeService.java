package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.repository.committee.CommitteeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommitteeService extends BaseModelService<Committee, CommitteeRepository> {

    @Autowired
    public CommitteeService(CommitteeRepository repository, ApplicationEventPublisher events) {
        super(repository);
    }

    public List<Committee> findAllByUserId(Long id) {
        return repository.findALlByMembersUserIdEquals(id);
    }
}
