package com.mysite.sbb.skilltree;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mysite.sbb.skilltree.comment.SkillTreeComment;
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
@Table(name = "skilltree_post")
public class SkillTreePost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(length = 200)
	private String subject;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private SkillTreeJob job;

	private Integer viewCount = 0;

	private LocalDateTime createDate;

	@OrderBy("createDate asc")
	@OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE)
	private List<SkillTreeComment> commentList;

	@Transient
	private long commentCount;

	@ManyToOne
	@JoinColumn(name = "author_id")
	private SiteUser author;

	private LocalDateTime modifyDate;

	@ManyToMany
	@JoinTable(name = "skilltree_post_voter", joinColumns = @JoinColumn(name = "post_id"), inverseJoinColumns = @JoinColumn(name = "voter_id"))
	private Set<SiteUser> voter = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "skilltree_post_dislike_voter", joinColumns = @JoinColumn(name = "post_id"), inverseJoinColumns = @JoinColumn(name = "voter_id"))
	private Set<SiteUser> dislikeVoter = new HashSet<>();

	@PrePersist
	void assignDefaults() {
		if (this.viewCount == null) {
			this.viewCount = 0;
		}
	}
}
