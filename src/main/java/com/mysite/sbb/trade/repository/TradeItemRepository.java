package com.mysite.sbb.trade.repository;

import java.util.List; 

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mysite.sbb.trade.entity.TradeItem;

import jakarta.persistence.LockModeType;

public interface TradeItemRepository extends JpaRepository<TradeItem, Integer> {

	@Query(value = "select ti from TradeItem ti "
			+ "where (:category = '' or ti.category = :category) "
			+ "and (:kw = '' or lower(ti.title) like lower(concat('%', :kw, '%'))) "
			+ "and ti.hidden = false",
			countQuery = "select count(ti) from TradeItem ti "
					+ "where (:category = '' or ti.category = :category) "
					+ "and (:kw = '' or lower(ti.title) like lower(concat('%', :kw, '%'))) "
					+ "and ti.hidden = false")
	Page<TradeItem> search(@Param("category") String category, @Param("kw") String kw, Pageable pageable);

	@Query("select distinct ti.category from TradeItem ti where ti.category is not null and ti.category <> '' order by ti.category asc")
	List<String> findDistinctCategories();

	@EntityGraph(attributePaths = { "seller", "catalogItem" })
	@Query("select ti from TradeItem ti where ti.id = :id and ti.hidden = false")
	Optional<TradeItem> findDetailById(@Param("id") Integer id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = { "seller", "catalogItem" })
	@Query("select ti from TradeItem ti where ti.id = :id and ti.hidden = false")
	Optional<TradeItem> findLockedById(@Param("id") Integer id);

	Page<TradeItem> findByStatusAndHiddenFalse(String status, Pageable pageable);

	long countByStatus(String status);

	long countByHidden(boolean hidden);

	Page<TradeItem> findBySeller_UserIdAndHiddenFalse(Long userId, Pageable pageable);

	Page<TradeItem> findBySeller_UsernameContainingIgnoreCase(String username, Pageable pageable);

	List<TradeItem> findBySeller_UserIdOrderByIdDesc(Long userId);

	List<TradeItem> findTop10BySeller_UserIdAndHiddenFalseOrderByIdDesc(Long userId);
}
