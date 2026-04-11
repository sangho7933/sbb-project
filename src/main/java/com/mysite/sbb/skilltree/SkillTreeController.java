package com.mysite.sbb.skilltree;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.mysite.sbb.skilltree.comment.SkillTreeCommentForm;
import com.mysite.sbb.skilltree.comment.SkillTreeCommentService;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RequestMapping("/skilltree")
@Controller
public class SkillTreeController {

	private final SkillTreePostService skillTreePostService;
	private final SkillTreeCommentService skillTreeCommentService;
	private final UserService userService;

	@GetMapping
	public String home(Model model) {
		model.addAttribute("pageTitle", "아이온2 직업별 스킬트리 - A2C");
		model.addAttribute("activeNav", "skilltree");
		model.addAttribute("skillTreeJobs", SkillTreeJob.values());
		model.addAttribute("skillTreePostCounts", this.skillTreePostService.getPostCounts());
		model.addAttribute("recentSkillTreePosts", this.skillTreePostService.getRecentPosts(6));
		model.addAttribute("popularSkillTreePosts", this.skillTreePostService.getPopularPosts(4));
		return "skilltree_home";
	}

	@GetMapping("/{jobSlug}")
	public String list(Model model, @PathVariable("jobSlug") String jobSlug,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "kw", defaultValue = "") String kw) {
		SkillTreeJob job = resolveJob(jobSlug);
		if (!job.getSlug().equalsIgnoreCase(jobSlug)) {
			return "redirect:" + buildListUrl(job, page, kw);
		}
		Page<SkillTreePost> paging = this.skillTreePostService.getList(job, page, kw);
		model.addAttribute("pageTitle", job.getLabel() + " 스킬트리 - A2C");
		model.addAttribute("activeNav", "skilltree");
		model.addAttribute("currentJob", job);
		model.addAttribute("skillTreeJobs", SkillTreeJob.values());
		model.addAttribute("paging", paging);
		model.addAttribute("kw", kw);
		return "skilltree_list";
	}

	@GetMapping("/{jobSlug}/posts/{id}")
	public String detail(Model model, @PathVariable("jobSlug") String jobSlug, @PathVariable("id") Integer id,
			@ModelAttribute("skillTreeCommentForm") SkillTreeCommentForm skillTreeCommentForm) {
		SkillTreeJob job = resolveJob(jobSlug);
		if (!job.getSlug().equalsIgnoreCase(jobSlug)) {
			return "redirect:" + buildDetailUrl(job, id);
		}
		SkillTreePost post = getDetailPost(job, id);
		populateDetailModel(model, post);
		return "skilltree_detail";
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/write")
	public String create(SkillTreePostForm skillTreePostForm, Model model,
			@RequestParam(value = "job", required = false) String jobValue,
			@RequestParam(value = "returnUrl", required = false) String returnUrl) {
		return populateCreateForm(model, skillTreePostForm, jobValue, returnUrl);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/write")
	public String create(@Valid SkillTreePostForm skillTreePostForm, BindingResult bindingResult,
			Principal principal, Model model,
			@RequestParam(value = "returnUrl", required = false) String returnUrl) {
		SkillTreeJob job = resolveSelectedJob(skillTreePostForm.getJob(), bindingResult);
		String resolvedReturnUrl = resolveCreateReturnUrl(returnUrl, skillTreePostForm.getJob());
		if (bindingResult.hasErrors()) {
			return populateCreateFormModel(model, resolvedReturnUrl);
		}
		SiteUser siteUser = getCurrentUser(principal);
		SkillTreePost post = this.skillTreePostService.create(job, skillTreePostForm.getSubject(),
				skillTreePostForm.getContent(), siteUser);
		return redirectToDetail(post);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/posts/{id}/edit")
	public String edit(SkillTreePostForm skillTreePostForm, Model model, @PathVariable("id") Integer id,
			Principal principal,
			@RequestParam(value = "returnUrl", required = false) String returnUrl) {
		SkillTreePost post = getAuthorizedPost(id, principal);
		populatePostForm(skillTreePostForm, post);
		return populateEditForm(model, post, returnUrl);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/posts/{id}/edit")
	public String edit(@PathVariable("id") Integer id, @Valid SkillTreePostForm skillTreePostForm,
			BindingResult bindingResult, Principal principal, Model model,
			@RequestParam(value = "returnUrl", required = false) String returnUrl) {
		SkillTreePost post = getAuthorizedPost(id, principal);
		SkillTreeJob job = resolveSelectedJob(skillTreePostForm.getJob(), bindingResult);
		String resolvedReturnUrl = resolveEditReturnUrl(returnUrl, post);
		if (bindingResult.hasErrors()) {
			return populateEditFormModel(model, post, resolvedReturnUrl);
		}
		this.skillTreePostService.modify(post, job, skillTreePostForm.getSubject(), skillTreePostForm.getContent());
		return redirectToDetail(post);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/posts/{id}/delete")
	public String deleteLegacy(Principal principal, @PathVariable("id") Integer id) {
		SkillTreePost post = getAuthorizedPost(id, principal);
		SkillTreeJob job = post.getJob();
		this.skillTreePostService.delete(post);
		return redirectToList(job);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/posts/{id}/delete")
	public String delete(Principal principal, @PathVariable("id") Integer id) {
		SkillTreePost post = getAuthorizedPost(id, principal);
		SkillTreeJob job = post.getJob();
		this.skillTreePostService.delete(post);
		return redirectToList(job);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping(value = "/posts/{id}/delete", headers = "X-Requested-With=XMLHttpRequest")
	@ResponseBody
	public ResponseEntity<Void> deleteAjax(Principal principal, @PathVariable("id") Integer id) {
		SkillTreePost post = getAuthorizedPost(id, principal);
		this.skillTreePostService.delete(post);
		return ResponseEntity.ok().build();
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/posts/{id}/like")
	public String likeLegacy(Principal principal, @PathVariable("id") Integer id) {
		return like(principal, id);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/posts/{id}/like")
	public String like(Principal principal, @PathVariable("id") Integer id) {
		SkillTreePost post = this.skillTreePostService.getPost(id);
		SiteUser siteUser = getCurrentUser(principal);
		this.skillTreePostService.toggleLike(post, siteUser);
		return redirectToDetail(post);
	}

	@PreAuthorize("isAuthenticated()")
	@GetMapping("/posts/{id}/dislike")
	public String dislikeLegacy(Principal principal, @PathVariable("id") Integer id) {
		return dislike(principal, id);
	}

	@PreAuthorize("isAuthenticated()")
	@PostMapping("/posts/{id}/dislike")
	public String dislike(Principal principal, @PathVariable("id") Integer id) {
		SkillTreePost post = this.skillTreePostService.getPost(id);
		SiteUser siteUser = getCurrentUser(principal);
		this.skillTreePostService.toggleDislike(post, siteUser);
		return redirectToDetail(post);
	}

	private String populateCreateForm(Model model, SkillTreePostForm skillTreePostForm, String jobValue,
			String returnUrl) {
		SkillTreeJob selectedJob = resolveRequestedJob(jobValue);
		populateSelectedJob(skillTreePostForm, selectedJob);
		return populateCreateFormModel(model, resolveCreateReturnUrl(returnUrl, selectedJob));
	}

	private String populateCreateFormModel(Model model, String returnUrl) {
		return populateFormModel(model, null, "스킬트리 글쓰기 - A2C", returnUrl);
	}

	private String populateEditForm(Model model, SkillTreePost post, String returnUrl) {
		return populateEditFormModel(model, post, resolveEditReturnUrl(returnUrl, post));
	}

	private String populateEditFormModel(Model model, SkillTreePost post, String returnUrl) {
		return populateFormModel(model, post, "스킬트리 글 수정 - A2C", returnUrl);
	}

	private String populateFormModel(Model model, SkillTreePost post, String pageTitle, String returnUrl) {
		model.addAttribute("pageTitle", pageTitle);
		model.addAttribute("activeNav", "skilltree");
		model.addAttribute("post", post);
		model.addAttribute("skillTreeJobs", SkillTreeJob.values());
		model.addAttribute("returnUrl", sanitizeReturnUrl(returnUrl));
		if (post != null) {
			model.addAttribute("formAction", String.format("/skilltree/posts/%s/edit", post.getId()));
		} else {
			model.addAttribute("formAction", "/skilltree/write");
		}
		return "skilltree_form";
	}

	private void populateDetailModel(Model model, SkillTreePost post) {
		post.setCommentList(this.skillTreeCommentService.getCommentsByPost(post));
		model.addAttribute("pageTitle", post.getSubject() + " - A2C");
		model.addAttribute("activeNav", "skilltree");
		model.addAttribute("currentJob", post.getJob());
		model.addAttribute("skillTreeJobs", SkillTreeJob.values());
		model.addAttribute("post", post);
		if (!model.containsAttribute("skillTreeCommentForm")) {
			model.addAttribute("skillTreeCommentForm", new SkillTreeCommentForm());
		}
	}

	private void populatePostForm(SkillTreePostForm skillTreePostForm, SkillTreePost post) {
		skillTreePostForm.setJob(post.getJob().getSlug());
		skillTreePostForm.setSubject(post.getSubject());
		skillTreePostForm.setContent(post.getContent());
	}

	private void populateSelectedJob(SkillTreePostForm skillTreePostForm, SkillTreeJob selectedJob) {
		if (selectedJob != null) {
			skillTreePostForm.setJob(selectedJob.getSlug());
		}
	}

	private SkillTreeJob resolveJob(String jobLabel) {
		try {
			return SkillTreeJob.fromPathValue(jobLabel);
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "직업을 찾을 수 없습니다.");
		}
	}

	private SkillTreeJob resolveSelectedJob(String jobLabel, BindingResult bindingResult) {
		try {
			return SkillTreeJob.fromPathValue(jobLabel);
		} catch (IllegalArgumentException exception) {
			bindingResult.rejectValue("job", "invalid", "직업을 선택해주세요.");
			return null;
		}
	}

	private SkillTreeJob resolveJobOrNull(String jobValue) {
		if (jobValue == null || jobValue.isBlank()) {
			return null;
		}
		try {
			return SkillTreeJob.fromPathValue(jobValue);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private SkillTreeJob resolveRequestedJob(String jobValue) {
		return resolveJobOrNull(jobValue);
	}

	private void validateJobMatch(SkillTreeJob job, SkillTreePost post) {
		if (post.getJob() != job) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "직업 스킬트리 글을 찾을 수 없습니다.");
		}
	}

	private SkillTreePost getDetailPost(SkillTreeJob job, Integer id) {
		SkillTreePost post = this.skillTreePostService.increaseViewCount(id);
		validateJobMatch(job, post);
		return post;
	}

	private SkillTreePost getAuthorizedPost(Integer id, Principal principal) {
		SkillTreePost post = this.skillTreePostService.getPost(id);
		validateAuthor(post, principal);
		return post;
	}

	private void validateAuthor(SkillTreePost post, Principal principal) {
		if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(principal.getName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정 권한이 없습니다.");
		}
	}

	private SiteUser getCurrentUser(Principal principal) {
		return this.userService.getUser(principal.getName());
	}

	private String sanitizeReturnUrl(String returnUrl) {
		if (returnUrl != null && returnUrl.startsWith("/") && !returnUrl.startsWith("//")) {
			return returnUrl;
		}
		return null;
	}

	private String fallbackReturnUrl(String returnUrl, String jobLabel) {
		String resolvedReturnUrl = canonicalizeReturnUrl(returnUrl);
		if (resolvedReturnUrl != null) {
			return resolvedReturnUrl;
		}
		SkillTreeJob job = resolveJobOrNull(jobLabel);
		if (job != null) {
			return buildListUrl(job);
		}
		return "/skilltree";
	}

	private String resolveCreateReturnUrl(String returnUrl, SkillTreeJob selectedJob) {
		String resolvedReturnUrl = canonicalizeReturnUrl(returnUrl);
		if (resolvedReturnUrl == null && selectedJob != null) {
			return buildListUrl(selectedJob);
		}
		if (resolvedReturnUrl == null) {
			return "/skilltree";
		}
		return resolvedReturnUrl;
	}

	private String resolveCreateReturnUrl(String returnUrl, String jobValue) {
		return fallbackReturnUrl(returnUrl, jobValue);
	}

	private String resolveEditReturnUrl(String returnUrl, SkillTreePost post) {
		String resolvedReturnUrl = canonicalizeReturnUrl(returnUrl);
		if (resolvedReturnUrl == null) {
			return buildDetailUrl(post);
		}
		return resolvedReturnUrl;
	}

	private String buildDetailUrl(SkillTreePost post) {
		return buildDetailUrl(post.getJob(), post.getId());
	}

	private String buildDetailUrl(SkillTreeJob job, Integer postId) {
		return UriComponentsBuilder.fromPath("/skilltree/{jobSlug}/posts/{id}")
				.buildAndExpand(job.getSlug(), postId)
				.encode()
				.toUriString();
	}

	private String buildListUrl(SkillTreeJob job) {
		return buildListUrl(job, 0, "");
	}

	private String buildListUrl(SkillTreeJob job, int page, String kw) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/skilltree/{jobSlug}");
		if (page > 0) {
			builder.queryParam("page", page);
		}
		if (kw != null && !kw.isBlank()) {
			builder.queryParam("kw", kw);
		}
		return builder.buildAndExpand(job.getSlug())
				.encode()
				.toUriString();
	}

	private String redirectToDetail(SkillTreePost post) {
		return "redirect:" + buildDetailUrl(post);
	}

	private String redirectToList(SkillTreeJob job) {
		return "redirect:" + buildListUrl(job);
	}

	private String canonicalizeReturnUrl(String returnUrl) {
		String sanitized = sanitizeReturnUrl(returnUrl);
		if (sanitized == null) {
			return null;
		}
		for (SkillTreeJob job : SkillTreeJob.values()) {
			String legacyList = "/skilltree/" + job.getLabel();
			String canonicalList = "/skilltree/" + job.getSlug();
			if (sanitized.equals(legacyList)) {
				return canonicalList;
			}
			if (sanitized.startsWith(legacyList + "?")) {
				return canonicalList + sanitized.substring(legacyList.length());
			}
			if (sanitized.startsWith(legacyList + "/posts/")) {
				return canonicalList + sanitized.substring(legacyList.length());
			}
		}
		return sanitized;
	}
}
