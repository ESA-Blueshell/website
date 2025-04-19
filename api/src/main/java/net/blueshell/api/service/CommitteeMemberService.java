package net.blueshell.api.service;

import net.blueshell.api.dto.CommitteeMemberDTO;
import net.blueshell.api.common.exception.ResourceNotFoundException;
import net.blueshell.api.mapper.CommitteeMemberMapper;
import net.blueshell.api.model.CommitteeMember;
import net.blueshell.api.repository.CommitteeMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommitteeMemberService {

    private final CommitteeMemberRepository committeeMemberRepository;
    private final CommitteeMemberMapper committeeMemberMapper;

    public CommitteeMemberService(CommitteeMemberRepository committeeMemberRepository,
                                  CommitteeMemberMapper committeeMemberMapper) {
        this.committeeMemberRepository = committeeMemberRepository;
        this.committeeMemberMapper = committeeMemberMapper;
    }

    /**
     * Creates a new CommitteeMember.
     *
     * @param dto         CommitteeMemberDTO instance.
     * @return Created CommitteeMember entity.
     */
    @Transactional
    public CommitteeMember createCommitteeMember(CommitteeMemberDTO dto) {
        CommitteeMember member = committeeMemberMapper.fromDTO(dto);
        return committeeMemberRepository.save(member);
    }

    /**
     * Retrieves all CommitteeMembers.
     *
     * @return List of CommitteeMember entities.
     */
    public List<CommitteeMember> findAll() {
        return committeeMemberRepository.findAll();
    }

    /**
     * Retrieves a CommitteeMember by ID.
     *
     * @param id CommitteeMember ID.
     * @return CommitteeMember entity.
     */
    public CommitteeMember findById(Long id) {
        return committeeMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommitteeMember not found for user id: " + id));
    }

    /**
     * Updates an existing CommitteeMember.
     *
     * @param dto CommitteeMemberDTO instance with updated data.
     * @return Updated CommitteeMember entity.
     */
    @Transactional
    public CommitteeMember update(CommitteeMemberDTO dto) {
        CommitteeMember updatedMember = committeeMemberMapper.fromDTO(dto);
        return committeeMemberRepository.save(updatedMember);
    }

    /**
     * Deletes a CommitteeMember by ID.
     *
     * @param id CommitteeMember ID.
     */
    @Transactional
    public void delete(Long id) {
        CommitteeMember member = findById(id);
        committeeMemberRepository.delete(member);
    }
}
