package com.mysite.sbb.board;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardPostForm {
	@NotEmpty(message = "제목은 필수항목입니다.")
	@Size(max = 200)
	private String subject;

	@NotEmpty(message = "내용은 필수항목입니다.")
	private String content;

	@Size(max = 500, message = "유튜브 링크는 500자 이하로 입력해 주세요.")
	private String youtubeUrl;

	private MultipartFile mediaFile;

	private boolean removeMedia;
}