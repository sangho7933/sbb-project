package com.mysite.sbb.board;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mysite.sbb.comment.BoardComment;
import com.mysite.sbb.user.SiteUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "question")
public class BoardPost {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(length = 200)
	private String subject;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Column(length = 500)
	private String mediaPath;

	@Column(length = 255)
	private String mediaOriginalName;

	@Column(length = 100)
	private String mediaContentType;

	@Column(length = 500)
	private String youtubeUrl;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private BoardCategory category = BoardCategory.FREE_BOARD;

	private Integer viewCount = 0;

	private LocalDateTime createDate;

	@OrderBy("createDate asc")
	@OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE)
	private List<BoardComment> commentList = new ArrayList<>();

	@Transient
	private long commentCount;

	@ManyToOne
	@JoinColumn(name = "author_id")
	private SiteUser author;

	private LocalDateTime modifyDate;

	@ManyToMany
	@JoinTable(name = "question_voter", joinColumns = @JoinColumn(name = "question_id"), inverseJoinColumns = @JoinColumn(name = "voter_id"))
	private Set<SiteUser> voter = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "question_dislike_voter", joinColumns = @JoinColumn(name = "question_id"), inverseJoinColumns = @JoinColumn(name = "voter_id"))
	private Set<SiteUser> dislikeVoter = new HashSet<>();

	@PrePersist
	void assignDefaults() {
		if (this.category == null) {
			this.category = BoardCategory.FREE_BOARD;
		}
		if (this.viewCount == null) {
			this.viewCount = 0;
		}
	}

	public boolean hasMedia() {
		return this.mediaPath != null && !this.mediaPath.isBlank();
	}

	public boolean hasVideoMedia() {
		return hasMedia() && this.mediaContentType != null && this.mediaContentType.startsWith("video/");
	}

	public boolean hasImageMedia() {
		return hasMedia() && this.mediaContentType != null && this.mediaContentType.startsWith("image/");
	}

	public boolean hasYoutubeVideo() {
		String videoId = getYoutubeVideoId();
		return videoId != null && !videoId.isBlank();
	}

	public String getYoutubeEmbedUrl() {
		String videoId = getYoutubeVideoId();
		return videoId == null ? null : "https://www.youtube.com/embed/" + videoId;
	}

	public String getYoutubeVideoId() {
		if (this.youtubeUrl == null || this.youtubeUrl.isBlank()) {
			return null;
		}

		String url = this.youtubeUrl.trim();
		int watchIndex = url.indexOf("v=");
		if (watchIndex >= 0) {
			String videoId = url.substring(watchIndex + 2);
			int ampIndex = videoId.indexOf('&');
			return ampIndex >= 0 ? videoId.substring(0, ampIndex) : videoId;
		}

		String shortMarker = "youtu.be/";
		int shortIndex = url.indexOf(shortMarker);
		if (shortIndex >= 0) {
			String videoId = url.substring(shortIndex + shortMarker.length());
			int queryIndex = videoId.indexOf('?');
			return queryIndex >= 0 ? videoId.substring(0, queryIndex) : videoId;
		}

		String embedMarker = "/embed/";
		int embedIndex = url.indexOf(embedMarker);
		if (embedIndex >= 0) {
			String videoId = url.substring(embedIndex + embedMarker.length());
			int slashIndex = videoId.indexOf('/');
			return slashIndex >= 0 ? videoId.substring(0, slashIndex) : videoId;
		}

		String shortsMarker = "/shorts/";
		int shortsIndex = url.indexOf(shortsMarker);
		if (shortsIndex >= 0) {
			String videoId = url.substring(shortsIndex + shortsMarker.length());
			int slashIndex = videoId.indexOf('/');
			int queryIndex = videoId.indexOf('?');
			int cutIndex = slashIndex >= 0 ? slashIndex : queryIndex;
			return cutIndex >= 0 ? videoId.substring(0, cutIndex) : videoId;
		}

		return null;
	}
}
