package com.mysite.sbb.skilltree.comment;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mysite.sbb.DataNotFoundException;
import com.mysite.sbb.skilltree.SkillTreePost;
import com.mysite.sbb.user.SiteUser;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class SkillTreeCommentService {

	private final SkillTreeCommentRepository skillTreeCommentRepository;

	public SkillTreeComment create(SkillTreePost post, String content, SiteUser author) {
		return create(post, content, author, null);
	}

	public SkillTreeComment create(SkillTreePost post, String content, SiteUser author, SkillTreeComment parentComment) {
		SkillTreeComment comment = new SkillTreeComment();
		comment.setContent(content);
		comment.setCreateDate(LocalDateTime.now());
		comment.setPost(post);
		comment.setAuthor(author);
		comment.setParentComment(parentComment);
		this.skillTreeCommentRepository.save(comment);
		return comment;
	}

	public SkillTreeComment getComment(Integer id) {
		Optional<SkillTreeComment> comment = this.skillTreeCommentRepository.findById(id);
		if (comment.isPresent()) {
			return comment.get();
		}
		throw new DataNotFoundException("skilltree comment not found");
	}

	public java.util.List<SkillTreeComment> getCommentsByPost(SkillTreePost post) {
		return this.skillTreeCommentRepository.findByPostOrderByCreateDateAsc(post);
	}

	public void modify(SkillTreeComment comment, String content) {
		if (comment.isDeleted()) {
			throw new IllegalStateException("삭제된 댓글은 수정할 수 없습니다.");
		}
		comment.setContent(content);
		comment.setModifyDate(LocalDateTime.now());
		this.skillTreeCommentRepository.save(comment);
	}

	public void delete(SkillTreeComment comment) {
		if (hasChildComments(comment)) {
			applySoftDelete(comment);
			return;
		}
		this.skillTreeCommentRepository.delete(comment);
	}

	public void toggleLike(SkillTreeComment comment, SiteUser siteUser) {
		if (comment.isDeleted()) {
			return;
		}
		toggleReaction(comment, siteUser, true);
	}

	public void toggleDislike(SkillTreeComment comment, SiteUser siteUser) {
		if (comment.isDeleted()) {
			return;
		}
		toggleReaction(comment, siteUser, false);
	}

	private boolean hasChildComments(SkillTreeComment comment) {
		return comment.getChildComments() != null && !comment.getChildComments().isEmpty();
	}

	private void applySoftDelete(SkillTreeComment comment) {
		comment.setDeleted(true);
		comment.setContent("삭제된 댓글입니다.");
		comment.setModifyDate(LocalDateTime.now());
		clearReactionVotes(comment);
		this.skillTreeCommentRepository.save(comment);
	}

	private void clearReactionVotes(SkillTreeComment comment) {
		ensureVoterSet(comment).clear();
		ensureDislikeVoterSet(comment).clear();
	}

	private void toggleReaction(SkillTreeComment comment, SiteUser siteUser, boolean likeAction) {
		Set<SiteUser> targetVoters = likeAction ? ensureVoterSet(comment) : ensureDislikeVoterSet(comment);
		Set<SiteUser> oppositeVoters = likeAction ? ensureDislikeVoterSet(comment) : ensureVoterSet(comment);
		if (containsUser(targetVoters, siteUser)) {
			removeUser(targetVoters, siteUser);
		} else {
			targetVoters.add(siteUser);
			removeUser(oppositeVoters, siteUser);
		}
		this.skillTreeCommentRepository.save(comment);
	}

	private Set<SiteUser> ensureVoterSet(SkillTreeComment comment) {
		if (comment.getVoter() == null) {
			comment.setVoter(new HashSet<>());
		}
		return comment.getVoter();
	}

	private Set<SiteUser> ensureDislikeVoterSet(SkillTreeComment comment) {
		if (comment.getDislikeVoter() == null) {
			comment.setDislikeVoter(new HashSet<>());
		}
		return comment.getDislikeVoter();
	}

	private boolean containsUser(Set<SiteUser> voters, SiteUser siteUser) {
		return voters.stream().anyMatch(user -> user.getUsername().equals(siteUser.getUsername()));
	}

	private void removeUser(Set<SiteUser> voters, SiteUser siteUser) {
		voters.removeIf(user -> user.getUsername().equals(siteUser.getUsername()));
	}
}
