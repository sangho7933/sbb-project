package com.mysite.sbb.mypage.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mysite.sbb.mypage.entity.Item;

public interface ItemRepository extends JpaRepository<Item, Long> {

	List<Item> findTop80BySlotCodeOrderByPowerScoreDescNameAsc(String slotCode);

	List<Item> findTop80BySlotCodeAndNameContainingIgnoreCaseOrderByPowerScoreDescNameAsc(String slotCode, String keyword);

	List<Item> findBySlotCodeOrderByPowerScoreDescNameAsc(String slotCode);

	List<Item> findBySlotCodeAndNameContainingIgnoreCaseOrderByPowerScoreDescNameAsc(String slotCode, String keyword);

	List<Item> findBySlotCodeIsNullAndTypeIn(Collection<String> types);

	List<Item> findByTypeIn(Collection<String> types);

	List<Item> findBySlotCodeIsNotNullAndTradableTrue();

	List<Item> findByTradableTrueAndTypeIn(Collection<String> types);

	long countBySlotCodeIsNotNull();

	long countBySlotCode(String slotCode);

	Optional<Item> findFirstBySlotCodeOrderByEquipLevelAscPowerScoreAscIdAsc(String slotCode);

	Optional<Item> findFirstBySlotCodeOrderByPowerScoreDescIdAsc(String slotCode);

	@Query("select max(i.syncedAt) from Item i")
	LocalDateTime findLastSyncedAt();
}
