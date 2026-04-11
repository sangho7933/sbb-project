package com.mysite.sbb.board;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mysite.sbb.DataNotFoundException;
import com.mysite.sbb.comment.BoardCommentRepository;
import com.mysite.sbb.user.SiteUser;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class BoardPostService {

	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;

	public Page<BoardPost> getList(BoardCategory category, int page, String kw) {
		Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Order.desc("createDate")));
		return hydratePageMetrics(this.boardPostRepository.findAllByCategoryAndKeyword(category, kw, pageable));
	}

	public BoardPost getPost(Integer id) {
		Optional<BoardPost> post = this.boardPostRepository.findById(id);
		if (post.isPresent()) {
			BoardPost boardPost = post.get();
			hydrateMetrics(boardPost);
			return boardPost;
		}
		throw new DataNotFoundException("board post not found");
	}

	public BoardPost increaseViewCount(Integer id) {
		BoardPost post = getPost(id);
		post.setViewCount(safeViewCount(post) + 1);
		this.boardPostRepository.save(post);
		hydrateMetrics(post);
		return post;
	}

	public BoardPost create(BoardCategory category, String subject, String content, SiteUser user) {
		return create(category, subject, content, user, null, null);
	}

	public BoardPost create(BoardCategory category, String subject, String content, SiteUser user, StoredBoardMedia media) {
		return create(category, subject, content, user, media, null);
	}

	public BoardPost create(BoardCategory category, String subject, String content, SiteUser user, StoredBoardMedia media,
			String youtubeUrl) {
		BoardPost post = new BoardPost();
		post.setCategory(category);
		post.setSubject(subject);
		post.setContent(content);
		post.setYoutubeUrl(normalizeYoutubeUrl(youtubeUrl));
		post.setViewCount(0);
		post.setCreateDate(LocalDateTime.now());
		post.setAuthor(user);
		applyMedia(post, media);
		return this.boardPostRepository.save(post);
	}

	public void modify(BoardPost post, String subject, String content) {
		modify(post, subject, content, null, null, false);
	}

	public void modify(BoardPost post, String subject, String content, StoredBoardMedia media, boolean removeMedia) {
		modify(post, subject, content, null, media, removeMedia);
	}

	public void modify(BoardPost post, String subject, String content, String youtubeUrl, StoredBoardMedia media,
			boolean removeMedia) {
		post.setSubject(subject);
		post.setContent(content);
		post.setYoutubeUrl(normalizeYoutubeUrl(youtubeUrl));
		post.setModifyDate(LocalDateTime.now());
		if (removeMedia) {
			clearMedia(post);
		}
		if (media != null) {
			applyMedia(post, media);
		}
		this.boardPostRepository.save(post);
	}

	public void delete(BoardPost post) {
		this.boardPostRepository.delete(post);
	}

	public void toggleLike(BoardPost post, SiteUser siteUser) {
		toggleReaction(post, siteUser, true);
	}

	public void toggleDislike(BoardPost post, SiteUser siteUser) {
		toggleReaction(post, siteUser, false);
	}

	public List<BoardPost> getLatestPosts(BoardCategory category, int limit) {
		Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Order.desc("createDate")));
		return hydrateListMetrics(this.boardPostRepository.findByCategory(category, pageable).getContent());
	}

	public List<BoardPost> getPopularPosts(BoardCategory category, int limit) {
		List<BoardPost> posts = hydrateListMetrics(this.boardPostRepository.findByCategory(category,
				Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createDate"))));
		return posts.stream()
				.sorted(popularPostComparator())
				.limit(limit)
				.toList();
	}

	public List<BoardPost> getTopViewedPosts(BoardCategory category, int limit) {
		return hydrateListMetrics(this.boardPostRepository.findByCategory(category,
				Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createDate")))).stream()
				.limit(limit)
				.toList();
	}

	public Page<BoardPost> getAdminList(int page, int size) {
		return getAdminList(page, size, "");
	}

	public Page<BoardPost> getAdminList(int page, int size, String kw) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.max(size, 1);
		PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createDate")));
		String keyword = kw == null ? "" : kw.trim();
		return hydratePageMetrics(keyword.isBlank()
				? this.boardPostRepository.findAll(pageable)
				: this.boardPostRepository.findByAuthor_UsernameContainingIgnoreCase(keyword, pageable));
	}

	public List<BoardPost> getPostsByAuthor(SiteUser author) {
		return hydrateListMetrics(this.boardPostRepository.findByAuthorOrderByCreateDateDesc(author));
	}

	@Transactional
	public void assignLegacyPostsToFreeBoard() {
		this.boardPostRepository.assignCategoryToUncategorized(BoardCategory.FREE_BOARD);
	}

	private void hydrateMetrics(BoardPost post) {
		post.setCommentCount(this.boardCommentRepository.countByPost(post));
	}

	private Page<BoardPost> hydratePageMetrics(Page<BoardPost> posts) {
		posts.forEach(this::hydrateMetrics);
		return posts;
	}

	private List<BoardPost> hydrateListMetrics(List<BoardPost> posts) {
		posts.forEach(this::hydrateMetrics);
		return posts;
	}

	private void applyMedia(BoardPost post, StoredBoardMedia media) {
		if (media == null) {
			return;
		}
		post.setMediaPath(media.path());
		post.setMediaOriginalName(media.originalName());
		post.setMediaContentType(media.contentType());
	}

	private void clearMedia(BoardPost post) {
		post.setMediaPath(null);
		post.setMediaOriginalName(null);
		post.setMediaContentType(null);
	}

	private String normalizeYoutubeUrl(String youtubeUrl) {
		if (youtubeUrl == null || youtubeUrl.isBlank()) {
			return null;
		}
		return youtubeUrl.trim();
	}

	private void toggleReaction(BoardPost post, SiteUser siteUser, boolean likeReaction) {
		post.setVoter(ensureVoterSet(post.getVoter()));
		post.setDislikeVoter(ensureVoterSet(post.getDislikeVoter()));

		Set<SiteUser> targetVoters = likeReaction ? post.getVoter() : post.getDislikeVoter();
		Set<SiteUser> oppositeVoters = likeReaction ? post.getDislikeVoter() : post.getVoter();

		if (containsUser(targetVoters, siteUser)) {
			removeUser(targetVoters, siteUser);
		} else {
			targetVoters.add(siteUser);
			removeUser(oppositeVoters, siteUser);
		}
		this.boardPostRepository.save(post);
	}

	private Set<SiteUser> ensureVoterSet(Set<SiteUser> voters) {
		return voters == null ? new HashSet<>() : voters;
	}

	private Comparator<BoardPost> popularPostComparator() {
		return Comparator.comparingInt(this::likeCount)
				.reversed()
				.thenComparing(Comparator.comparingInt(this::safeViewCount).reversed())
				.thenComparing(BoardPost::getCreateDate, Comparator.reverseOrder());
	}

	private int likeCount(BoardPost post) {
		return post.getVoter() == null ? 0 : post.getVoter().size();
	}

	private int safeViewCount(BoardPost post) {
		return post.getViewCount() == null ? 0 : post.getViewCount();
	}

	private boolean containsUser(Set<SiteUser> voters, SiteUser siteUser) {
		return voters.stream().anyMatch(user -> user.getUsername().equals(siteUser.getUsername()));
	}

	private void removeUser(Set<SiteUser> voters, SiteUser siteUser) {
		voters.removeIf(user -> user.getUsername().equals(siteUser.getUsername()));
	}
}
