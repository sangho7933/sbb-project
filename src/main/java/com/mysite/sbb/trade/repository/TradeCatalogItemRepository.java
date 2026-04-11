package com.mysite.sbb.trade.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mysite.sbb.trade.entity.TradeCatalogItem;

public interface TradeCatalogItemRepository extends JpaRepository<TradeCatalogItem, Long> {

	List<TradeCatalogItem> findAllByOrderByCategoryAscDisplayOrderAsc();

	List<TradeCatalogItem> findByCategoryOrderByDisplayOrderAsc(String category);

	Optional<TradeCatalogItem> findByCategoryAndItemName(String category, String itemName);

	boolean existsByCategoryAndItemName(String category, String itemName);

	Optional<TradeCatalogItem> findTopByOrderByDisplayOrderDesc();

	@Query("select t.category from TradeCatalogItem t group by t.category order by min(t.displayOrder), t.category asc")
	List<String> findDistinctCategories();
}
