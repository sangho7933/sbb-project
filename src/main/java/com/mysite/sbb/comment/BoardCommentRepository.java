package com.mysite.sbb.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mysite.sbb.board.BoardPost;
import com.mysite.sbb.user.SiteUser;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Integer> {

	long countByPost(BoardPost post);

	long countByAuthor(SiteUser author);

	long countByDeleted(boolean deleted);

	Page<BoardComment> findByAuthor_UsernameContainingIgnoreCase(String username, Pageable pageable);

	java.util.List<BoardComment> findByAuthorOrderByCreateDateDesc(SiteUser author);

	java.util.List<BoardComment> findTop5ByAuthorOrderByCreateDateDesc(SiteUser author);
}
