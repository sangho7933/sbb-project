package com.mysite.sbb.skilltree.comment;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mysite.sbb.skilltree.SkillTreePost;
import com.mysite.sbb.user.SiteUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "skilltree_comment")
public class SkillTreeComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(columnDefinition = "TEXT")
	private String content;

	private LocalDateTime createDate;

	@ManyToOne
	@JoinColumn(name = "post_id")
	private SkillTreePost post;

	@ManyToOne
	@JoinColumn(name = "author_id")
	private SiteUser author;

	private LocalDateTime modifyDate;

	@ManyToMany
	@JoinTable(name = "skilltree_comment_voter", joinColumns = @JoinColumn(name = "comment_id"), inverseJoinColumns = @JoinColumn(name = "voter_id"))
	private Set<SiteUser> voter = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "skilltree_comment_dislike_voter", joinColumns = @JoinColumn(name = "comment_id"), inverseJoinColumns = @JoinColumn(name = "voter_id"))
	private Set<SiteUser> dislikeVoter = new HashSet<>();

	@ManyToOne
	@JoinColumn(name = "parent_id")
	private SkillTreeComment parentComment;

	@OrderBy("createDate asc")
	@OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SkillTreeComment> childComments;

	private boolean deleted;
}
