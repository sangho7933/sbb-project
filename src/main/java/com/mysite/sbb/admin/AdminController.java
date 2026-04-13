/*
 * 관리자 대시보드 조회와 운영 액션 진입점을 묶는 컨트롤러이다.
 */
package com.mysite.sbb.admin;

import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.mysite.sbb.board.BoardMediaStorageService;
import com.mysite.sbb.board.BoardPost;
import com.mysite.sbb.board.BoardPostService;
import com.mysite.sbb.comment.BoardComment;
import com.mysite.sbb.comment.BoardCommentService;
import com.mysite.sbb.skilltree.SkillTreePost;
import com.mysite.sbb.skilltree.SkillTreePostService;
import com.mysite.sbb.skilltree.comment.SkillTreeComment;
import com.mysite.sbb.skilltree.comment.SkillTreeCommentService;
import com.mysite.sbb.trade.entity.TradeTransaction;
import com.mysite.sbb.trade.service.TradeItemService;
import com.mysite.sbb.user.SiteUser;
import com.mysite.sbb.user.UserService;

import lombok.RequiredArgsConstructor;

@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
/**
 * 관리자 화면 조회와 공통 삭제/숨김 흐름을 조합한다.
 */
public class AdminController {

	private static final int PAGE_SIZE = 10;

	private final UserService userService;
	private final BoardPostService boardPostService;
	private final BoardCommentService boardCommentService;
	private final SkillTreePostService skillTreePostService;
	private final SkillTreeCommentService skillTreeCommentService;
	private final TradeItemService tradeItemService;
	private final BoardMediaStorageService boardMediaStorageService;
	private final AdminDashboardService adminDashboardService;
	private final AdminModeService adminModeService;

	@GetMapping
	public String index(
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "userPage", defaultValue = "0") int userPage,
			@RequestParam(value = "postPage", defaultValue = "0") int postPage,
			@RequestParam(value = "commentPage", defaultValue = "0") int commentPage,
			@RequestParam(value = "tradePage", defaultValue = "0") int tradePage,
			Model model) {
		DashboardRequest dashboardRequest = sanitizeDashboardRequest(kw, userPage, postPage, commentPage, tradePage);
		boolean adminModeActive = isAdminMode(authentication, session);

		populateAdminPage(model, "\uAD00\uB9AC\uC790 \uD398\uC774\uC9C0 - A2C", adminModeActive);
		model.addAttribute("kw", dashboardRequest.keyword());
		if (adminModeActive) {
			model.addAttribute("dashboardStats", this.adminDashboardService.getDashboardStats());
			model.addAttribute("userPageData", this.userService.getAdminList(
					dashboardRequest.userPage(), PAGE_SIZE, dashboardRequest.keyword()));
			model.addAttribute("suspendedUsers", this.userService.getSuspendedUsers());
			model.addAttribute("postPageData", this.boardPostService.getAdminList(
					dashboardRequest.postPage(), PAGE_SIZE, dashboardRequest.keyword()));
			model.addAttribute("commentPageData", this.boardCommentService.getAdminList(
					dashboardRequest.commentPage(), PAGE_SIZE, dashboardRequest.keyword()));
			model.addAttribute("tradePageData", this.tradeItemService.getAdminList(
					dashboardRequest.tradePage(), PAGE_SIZE, dashboardRequest.keyword()));
		}
		return "admin/index";
	}

	@PostMapping("/mode/enable")
	public String enableAdminMode(HttpSession session) {
		this.adminModeService.enable(session);
		return "redirect:/admin";
	}

	@PostMapping("/mode/disable")
	public String disableAdminMode(HttpSession session) {
		this.adminModeService.disable(session);
		return "redirect:/";
	}

	@GetMapping("/users/{id}")
	public String userDetail(@PathVariable("id") Long id, Authentication authentication, HttpSession session, Model model) {
		requireAdminMode(authentication, session);
		SiteUser managedUser = this.userService.getUser(id);
		populateAdminPage(model, managedUser.getUsername() + " - 관리자 회원 상세 - A2C", true);
		model.addAttribute("managedUser", managedUser);
		model.addAttribute("userPosts", this.boardPostService.getPostsByAuthor(managedUser));
		model.addAttribute("userComments", this.boardCommentService.getCommentsByAuthor(managedUser));
		model.addAttribute("userTradeItems", this.tradeItemService.getItemsBySeller(managedUser));

		List<TradeTransaction> purchaseHistory = this.tradeItemService.getPurchaseHistory(managedUser);
		List<TradeTransaction> salesHistory = this.tradeItemService.getSalesHistory(managedUser);
		model.addAttribute("purchaseHistory", purchaseHistory);
		model.addAttribute("salesHistory", salesHistory);
		return "admin/user_detail";
	}

	@PostMapping("/users/{id}/suspend/{days}")
	public String suspendUser(
			@PathVariable("id") Long id,
			@PathVariable("days") int days,
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "redirect", defaultValue = "") String redirect,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "userPage", defaultValue = "0") int userPage,
			@RequestParam(value = "postPage", defaultValue = "0") int postPage,
			@RequestParam(value = "commentPage", defaultValue = "0") int commentPage,
			@RequestParam(value = "tradePage", defaultValue = "0") int tradePage,
			RedirectAttributes redirectAttributes) {
		DashboardRequest dashboardRequest = sanitizeDashboardRequest(kw, userPage, postPage, commentPage, tradePage);
		requireAdminMode(authentication, session);
		try {
			SiteUser suspendedUser = this.userService.suspendUser(id, days);
			redirectAttributes.addFlashAttribute("successMessage",
					suspendedUser.getUsername() + "님을 " + days + "일 정지했습니다.");
		} catch (IllegalStateException exception) {
			redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		}
		return resolveAdminRedirect(redirect, buildDashboardUrl(dashboardRequest));
	}

	@PostMapping("/users/{id}/release")
	public String releaseUserSuspension(
			@PathVariable("id") Long id,
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "redirect", defaultValue = "") String redirect,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "userPage", defaultValue = "0") int userPage,
			@RequestParam(value = "postPage", defaultValue = "0") int postPage,
			@RequestParam(value = "commentPage", defaultValue = "0") int commentPage,
			@RequestParam(value = "tradePage", defaultValue = "0") int tradePage,
			RedirectAttributes redirectAttributes) {
		DashboardRequest dashboardRequest = sanitizeDashboardRequest(kw, userPage, postPage, commentPage, tradePage);
		requireAdminMode(authentication, session);
		SiteUser releasedUser = this.userService.releaseSuspension(id);
		redirectAttributes.addFlashAttribute("successMessage",
				releasedUser.getUsername() + "님의 정지를 해제했습니다.");
		return resolveAdminRedirect(redirect, buildDashboardUrl(dashboardRequest));
	}

	@PostMapping("/posts/{id}/delete")
	public String deletePost(
			@PathVariable("id") Integer id,
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "redirect", defaultValue = "") String redirect,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "userPage", defaultValue = "0") int userPage,
			@RequestParam(value = "postPage", defaultValue = "0") int postPage,
			@RequestParam(value = "commentPage", defaultValue = "0") int commentPage,
			@RequestParam(value = "tradePage", defaultValue = "0") int tradePage,
			RedirectAttributes redirectAttributes) {
		DashboardRequest dashboardRequest = sanitizeDashboardRequest(kw, userPage, postPage, commentPage, tradePage);
		return handleAdminAction(
				authentication,
				session,
				redirectAttributes,
				"\uAC8C\uC2DC\uAE00\uC744 \uC0AD\uC81C\uD588\uC2B5\uB2C8\uB2E4.",
				redirect,
				() -> {
					BoardPost post = this.boardPostService.getPost(id);
					String mediaPath = post.getMediaPath();
					this.boardPostService.delete(post);
					this.boardMediaStorageService.delete(mediaPath);
					return buildDashboardUrl(dashboardRequest);
				});
	}

	@PostMapping("/comments/{id}/delete")
	public String deleteComment(
			@PathVariable("id") Integer id,
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "redirect", defaultValue = "") String redirect,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "userPage", defaultValue = "0") int userPage,
			@RequestParam(value = "postPage", defaultValue = "0") int postPage,
			@RequestParam(value = "commentPage", defaultValue = "0") int commentPage,
			@RequestParam(value = "tradePage", defaultValue = "0") int tradePage,
			RedirectAttributes redirectAttributes) {
		DashboardRequest dashboardRequest = sanitizeDashboardRequest(kw, userPage, postPage, commentPage, tradePage);
		return handleAdminAction(
				authentication,
				session,
				redirectAttributes,
				"\uB313\uAE00\uC744 \uC0AD\uC81C\uD588\uC2B5\uB2C8\uB2E4.",
				redirect,
				() -> {
					BoardComment comment = this.boardCommentService.getComment(id);
					this.boardCommentService.deleteByAdmin(comment);
					return buildDashboardUrl(dashboardRequest);
				});
	}

	@PostMapping("/skilltree/posts/{id}/delete")
	public String deleteSkillTreePost(
			@PathVariable("id") Integer id,
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "redirect", defaultValue = "") String redirect,
			RedirectAttributes redirectAttributes) {
		return handleAdminAction(
				authentication,
				session,
				redirectAttributes,
				"스킬트리 게시글을 삭제했습니다.",
				redirect,
				() -> {
					SkillTreePost post = this.skillTreePostService.getPost(id);
					this.skillTreePostService.delete(post);
					return buildSkillTreeListUrl(post);
				});
	}

	@PostMapping("/skilltree/comments/{id}/delete")
	public String deleteSkillTreeComment(
			@PathVariable("id") Integer id,
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "redirect", defaultValue = "") String redirect,
			RedirectAttributes redirectAttributes) {
		return handleAdminAction(
				authentication,
				session,
				redirectAttributes,
				"스킬트리 댓글을 삭제했습니다.",
				redirect,
				() -> {
					SkillTreeComment comment = this.skillTreeCommentService.getComment(id);
					String fallbackUrl = buildSkillTreeCommentRedirectUrl(comment);
					this.skillTreeCommentService.delete(comment);
					return fallbackUrl;
				});
	}

	@PostMapping("/trade-items/{id}/hide")
	public String hideTradeItem(
			@PathVariable("id") Integer id,
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "redirect", defaultValue = "") String redirect,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "userPage", defaultValue = "0") int userPage,
			@RequestParam(value = "postPage", defaultValue = "0") int postPage,
			@RequestParam(value = "commentPage", defaultValue = "0") int commentPage,
			@RequestParam(value = "tradePage", defaultValue = "0") int tradePage,
			RedirectAttributes redirectAttributes) {
		DashboardRequest dashboardRequest = sanitizeDashboardRequest(kw, userPage, postPage, commentPage, tradePage);
		return handleAdminAction(
				authentication,
				session,
				redirectAttributes,
				"\uAC70\uB798\uAE00\uC744 \uC228\uAE40 \uCC98\uB9AC\uD588\uC2B5\uB2C8\uB2E4.",
				redirect,
				() -> {
					this.tradeItemService.hideByAdmin(id);
					return buildDashboardUrl(dashboardRequest);
				});
	}

	@PostMapping("/trade-items/{id}/delete")
	public String deleteTradeItem(
			@PathVariable("id") Integer id,
			Authentication authentication,
			HttpSession session,
			@RequestParam(value = "redirect", defaultValue = "") String redirect,
			@RequestParam(value = "kw", defaultValue = "") String kw,
			@RequestParam(value = "userPage", defaultValue = "0") int userPage,
			@RequestParam(value = "postPage", defaultValue = "0") int postPage,
			@RequestParam(value = "commentPage", defaultValue = "0") int commentPage,
			@RequestParam(value = "tradePage", defaultValue = "0") int tradePage,
			RedirectAttributes redirectAttributes) {
		DashboardRequest dashboardRequest = sanitizeDashboardRequest(kw, userPage, postPage, commentPage, tradePage);
		return handleAdminAction(
				authentication,
				session,
				redirectAttributes,
				"거래글을 삭제했습니다.",
				redirect,
				() -> {
					this.tradeItemService.deleteByAdmin(id);
					return buildDashboardUrl(dashboardRequest);
				});
	}

	@ExceptionHandler(ResponseStatusException.class)
	public String handleAdminModeError(ResponseStatusException exception) {
		if (exception.getStatusCode() == HttpStatus.FORBIDDEN) {
			return "redirect:/admin";
		}
		throw exception;
	}

	// 관리자 모드 확인과 성공 메시지/복귀 리다이렉트를 한 곳에서 처리한다.
	private String handleAdminAction(
			Authentication authentication,
			HttpSession session,
			RedirectAttributes redirectAttributes,
			String successMessage,
			String redirect,
			Supplier<String> fallbackUrlSupplier) {
		requireAdminMode(authentication, session);
		String fallbackUrl = fallbackUrlSupplier.get();
		redirectAttributes.addFlashAttribute("successMessage", successMessage);
		return resolveAdminRedirect(redirect, fallbackUrl);
	}

	// 관리자 화면 공통 메타 모델을 한 번에 채운다.
	private void populateAdminPage(Model model, String pageTitle, boolean adminModeActive) {
		model.addAttribute("pageTitle", pageTitle);
		model.addAttribute("activeNav", "admin");
		model.addAttribute("adminModeActive", adminModeActive);
	}

	// 검색어와 각 목록 페이지 번호를 같은 기준으로 보정한다.
	private DashboardRequest sanitizeDashboardRequest(String kw, int userPage, int postPage, int commentPage,
			int tradePage) {
		return new DashboardRequest(
				sanitizeKeyword(kw),
				sanitizePage(userPage),
				sanitizePage(postPage),
				sanitizePage(commentPage),
				sanitizePage(tradePage));
	}

	// 관리자 액션 후 현재 목록 상태를 유지할 복귀 URL을 만든다.
	private String buildDashboardUrl(DashboardRequest dashboardRequest) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin")
				.queryParam("userPage", dashboardRequest.userPage())
				.queryParam("postPage", dashboardRequest.postPage())
				.queryParam("commentPage", dashboardRequest.commentPage())
				.queryParam("tradePage", dashboardRequest.tradePage());
		if (!dashboardRequest.keyword().isBlank()) {
			builder.queryParam("kw", dashboardRequest.keyword());
		}
		return builder.build().toUriString();
	}

	// 음수 페이지 요청은 첫 페이지로 고정한다.
	private int sanitizePage(int page) {
		return Math.max(page, 0);
	}

	// 관리자 검색어는 공백만 정리하고 비어 있는 값은 그대로 둔다.
	private String sanitizeKeyword(String kw) {
		return kw == null ? "" : kw.trim();
	}

	// 외부 이동을 막기 위해 로컬 경로만 관리자 redirect 파라미터로 허용한다.
	private String sanitizeLocalRedirect(String redirect) {
		if (redirect == null || redirect.isBlank() || !redirect.startsWith("/") || redirect.startsWith("//")) {
			return null;
		}
		return redirect;
	}

	// 명시 redirect가 없으면 안전한 fallback 경로로 복귀시킨다.
	private String resolveAdminRedirect(String redirect, String fallbackUrl) {
		String localRedirect = sanitizeLocalRedirect(redirect);
		return "redirect:" + (localRedirect != null ? localRedirect : fallbackUrl);
	}

	// 스킬트리 글 삭제 후 직업 목록으로 돌아갈 경로를 만든다.
	private String buildSkillTreeListUrl(SkillTreePost post) {
		return UriComponentsBuilder.fromPath("/skilltree/{jobSlug}")
				.buildAndExpand(post.getJob().getSlug())
				.encode()
				.toUriString();
	}

	// 스킬트리 댓글 삭제 fallback에서 재사용할 상세 경로를 만든다.
	private String buildSkillTreeDetailUrl(SkillTreePost post) {
		return UriComponentsBuilder.fromPath("/skilltree/{jobSlug}/posts/{id}")
				.buildAndExpand(post.getJob().getSlug(), post.getId())
				.encode()
				.toUriString();
	}

	// 댓글 anchor 복귀 형식을 한 곳에서 조립한다.
	private String buildSkillTreeCommentAnchorUrl(SkillTreePost post, Integer commentId) {
		return buildSkillTreeDetailUrl(post) + "#comment_" + commentId;
	}

	// 답글 삭제 시 부모 anchor, 일반 댓글 삭제 시 상세 화면으로 복귀시킨다.
	private String buildSkillTreeCommentRedirectUrl(SkillTreeComment comment) {
		SkillTreeComment parentComment = comment.getParentComment();
		if (parentComment != null) {
			return buildSkillTreeCommentAnchorUrl(comment.getPost(), parentComment.getId());
		}
		return buildSkillTreeDetailUrl(comment.getPost());
	}

	// 세션 기반 관리자 모드 판정을 컨트롤러 내부에서 일관되게 재사용한다.
	private boolean isAdminMode(Authentication authentication, HttpSession session) {
		return this.adminModeService.isAdminMode(authentication, session);
	}

	// 관리자 모드가 아니면 운영 액션 진입 자체를 막는다.
	private void requireAdminMode(Authentication authentication, HttpSession session) {
		if (!isAdminMode(authentication, session)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin mode required");
		}
	}

	// 관리자 목록 탭별 상태를 함께 들고 다니는 보정 결과이다.
	private record DashboardRequest(
			String keyword,
			int userPage,
			int postPage,
			int commentPage,
			int tradePage) {
	}
}
