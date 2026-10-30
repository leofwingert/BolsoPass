package com.bolsopass.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, String> {
  List<Event> findByStatusInOrderByStartsAt(Collection<Event.Status> statuses);

  List<Event> findByOrganizerIdOrderByStartsAt(String organizerId);

  List<Event> findAllByOrderByStartsAt();
}
