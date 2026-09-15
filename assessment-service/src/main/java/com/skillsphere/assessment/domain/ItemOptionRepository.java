package com.skillsphere.assessment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemOptionRepository extends JpaRepository<ItemOption, Long> {

    List<ItemOption> findByItemIdOrderByPosition(Long itemId);

    List<ItemOption> findByItemIdInOrderByPosition(List<Long> itemIds);

    void deleteByItemId(Long itemId);
}
