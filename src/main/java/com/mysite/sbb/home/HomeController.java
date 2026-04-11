package com.mysite.sbb.home;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mysite.sbb.board.BoardCategory;
import com.mysite.sbb.board.BoardPostService;
import com.mysite.sbb.skilltree.SkillTreePostService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
public class HomeController {

	private final BoardPostService boardPostService;
	private final SkillTreePostService skillTreePostService;

	@GetMapping("/a2c")
	@ResponseBody
	public String index() {
		return "아이온2 커뮤니티 A2C";
	}

	@GetMapping("/")
	public String root(Model model) {
		model.addAttribute("pageTitle", "아이온2 커뮤니티 | A2C");
		model.addAttribute("activeNav", "home");
		model.addAttribute("featuredVideos", FeaturedVideo.defaultLineup());
		model.addAttribute("freeBoardPosts", this.boardPostService.getTopViewedPosts(BoardCategory.FREE_BOARD, 3));
		model.addAttribute("guildRecruitmentPosts",
				this.boardPostService.getTopViewedPosts(BoardCategory.GUILD_RECRUITMENT, 3));
		model.addAttribute("bossGuidePosts", this.boardPostService.getTopViewedPosts(BoardCategory.BOSS_GUIDE, 3));
		model.addAttribute("highlightPosts", this.boardPostService.getTopViewedPosts(BoardCategory.HIGHLIGHT, 3));
		model.addAttribute("skillTreePostCounts", this.skillTreePostService.getPostCounts());
		model.addAttribute("recentSkillTreePosts", this.skillTreePostService.getRecentPosts(5));
		model.addAttribute("highlightShowcase", this.boardPostService.getPopularPosts(BoardCategory.HIGHLIGHT, 3));
		model.addAttribute("boardSections", List.of(
				BoardCategory.FREE_BOARD,
				BoardCategory.GUILD_RECRUITMENT,
				BoardCategory.BOSS_GUIDE,
				BoardCategory.HIGHLIGHT));
		return "main_home";
	}
}
