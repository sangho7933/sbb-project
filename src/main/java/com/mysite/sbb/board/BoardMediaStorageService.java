package com.mysite.sbb.board;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BoardMediaStorageService {

	private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
			"image/jpeg",
			"image/png",
			"image/gif",
			"image/webp");

	private static final Set<String> VIDEO_CONTENT_TYPES = Set.of(
			"video/mp4",
			"video/webm",
			"video/ogg",
			"video/quicktime",
			"video/x-matroska");

	private final Path uploadRoot;

	public BoardMediaStorageService(@Value("${app.upload.dir:./data/uploads}") String uploadDir) {
		this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
	}

	public StoredBoardMedia store(BoardCategory category, MultipartFile mediaFile) throws IOException {
		if (mediaFile == null || mediaFile.isEmpty()) {
			return null;
		}
		if (!category.isMediaUploadSupported()) {
			throw new IllegalArgumentException("이 게시판은 미디어 업로드를 지원하지 않습니다.");
		}

		String originalName = mediaFile.getOriginalFilename() == null ? "" : mediaFile.getOriginalFilename().trim();
		String contentType = resolveContentType(mediaFile, originalName);
		validateSupportedType(contentType);

		Path categoryDirectory = this.uploadRoot.resolve(category.getCode()).normalize();
		Files.createDirectories(categoryDirectory);

		String extension = extractExtension(originalName);
		String storedFileName = UUID.randomUUID() + extension;
		Path target = categoryDirectory.resolve(storedFileName).normalize();
		if (!target.startsWith(this.uploadRoot)) {
			throw new IllegalArgumentException("업로드 경로가 올바르지 않습니다.");
		}

		Files.copy(mediaFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
		return new StoredBoardMedia("/uploads/" + category.getCode() + "/" + storedFileName, originalName, contentType);
	}

	public void delete(String mediaPath) {
		if (mediaPath == null || mediaPath.isBlank() || !mediaPath.startsWith("/uploads/")) {
			return;
		}
		String relativePath = mediaPath.substring("/uploads/".length()).replace('/', java.io.File.separatorChar);
		Path target = this.uploadRoot.resolve(relativePath).normalize();
		if (!target.startsWith(this.uploadRoot)) {
			return;
		}
		try {
			Files.deleteIfExists(target);
		} catch (IOException ignored) {
		}
	}

	private String resolveContentType(MultipartFile mediaFile, String originalName) {
		String contentType = mediaFile.getContentType();
		if (contentType != null && !contentType.isBlank()) {
			return contentType.toLowerCase(Locale.ROOT);
		}
		String extension = extractExtension(originalName).toLowerCase(Locale.ROOT);
		return switch (extension) {
		case ".jpg", ".jpeg" -> "image/jpeg";
		case ".png" -> "image/png";
		case ".gif" -> "image/gif";
		case ".webp" -> "image/webp";
		case ".mp4" -> "video/mp4";
		case ".webm" -> "video/webm";
		case ".ogv", ".ogg" -> "video/ogg";
		case ".mov" -> "video/quicktime";
		case ".mkv" -> "video/x-matroska";
		default -> throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
		};
	}

	private void validateSupportedType(String contentType) {
		if (IMAGE_CONTENT_TYPES.contains(contentType) || VIDEO_CONTENT_TYPES.contains(contentType)) {
			return;
		}
		throw new IllegalArgumentException("이미지나 동영상 파일만 업로드할 수 있습니다.");
	}

	private String extractExtension(String filename) {
		int extensionIndex = filename.lastIndexOf('.');
		if (extensionIndex < 0) {
			return "";
		}
		return filename.substring(extensionIndex);
	}
}
