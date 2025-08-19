package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Committee;
import net.blueshell.api.repository.CommitteeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommitteeService extends BaseModelService<Committee, Long, CommitteeRepository> {

    @Autowired
    public CommitteeService(CommitteeRepository repository, ApplicationEventPublisher events) {
        super(repository, events);
    }

    public List<Committee> findALlByUserId(Long id) {
        return repository.findALlByMembersUserIdEquals(id);
    }
}
