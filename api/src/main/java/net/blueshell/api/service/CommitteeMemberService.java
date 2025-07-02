package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.dto.CommitteeMemberDTO;
import net.blueshell.api.common.exception.ResourceNotFoundException;
import net.blueshell.api.mapper.CommitteeMemberMapper;
import net.blueshell.api.model.CommitteeMember;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.CommitteeMemberRepository;
import net.blueshell.api.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommitteeMemberService extends BaseModelService<CommitteeMember, Long, CommitteeMemberRepository>  {

    public CommitteeMemberService(CommitteeMemberRepository repository,
                                  ApplicationEventPublisher events) {
        super(repository, events);
    }
}
