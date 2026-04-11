package com.mysite.sbb.board;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mysite.sbb.user.SiteUser;

public interface BoardPostRepository extends JpaRepository<BoardPost, Integer> {

	Page<BoardPost> findByCategory(BoardCategory category, Pageable pageable);

	List<BoardPost> findByCategory(BoardCategory category, Sort sort);

	long countByCategory(BoardCategory category);

	long countByAuthor(SiteUser author);

	Page<BoardPost> findByAuthor_UsernameContainingIgnoreCase(String username, Pageable pageable);

	List<BoardPost> findByAuthorOrderByCreateDateDesc(SiteUser author);

	List<BoardPost> findTop5ByAuthorOrderByCreateDateDesc(SiteUser author);

	@Query("select distinct p "
			+ "from BoardPost p "
			+ "left outer join SiteUser author on p.author = author "
			+ "left outer join BoardComment comment on comment.post = p "
			+ "left outer join SiteUser commentAuthor on comment.author = commentAuthor "
			+ "where p.category = :category "
			+ "and ("
			+ "p.subject like %:kw% "
			+ "or p.content like %:kw% "
			+ "or author.username like %:kw% "
			+ "or comment.content like %:kw% "
			+ "or commentAuthor.username like %:kw%)")
	Page<BoardPost> findAllByCategoryAndKeyword(@Param("category") BoardCategory category, @Param("kw") String kw,
			Pageable pageable);

	@Modifying
	@Query("update BoardPost p set p.category = :category where p.category is null")
	int assignCategoryToUncategorized(@Param("category") BoardCategory category);
}
