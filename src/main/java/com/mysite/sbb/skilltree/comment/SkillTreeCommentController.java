package com.mysite.sbb.skilltree.comment;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.mysite.sbb.skilltree.SkillTreeJob;
import com.mysite.sbb.skilltree.SkillTreePost;
import com.mysite.sbb.skilltree.SkillTreePostService;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RequestMapping("/skilltree/comments")
@Controller
public class SkillTreeCommentController {

	private final SkillTreePostService skillTreePostService;
	private final SkillTreeCommentService skillTreeCommentService;
	private final UserService userService;

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/create/{postId}")
	public String create(Model model, @PathVariable("postId") Integer postId,
			@Valid SkillTreeCommentForm skillTreeCommentForm, BindingResult bindingResult, Principal principal) {
		SkillTreePost post = this.skillTreePostService.getPost(postId);
		SiteUser siteUser = getCurrentUser(principal);
		SkillTreeComment parentComment = resolveParentComment(post, skillTreeCommentForm.getParentId());
		if (bindingResult.hasErrors()) {
			return populateDetailModel(model, post);
		}
		if (isDeletedParentComment(parentComment)) {
			bindingResult.reject("parentId", "삭제된 댓글에는 답글을 달 수 없습니다.");
			return populateDetailModel(model, post);
		}
		SkillTreeComment comment = this.skillTreeCommentService.create(post, skillTreeCommentForm.getContent(),
				siteUser, parentComment);
		return redirectToComment(post, comment.getId());
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/edit/{id}")
	public String edit(SkillTreeCommentForm skillTreeCommentForm, Model model, @PathVariable("id") Integer id,
			Principal principal) {
		SkillTreeComment comment = getEditableComment(id, principal);
		skillTreeCommentForm.setContent(comment.getContent());
		return populateCommentForm(model, comment);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/edit/{id}")
	public String edit(@Valid SkillTreeCommentForm skillTreeCommentForm, BindingResult bindingResult,
			@PathVariable("id") Integer id, Principal principal, Model model) {
		SkillTreeComment comment = getEditableComment(id, principal);
		if (bindingResult.hasErrors()) {
			return populateCommentForm(model, comment);
		}
		this.skillTreeCommentService.modify(comment, skillTreeCommentForm.getContent());
		return redirectToComment(comment);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/delete/{id}")
	public String deleteLegacy(Principal principal, @PathVariable("id") Integer id) {
		return delete(principal, id);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/delete/{id}")
	public String delete(Principal principal, @PathVariable("id") Integer id) {
		SkillTreeComment comment = getAuthorizedComment(id, principal);
		String redirectUrl = buildDeleteRedirect(comment);
		this.skillTreeCommentService.delete(comment);
		return redirectUrl;
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/like/{id}")
	public String likeLegacy(Principal principal, @PathVariable("id") Integer id) {
		return like(principal, id);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/like/{id}")
	public String like(Principal principal, @PathVariable("id") Integer id) {
		SkillTreeComment comment = this.skillTreeCommentService.getComment(id);
		SiteUser siteUser = getCurrentUser(principal);
		this.skillTreeCommentService.toggleLike(comment, siteUser);
		return redirectToComment(comment);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/dislike/{id}")
	public String dislikeLegacy(Principal principal, @PathVariable("id") Integer id) {
		return dislike(principal, id);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/dislike/{id}")
	public String dislike(Principal principal, @PathVariable("id") Integer id) {
		SkillTreeComment comment = this.skillTreeCommentService.getComment(id);
		SiteUser siteUser = getCurrentUser(principal);
		this.skillTreeCommentService.toggleDislike(comment, siteUser);
		return redirectToComment(comment);
	}

	private SkillTreeComment resolveParentComment(SkillTreePost post, Integer parentId) {
		if (parentId == null) {
			return null;
		}
		SkillTreeComment parentComment = this.skillTreeCommentService.getComment(parentId);
		if (!parentComment.getPost().getId().equals(post.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 답글 요청입니다.");
		}
		if (parentComment.getParentComment() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답글에는 다시 답글을 달 수 없습니다.");
		}
		return parentComment;
	}

	private void validateAuthor(SkillTreeComment comment, Principal principal) {
		if (comment.getAuthor() == null || !comment.getAuthor().getUsername().equals(principal.getName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정 권한이 없습니다.");
		}
	}

	private SkillTreeComment getAuthorizedComment(Integer id, Principal principal) {
		SkillTreeComment comment = this.skillTreeCommentService.getComment(id);
		validateAuthor(comment, principal);
		return comment;
	}

	private SkillTreeComment getEditableComment(Integer id, Principal principal) {
		SkillTreeComment comment = getAuthorizedComment(id, principal);
		validateEditable(comment);
		return comment;
	}

	private void validateEditable(SkillTreeComment comment) {
		if (comment.isDeleted()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제된 댓글은 수정할 수 없습니다.");
		}
	}

	private String populateCommentForm(Model model, SkillTreeComment comment) {
		model.addAttribute("pageTitle", "스킬트리 댓글 수정 - A2C");
		model.addAttribute("activeNav", "skilltree");
		model.addAttribute("currentJob", comment.getPost().getJob());
		model.addAttribute("comment", comment);
		return "skilltree_comment_form";
	}

	private String populateDetailModel(Model model, SkillTreePost post) {
		post.setCommentList(this.skillTreeCommentService.getCommentsByPost(post));
		model.addAttribute("pageTitle", post.getSubject() + " - A2C");
		model.addAttribute("activeNav", "skilltree");
		model.addAttribute("currentJob", post.getJob());
		model.addAttribute("skillTreeJobs", com.mysite.sbb.skilltree.SkillTreeJob.values());
		model.addAttribute("post", post);
		if (!model.containsAttribute("skillTreeCommentForm")) {
			model.addAttribute("skillTreeCommentForm", new SkillTreeCommentForm());
		}
		return "skilltree_detail";
	}

	private boolean isDeletedParentComment(SkillTreeComment parentComment) {
		return parentComment != null && parentComment.isDeleted();
	}

	private SiteUser getCurrentUser(Principal principal) {
		return this.userService.getUser(principal.getName());
	}

	private String buildDeleteRedirect(SkillTreeComment comment) {
		SkillTreeComment parentComment = comment.getParentComment();
		if (parentComment != null) {
			return redirectToComment(comment.getPost(), parentComment.getId());
		}
		return redirectToDetail(comment.getPost());
	}

	private String buildCommentAnchorUrl(SkillTreePost post, Integer commentId) {
		return buildDetailUrl(post) + "#comment_" + commentId;
	}

	private String redirectToComment(SkillTreeComment comment) {
		return redirectToComment(comment.getPost(), comment.getId());
	}

	private String redirectToComment(SkillTreePost post, Integer commentId) {
		return "redirect:" + buildCommentAnchorUrl(post, commentId);
	}

	private String redirectToDetail(SkillTreePost post) {
		return "redirect:" + buildDetailUrl(post);
	}

	private String buildDetailUrl(SkillTreePost post) {
		SkillTreeJob job = post.getJob();
		return UriComponentsBuilder.fromPath("/skilltree/{jobSlug}/posts/{id}")
				.buildAndExpand(job.getSlug(), post.getId())
				.encode()
				.toUriString();
	}
}
