package com.mysite.sbb.skilltree;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.mysite.sbb.DataNotFoundException;
import com.mysite.sbb.skilltree.comment.SkillTreeCommentRepository;
import com.mysite.sbb.user.SiteUser;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class SkillTreePostService {

	private final SkillTreePostRepository skillTreePostRepository;
	private final SkillTreeCommentRepository skillTreeCommentRepository;

	public Page<SkillTreePost> getList(SkillTreeJob job, int page, String kw) {
		List<Sort.Order> sorts = new ArrayList<>();
		sorts.add(Sort.Order.desc("createDate"));
		Pageable pageable = PageRequest.of(page, 10, Sort.by(sorts));
		Page<SkillTreePost> postPage = this.skillTreePostRepository.findAllByJobAndKeyword(job, kw, pageable);
		return hydratePageMetrics(postPage);
	}

	public SkillTreePost getPost(Integer id) {
		Optional<SkillTreePost> post = this.skillTreePostRepository.findById(id);
		if (post.isPresent()) {
			SkillTreePost skillTreePost = post.get();
			hydrateMetrics(skillTreePost);
			return skillTreePost;
		}
		throw new DataNotFoundException("skilltree post not found");
	}

	public SkillTreePost increaseViewCount(Integer id) {
		SkillTreePost post = getPost(id);
		post.setViewCount(safeViewCount(post) + 1);
		this.skillTreePostRepository.save(post);
		hydrateMetrics(post);
		return post;
	}

	public SkillTreePost create(SkillTreeJob job, String subject, String content, SiteUser author) {
		SkillTreePost post = new SkillTreePost();
		post.setJob(job);
		post.setSubject(subject);
		post.setContent(content);
		post.setViewCount(0);
		post.setCreateDate(LocalDateTime.now());
		post.setAuthor(author);
		return this.skillTreePostRepository.save(post);
	}

	public void modify(SkillTreePost post, SkillTreeJob job, String subject, String content) {
		post.setJob(job);
		post.setSubject(subject);
		post.setContent(content);
		post.setModifyDate(LocalDateTime.now());
		this.skillTreePostRepository.save(post);
	}

	public void delete(SkillTreePost post) {
		this.skillTreePostRepository.delete(post);
	}

	public void toggleLike(SkillTreePost post, SiteUser siteUser) {
		toggleReaction(post, siteUser, true);
	}

	public void toggleDislike(SkillTreePost post, SiteUser siteUser) {
		toggleReaction(post, siteUser, false);
	}

	public List<SkillTreePost> getRecentPosts(int limit) {
		Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Order.desc("createDate")));
		List<SkillTreePost> posts = this.skillTreePostRepository.findAll(pageable).getContent();
		return hydrateListMetrics(posts);
	}

	public List<SkillTreePost> getPopularPosts(int limit) {
		return this.skillTreePostRepository.findAll(
				Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createDate"))).stream()
				.peek(this::hydrateMetrics)
				.sorted(popularPostComparator())
				.limit(limit)
				.toList();
	}

	public Map<String, Long> getPostCounts() {
		Map<String, Long> postCounts = new LinkedHashMap<>();
		for (SkillTreeJob job : SkillTreeJob.values()) {
			postCounts.put(job.getLabel(), this.skillTreePostRepository.countByJob(job));
		}
		return postCounts;
	}

	private void hydrateMetrics(SkillTreePost post) {
		post.setCommentCount(this.skillTreeCommentRepository.countByPost(post));
	}

	private Page<SkillTreePost> hydratePageMetrics(Page<SkillTreePost> postPage) {
		postPage.forEach(this::hydrateMetrics);
		return postPage;
	}

	private List<SkillTreePost> hydrateListMetrics(List<SkillTreePost> posts) {
		posts.forEach(this::hydrateMetrics);
		return posts;
	}

	private void toggleReaction(SkillTreePost post, SiteUser siteUser, boolean likeAction) {
		Set<SiteUser> targetVoters = likeAction ? ensureVoterSet(post) : ensureDislikeVoterSet(post);
		Set<SiteUser> oppositeVoters = likeAction ? ensureDislikeVoterSet(post) : ensureVoterSet(post);
		if (containsUser(targetVoters, siteUser)) {
			removeUser(targetVoters, siteUser);
		} else {
			targetVoters.add(siteUser);
			removeUser(oppositeVoters, siteUser);
		}
		this.skillTreePostRepository.save(post);
	}

	private Set<SiteUser> ensureVoterSet(SkillTreePost post) {
		if (post.getVoter() == null) {
			post.setVoter(new HashSet<>());
		}
		return post.getVoter();
	}

	private Set<SiteUser> ensureDislikeVoterSet(SkillTreePost post) {
		if (post.getDislikeVoter() == null) {
			post.setDislikeVoter(new HashSet<>());
		}
		return post.getDislikeVoter();
	}

	private Comparator<SkillTreePost> popularPostComparator() {
		return Comparator.comparingInt(this::likeCount)
				.reversed()
				.thenComparing(this::safeViewCount, Comparator.reverseOrder())
				.thenComparing(SkillTreePost::getCreateDate, Comparator.reverseOrder());
	}

	private int likeCount(SkillTreePost post) {
		return post.getVoter() == null ? 0 : post.getVoter().size();
	}

	private int safeViewCount(SkillTreePost post) {
		return post.getViewCount() == null ? 0 : post.getViewCount();
	}

	private boolean containsUser(Set<SiteUser> voters, SiteUser siteUser) {
		return voters.stream().anyMatch(user -> user.getUsername().equals(siteUser.getUsername()));
	}

	private void removeUser(Set<SiteUser> voters, SiteUser siteUser) {
		voters.removeIf(user -> user.getUsername().equals(siteUser.getUsername()));
	}
}
