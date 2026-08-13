package com.team3.gudit.goods.service.component;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.goods.constant.PathConstant;
import com.team3.gudit.goods.exception.ImageErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
public class ImageStorageManager {

    private static final String DEFAULT_IMAGE_URL = "/thumbnails/default.png";
    private static final String IMAGE_URL_PREFIX = "/thumbnails/";

    public String store(MultipartFile image) {
        if(image == null || image.isEmpty()) {
            return "/thumbnails/default.png";
        }
        validate(image);
        try {
            Files.createDirectories(PathConstant.THUMBNAIL_DIRECTORY);
            String originalFilename = sanitizeFilename(image.getOriginalFilename());
            String storedFilename = UUID.randomUUID() + "-" + originalFilename;

            Path targetPath = PathConstant.THUMBNAIL_DIRECTORY
                    .resolve(storedFilename)
                    .normalize();

            if(!targetPath.startsWith(PathConstant.THUMBNAIL_DIRECTORY)) {
                throw new BusinessException(ImageErrorCode.INVALID_IMAGE_FILENAME);
            }
            image.transferTo(targetPath);

            return IMAGE_URL_PREFIX + storedFilename;

        } catch (IOException e) {
            throw new BusinessException(ImageErrorCode.IMAGE_STORAGE_FAILED, e);
        }
    }

    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (DEFAULT_IMAGE_URL.equals(imageUrl)) {
            return;
        }
        if (!imageUrl.startsWith(IMAGE_URL_PREFIX)) {
            log.warn("올바르지 않은 이미지 경로입니다. imageUrl={}", imageUrl);
            return;
        }
        String storedFilename =
                imageUrl.substring(IMAGE_URL_PREFIX.length());
        Path targetPath = PathConstant.THUMBNAIL_DIRECTORY
                .resolve(storedFilename)
                .normalize();
        if (!targetPath.startsWith(PathConstant.THUMBNAIL_DIRECTORY)) {
            log.warn("썸네일 디렉터리를 벗어난 이미지 경로입니다. imageUrl={}", imageUrl);
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(targetPath);
            if (!deleted) {
                log.warn("삭제할 이미지 파일이 존재하지 않습니다. path={}", targetPath);
            }
        } catch (IOException e) {
            log.error(
                    "이미지 파일 삭제에 실패했습니다. 메뉴 삭제는 계속 진행합니다. path={}",
                    targetPath,
                    e
            );
        }
    }

    private void validate(MultipartFile image) {

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ImageErrorCode.INVALID_IMAGE_TYPE);
        }
    }
    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "thumbnail";
        }
        String filename = Paths.get(originalFilename)
                .getFileName()
                .toString();
        return filename
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-zA-Z0-9가-힣._-]", "");
    }

}
