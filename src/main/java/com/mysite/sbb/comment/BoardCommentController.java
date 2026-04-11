package com.mysite.sbb.comment;

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

import com.mysite.sbb.board.BoardPost;
import com.mysite.sbb.board.BoardPostService;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequestMapping("/comments")
@RequiredArgsConstructor
@Controller
public class BoardCommentController {

	private final BoardPostService boardPostService;
	private final BoardCommentService boardCommentService;
	private final UserService userService;

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/create/{postId}")
	public String create(Model model, @PathVariable("postId") Integer postId, @Valid BoardCommentForm boardCommentForm,
			BindingResult bindingResult, Principal principal) {
		BoardPost post = this.boardPostService.getPost(postId);
		SiteUser siteUser = this.userService.getUser(principal.getName());
		BoardComment parentComment = resolveParentComment(post, boardCommentForm.getParentId());
		if (bindingResult.hasErrors()) {
			return populateBoardDetail(model, post);
		}
		if (parentComment != null && parentComment.isDeleted()) {
			bindingResult.reject("parentId", "??젣???볤??먮뒗 ?듦????????놁뒿?덈떎.");
			return populateBoardDetail(model, post);
		}
		BoardComment comment = this.boardCommentService.create(post, boardCommentForm.getContent(), siteUser, parentComment);
		return buildCommentRedirect(comment);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/edit/{id}")
	public String edit(BoardCommentForm boardCommentForm, Model model, @PathVariable("id") Integer id,
			Principal principal) {
		BoardComment comment = getEditableComment(id, principal);
		boardCommentForm.setContent(comment.getContent());
		return populateCommentForm(model, comment);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/edit/{id}")
	public String edit(@Valid BoardCommentForm boardCommentForm, BindingResult bindingResult, @PathVariable("id") Integer id,
			Principal principal, Model model) {
		BoardComment comment = getEditableComment(id, principal);
		if (bindingResult.hasErrors()) {
			return populateCommentForm(model, comment);
		}
		this.boardCommentService.modify(comment, boardCommentForm.getContent());
		return buildCommentRedirect(comment);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/delete/{id}")
	public String delete(Principal principal, @PathVariable("id") Integer id) {
		BoardComment comment = getAuthorizedComment(id, principal);
		BoardPost post = comment.getPost();
		BoardComment parentComment = comment.getParentComment();
		this.boardCommentService.delete(comment);
		if (parentComment != null) {
			return buildCommentRedirect(post, parentComment.getId());
		}
		return buildPostRedirect(post);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/like/{id}")
	public String like(Principal principal, @PathVariable("id") Integer id) {
		BoardComment comment = this.boardCommentService.getComment(id);
		SiteUser siteUser = this.userService.getUser(principal.getName());
		this.boardCommentService.toggleLike(comment, siteUser);
		return buildCommentRedirect(comment);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/dislike/{id}")
	public String dislike(Principal principal, @PathVariable("id") Integer id) {
		BoardComment comment = this.boardCommentService.getComment(id);
		SiteUser siteUser = this.userService.getUser(principal.getName());
		this.boardCommentService.toggleDislike(comment, siteUser);
		return buildCommentRedirect(comment);
	}

	private void validateAuthor(BoardComment comment, Principal principal) {
		if (comment.getAuthor() == null || !comment.getAuthor().getUsername().equals(principal.getName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?섏젙 沅뚰븳???놁뒿?덈떎.");
		}
	}

	private void validateEditable(BoardComment comment) {
		if (comment.isDeleted()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "??젣???볤?? ?섏젙?????놁뒿?덈떎.");
		}
	}

	private BoardComment getAuthorizedComment(Integer id, Principal principal) {
		BoardComment comment = this.boardCommentService.getComment(id);
		validateAuthor(comment, principal);
		return comment;
	}

	private BoardComment getEditableComment(Integer id, Principal principal) {
		BoardComment comment = getAuthorizedComment(id, principal);
		validateEditable(comment);
		return comment;
	}

	private BoardComment resolveParentComment(BoardPost post, Integer parentId) {
		if (parentId == null) {
			return null;
		}
		BoardComment parentComment = this.boardCommentService.getComment(parentId);
		if (!parentComment.getPost().getId().equals(post.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?섎せ???듦? ?붿껌?낅땲??");
		}
		if (parentComment.getParentComment() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?듦??먮뒗 ?ㅼ떆 ?듦????????놁뒿?덈떎.");
		}
		return parentComment;
	}

	private String populateBoardDetail(Model model, BoardPost post) {
		model.addAttribute("pageTitle", post.getSubject() + " - A2C");
		model.addAttribute("currentCategory", post.getCategory());
		model.addAttribute("activeBoardCode", post.getCategory().getCode());
		model.addAttribute("post", post);
		return "board_detail";
	}

	private String populateCommentForm(Model model, BoardComment comment) {
		model.addAttribute("pageTitle", "?볤? ?섏젙 - A2C");
		model.addAttribute("currentCategory", comment.getPost().getCategory());
		model.addAttribute("activeBoardCode", comment.getPost().getCategory().getCode());
		model.addAttribute("comment", comment);
		return "comment_form";
	}

	private String buildCommentRedirect(BoardComment comment) {
		return buildCommentRedirect(comment.getPost(), comment.getId());
	}

	private String buildCommentRedirect(BoardPost post, Integer commentId) {
		return String.format("redirect:/boards/%s/posts/%s#comment_%s", post.getCategory().getCode(), post.getId(),
				commentId);
	}

	private String buildPostRedirect(BoardPost post) {
		return String.format("redirect:/boards/%s/posts/%s", post.getCategory().getCode(), post.getId());
	}
}
