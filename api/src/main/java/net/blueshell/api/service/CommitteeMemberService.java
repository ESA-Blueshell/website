package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.CommitteeMember;
import net.blueshell.api.repository.CommitteeMemberRepository;
import org.springframework.stereotype.Service;

@Service
public class CommitteeMemberService extends BaseModelService<CommitteeMember, CommitteeMemberRepository> {

    public CommitteeMemberService(CommitteeMemberRepository repository) {
        super(repository);
    }
}
