package com.mysite.sbb.trade.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mysite.sbb.trade.entity.TradeTransaction;

public interface TradeTransactionRepository extends JpaRepository<TradeTransaction, Integer> {

	List<TradeTransaction> findByBuyer_UserId(Long userId);

	void deleteByItem_Id(Integer itemId);

	@Query("""
			select tradeTransaction
			from TradeTransaction tradeTransaction
			join fetch tradeTransaction.item item
			left join fetch item.catalogItem catalogItem
			where tradeTransaction.buyer.userId = :userId
			order by tradeTransaction.createdAt desc, tradeTransaction.id desc
			""")
	List<TradeTransaction> findPurchaseHistoryByBuyerUserId(@Param("userId") Long userId);

	@Query("""
			select tradeTransaction
			from TradeTransaction tradeTransaction
			join fetch tradeTransaction.item item
			left join fetch item.catalogItem catalogItem
			join fetch tradeTransaction.buyer buyer
			join fetch tradeTransaction.seller seller
			where tradeTransaction.seller.userId = :userId
			order by tradeTransaction.createdAt desc, tradeTransaction.id desc
			""")
	List<TradeTransaction> findSalesHistoryBySellerUserId(@Param("userId") Long userId);

	@Query("""
			select tradeTransaction
			from TradeTransaction tradeTransaction
			join fetch tradeTransaction.item item
			left join fetch item.catalogItem catalogItem
			join fetch tradeTransaction.buyer buyer
			join fetch tradeTransaction.seller seller
			where tradeTransaction.buyer.userId = :userId
			   or tradeTransaction.seller.userId = :userId
			order by tradeTransaction.createdAt desc, tradeTransaction.id desc
			""")
	List<TradeTransaction> findRelatedHistoryByUserId(@Param("userId") Long userId);

}
