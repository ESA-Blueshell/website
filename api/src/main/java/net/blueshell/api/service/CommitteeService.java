package net.blueshell.api.service;

import jakarta.transaction.Transactional;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.Committee;
import net.blueshell.api.model.CommitteeMember;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.CommitteeRepository;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommitteeService extends BaseModelService<Committee, Long, CommitteeRepository> {

    private static UserService userService;

    @Autowired
    public CommitteeService(CommitteeRepository repository, UserService userService) {
        super(repository);
        this.userService = userService;
    }


    @Transactional
    public void createCommittee(Committee committee) {
        create(committee);
        for (CommitteeMember member : committee.getMembers()) {
            User user = member.getUser();
            userService.addRole(user, Role.COMMITTEE);
        }
    }

    public List<Committee> findALlByUserId(Long id) {
        return repository.findAllByUsersIdEquals(id);
    }
}
