package com.mysite.sbb.board;

import java.io.IOException;
import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.mysite.sbb.comment.BoardCommentForm;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequestMapping("/boards")
@RequiredArgsConstructor
@Controller
public class BoardController {

	private final BoardPostService boardPostService;
	private final BoardMediaStorageService boardMediaStorageService;
	private final UserService userService;

	@GetMapping
	public String boardHomeRedirect() {
		return "redirect:/boards/free";
	}

	@GetMapping("/{categoryCode}")
	public String list(Model model, @PathVariable("categoryCode") String categoryCode,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "kw", defaultValue = "") String kw) {
		BoardCategory category = resolveCategory(categoryCode);
		Page<BoardPost> paging = this.boardPostService.getList(category, page, kw);
		model.addAttribute("pageTitle", category.getLabel() + " - A2C");
		model.addAttribute("currentCategory", category);
		model.addAttribute("activeBoardCode", category.getCode());
		model.addAttribute("paging", paging);
		model.addAttribute("kw", kw);
		return "board_list";
	}

	@GetMapping("/{categoryCode}/posts/{id}")
	public String detail(Model model, @PathVariable("categoryCode") String categoryCode, @PathVariable("id") Integer id,
			BoardCommentForm boardCommentForm) {
		BoardCategory category = resolveCategory(categoryCode);
		BoardPost post = increaseValidatedPostViewCount(category, id);
		model.addAttribute("pageTitle", post.getSubject() + " - A2C");
		model.addAttribute("currentCategory", category);
		model.addAttribute("activeBoardCode", category.getCode());
		model.addAttribute("post", post);
		return "board_detail";
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/{categoryCode}/write")
	public String create(BoardPostForm boardPostForm, Model model, @PathVariable("categoryCode") String categoryCode) {
		BoardCategory category = resolveCategory(categoryCode);
		return populateBoardForm(model, category, null, category.getLabel() + " 글쓰기 - A2C");
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/{categoryCode}/write")
	public String create(@PathVariable("categoryCode") String categoryCode, @Valid BoardPostForm boardPostForm,
			BindingResult bindingResult, Principal principal, Model model) {
		BoardCategory category = resolveCategory(categoryCode);
		if (bindingResult.hasErrors()) {
			return populateBoardForm(model, category, null, category.getLabel() + " 글쓰기 - A2C");
		}
		SiteUser siteUser = this.userService.getUser(principal.getName());
		StoredBoardMedia storedMedia;
		try {
			storedMedia = storeMedia(category, boardPostForm);
		} catch (IllegalArgumentException | IOException exception) {
			bindingResult.reject("mediaFile", exception.getMessage());
			return populateBoardForm(model, category, null, category.getLabel() + " 글쓰기 - A2C");
		}
		BoardPost post;
		try {
			post = this.boardPostService.create(category, boardPostForm.getSubject(), boardPostForm.getContent(),
					siteUser, storedMedia, boardPostForm.getYoutubeUrl());
		} catch (RuntimeException exception) {
			deleteStoredMediaIfPresent(storedMedia);
			throw exception;
		}
		return String.format("redirect:/boards/%s/posts/%s", category.getCode(), post.getId());
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/{categoryCode}/posts/{id}/edit")
	public String edit(BoardPostForm boardPostForm, Model model, @PathVariable("categoryCode") String categoryCode,
			@PathVariable("id") Integer id, Principal principal) {
		BoardCategory category = resolveCategory(categoryCode);
		BoardPost post = getAuthorizedPost(category, id, principal);
		boardPostForm.setSubject(post.getSubject());
		boardPostForm.setContent(post.getContent());
		boardPostForm.setYoutubeUrl(post.getYoutubeUrl());
		return populateBoardForm(model, category, post, "게시글 수정 - A2C");
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/{categoryCode}/posts/{id}/edit")
	public String edit(@PathVariable("categoryCode") String categoryCode, @PathVariable("id") Integer id,
			@Valid BoardPostForm boardPostForm, BindingResult bindingResult, Principal principal, Model model) {
		BoardCategory category = resolveCategory(categoryCode);
		BoardPost post = getAuthorizedPost(category, id, principal);
		if (bindingResult.hasErrors()) {
			return populateBoardForm(model, category, post, "게시글 수정 - A2C");
		}
		String previousMediaPath = post.getMediaPath();
		boolean removeMedia = boardPostForm.isRemoveMedia();
		StoredBoardMedia storedMedia;
		try {
			storedMedia = storeMedia(category, boardPostForm);
		} catch (IllegalArgumentException | IOException exception) {
			bindingResult.reject("mediaFile", exception.getMessage());
			return populateBoardForm(model, category, post, "게시글 수정 - A2C");
		}
		try {
			this.boardPostService.modify(post, boardPostForm.getSubject(), boardPostForm.getContent(),
					boardPostForm.getYoutubeUrl(), storedMedia, removeMedia);
		} catch (RuntimeException exception) {
			deleteStoredMediaIfPresent(storedMedia);
			throw exception;
		}
		if (storedMedia != null || removeMedia) {
			this.boardMediaStorageService.delete(previousMediaPath);
		}
		return String.format("redirect:/boards/%s/posts/%s", category.getCode(), id);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/{categoryCode}/posts/{id}/delete")
	public String delete(Principal principal, @PathVariable("categoryCode") String categoryCode,
			@PathVariable("id") Integer id) {
		BoardCategory category = resolveCategory(categoryCode);
		BoardPost post = getAuthorizedPost(category, id, principal);
		String mediaPath = post.getMediaPath();
		this.boardPostService.delete(post);
		this.boardMediaStorageService.delete(mediaPath);
		return String.format("redirect:/boards/%s", category.getCode());
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/{categoryCode}/posts/{id}/like")
	public String like(Principal principal, @PathVariable("categoryCode") String categoryCode,
			@PathVariable("id") Integer id) {
		BoardCategory category = resolveCategory(categoryCode);
		BoardPost post = getValidatedPost(category, id);
		SiteUser siteUser = this.userService.getUser(principal.getName());
		this.boardPostService.toggleLike(post, siteUser);
		return String.format("redirect:/boards/%s/posts/%s", category.getCode(), id);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/{categoryCode}/posts/{id}/dislike")
	public String dislike(Principal principal, @PathVariable("categoryCode") String categoryCode,
			@PathVariable("id") Integer id) {
		BoardCategory category = resolveCategory(categoryCode);
		BoardPost post = getValidatedPost(category, id);
		SiteUser siteUser = this.userService.getUser(principal.getName());
		this.boardPostService.toggleDislike(post, siteUser);
		return String.format("redirect:/boards/%s/posts/%s", category.getCode(), id);
	}

	private BoardCategory resolveCategory(String categoryCode) {
		try {
			return BoardCategory.fromCode(categoryCode);
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시판을 찾을 수 없습니다.");
		}
	}

	private void validateBoardMatch(BoardCategory category, BoardPost post) {
		if (post.getCategory() != category) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시판 글을 찾을 수 없습니다.");
		}
	}

	private void validateAuthor(BoardPost post, Principal principal) {
		if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(principal.getName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정 권한이 없습니다.");
		}
	}

	private BoardPost getValidatedPost(BoardCategory category, Integer id) {
		BoardPost post = this.boardPostService.getPost(id);
		validateBoardMatch(category, post);
		return post;
	}

	private BoardPost getAuthorizedPost(BoardCategory category, Integer id, Principal principal) {
		BoardPost post = getValidatedPost(category, id);
		validateAuthor(post, principal);
		return post;
	}

	private BoardPost increaseValidatedPostViewCount(BoardCategory category, Integer id) {
		BoardPost post = this.boardPostService.increaseViewCount(id);
		validateBoardMatch(category, post);
		return post;
	}

	private StoredBoardMedia storeMedia(BoardCategory category, BoardPostForm boardPostForm) throws IOException {
		return this.boardMediaStorageService.store(category, boardPostForm.getMediaFile());
	}

	private void deleteStoredMediaIfPresent(StoredBoardMedia storedMedia) {
		if (storedMedia != null) {
			this.boardMediaStorageService.delete(storedMedia.path());
		}
	}

	private String populateBoardForm(Model model, BoardCategory category, BoardPost post, String pageTitle) {
		model.addAttribute("pageTitle", pageTitle);
		model.addAttribute("currentCategory", category);
		model.addAttribute("activeBoardCode", category.getCode());
		model.addAttribute("post", post);
		if (post != null) {
			model.addAttribute("formAction", String.format("/boards/%s/posts/%s/edit", category.getCode(), post.getId()));
		} else {
			model.addAttribute("formAction", String.format("/boards/%s/write", category.getCode()));
		}
		return "board_form";
	}
}
