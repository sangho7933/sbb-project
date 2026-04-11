package com.mysite.sbb.comment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.mysite.sbb.DataNotFoundException;
import com.mysite.sbb.board.BoardPost;
import com.mysite.sbb.user.SiteUser;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class BoardCommentService {

	private final BoardCommentRepository boardCommentRepository;

	public BoardComment create(BoardPost post, String content, SiteUser author) {
		return create(post, content, author, null);
	}

	public BoardComment create(BoardPost post, String content, SiteUser author, BoardComment parentComment) {
		BoardComment comment = new BoardComment();
		comment.setContent(content);
		comment.setCreateDate(LocalDateTime.now());
		comment.setPost(post);
		comment.setAuthor(author);
		comment.setParentComment(parentComment);
		ensureCommentList(post).add(comment);
		if (parentComment != null) {
			ensureChildCommentList(parentComment).add(comment);
		}
		this.boardCommentRepository.save(comment);
		return comment;
	}

	public BoardComment getComment(Integer id) {
		Optional<BoardComment> comment = this.boardCommentRepository.findById(id);
		if (comment.isPresent()) {
			return comment.get();
		}
		throw new DataNotFoundException("comment not found");
	}

	public void modify(BoardComment comment, String content) {
		if (comment.isDeleted()) {
			throw new IllegalStateException("Deleted comments cannot be edited.");
		}
		comment.setContent(content);
		comment.setModifyDate(LocalDateTime.now());
		this.boardCommentRepository.save(comment);
	}

	public void delete(BoardComment comment) {
		delete(comment, false);
	}

	public void deleteByAdmin(BoardComment comment) {
		delete(comment, true);
	}

	private void delete(BoardComment comment, boolean deletedByAdmin) {
		if (hasChildComments(comment)) {
			applySoftDelete(comment, deletedByAdmin);
			this.boardCommentRepository.save(comment);
			return;
		}
		this.boardCommentRepository.delete(comment);
	}

	public void toggleLike(BoardComment comment, SiteUser siteUser) {
		toggleReaction(comment, siteUser, true);
	}

	public void toggleDislike(BoardComment comment, SiteUser siteUser) {
		toggleReaction(comment, siteUser, false);
	}

	public Page<BoardComment> getAdminList(int page, int size) {
		return getAdminList(page, size, "");
	}

	public Page<BoardComment> getAdminList(int page, int size, String kw) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(size, 1);
		PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createDate")));
		String keyword = kw == null ? "" : kw.trim();
		if (keyword.isBlank()) {
			return this.boardCommentRepository.findAll(pageable);
		}
		return this.boardCommentRepository.findByAuthor_UsernameContainingIgnoreCase(keyword, pageable);
	}

	public List<BoardComment> getCommentsByAuthor(SiteUser author) {
		return this.boardCommentRepository.findByAuthorOrderByCreateDateDesc(author);
	}

	private List<BoardComment> ensureCommentList(BoardPost post) {
		if (post.getCommentList() == null) {
			post.setCommentList(new ArrayList<>());
		}
		return post.getCommentList();
	}

	private List<BoardComment> ensureChildCommentList(BoardComment parentComment) {
		if (parentComment.getChildComments() == null) {
			parentComment.setChildComments(new ArrayList<>());
		}
		return parentComment.getChildComments();
	}

	private boolean hasChildComments(BoardComment comment) {
		return comment.getChildComments() != null && !comment.getChildComments().isEmpty();
	}

	private void applySoftDelete(BoardComment comment, boolean deletedByAdmin) {
		comment.setDeleted(true);
		comment.setContent(deletedByAdmin ? "운영자에 의해 삭제된 댓글입니다." : "삭제된 댓글입니다.");
		comment.setModifyDate(LocalDateTime.now());
		clearReactionVotes(comment);
	}

	private void clearReactionVotes(BoardComment comment) {
		comment.setVoter(ensureVoterSet(comment.getVoter()));
		comment.setDislikeVoter(ensureVoterSet(comment.getDislikeVoter()));
		comment.getVoter().clear();
		comment.getDislikeVoter().clear();
	}

	private void toggleReaction(BoardComment comment, SiteUser siteUser, boolean likeReaction) {
		if (comment.isDeleted()) {
			return;
		}
		comment.setVoter(ensureVoterSet(comment.getVoter()));
		comment.setDislikeVoter(ensureVoterSet(comment.getDislikeVoter()));

		Set<SiteUser> targetVoters = likeReaction ? comment.getVoter() : comment.getDislikeVoter();
		Set<SiteUser> oppositeVoters = likeReaction ? comment.getDislikeVoter() : comment.getVoter();

		if (containsUser(targetVoters, siteUser)) {
			removeUser(targetVoters, siteUser);
		} else {
			targetVoters.add(siteUser);
			removeUser(oppositeVoters, siteUser);
		}
		this.boardCommentRepository.save(comment);
	}

	private Set<SiteUser> ensureVoterSet(Set<SiteUser> voters) {
		return voters == null ? new HashSet<>() : voters;
	}

	private boolean containsUser(Set<SiteUser> voters, SiteUser siteUser) {
		return voters.stream().anyMatch(user -> user.getUsername().equals(siteUser.getUsername()));
	}

	private void removeUser(Set<SiteUser> voters, SiteUser siteUser) {
		voters.removeIf(user -> user.getUsername().equals(siteUser.getUsername()));
	}
}
