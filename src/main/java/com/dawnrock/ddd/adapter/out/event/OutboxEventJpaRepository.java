package com.dawnrock.ddd.adapter.out.event;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de Spring Data del outbox de eventos (#165). */
interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    /** Eventos pendientes (ni publicados ni fallidos), en el orden en que se guardaron. */
    List<OutboxEventEntity> findByPublishedAtIsNullAndFailedAtIsNullOrderByIdAsc(Limit limit);
}
