package com.mysite.sbb.skilltree.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mysite.sbb.skilltree.SkillTreePost;
import com.mysite.sbb.user.SiteUser;

public interface SkillTreeCommentRepository extends JpaRepository<SkillTreeComment, Integer> {

	long countByPost(SkillTreePost post);

	java.util.List<SkillTreeComment> findByPostOrderByCreateDateAsc(SkillTreePost post);

	long countByAuthor(SiteUser author);

	java.util.List<SkillTreeComment> findTop5ByAuthorOrderByCreateDateDesc(SiteUser author);
}
