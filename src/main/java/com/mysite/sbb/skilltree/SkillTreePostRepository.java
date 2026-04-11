package com.mysite.sbb.skilltree;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mysite.sbb.user.SiteUser;

public interface SkillTreePostRepository extends JpaRepository<SkillTreePost, Integer> {

	Page<SkillTreePost> findByJob(SkillTreeJob job, Pageable pageable);

	List<SkillTreePost> findByJob(SkillTreeJob job, Sort sort);

	long countByJob(SkillTreeJob job);

	long countByAuthor(SiteUser author);

	List<SkillTreePost> findTop5ByAuthorOrderByCreateDateDesc(SiteUser author);

	@Query("select distinct p "
			+ "from SkillTreePost p "
			+ "left outer join SiteUser author on p.author = author "
			+ "left outer join SkillTreeComment comment on comment.post = p "
			+ "left outer join SiteUser commentAuthor on comment.author = commentAuthor "
			+ "where p.job = :job "
			+ "and ("
			+ "p.subject like %:kw% "
			+ "or p.content like %:kw% "
			+ "or author.username like %:kw% "
			+ "or comment.content like %:kw% "
			+ "or commentAuthor.username like %:kw%)")
	Page<SkillTreePost> findAllByJobAndKeyword(@Param("job") SkillTreeJob job, @Param("kw") String kw, Pageable pageable);
}
