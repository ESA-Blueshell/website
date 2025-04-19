package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.EventFeedback;
import org.springframework.stereotype.Repository;

@Repository
public interface EventFeedbackRepository extends BaseRepository<EventFeedback, Long> {
}
