package com.lawyus.reservation.integration.file;

import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.StpStaffUtil;
import com.lawyus.reservation.common.security.StpUserUtil;
import com.lawyus.reservation.integration.storage.RustfsStorageService;
import com.lawyus.reservation.integration.wx.ContentSecurityService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 图片上传接口：顾客或商家登录后均可上传，仅支持 jpg/png/webp，大小上限 2MB。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final RustfsStorageService rustfsStorageService;
    private final ContentSecurityService contentSecurityService;

    public FileController(RustfsStorageService rustfsStorageService, ContentSecurityService contentSecurityService) {
        this.rustfsStorageService = rustfsStorageService;
        this.contentSecurityService = contentSecurityService;
    }

    @PostMapping("/upload")
    public Result<UploadResult> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (!StpUserUtil.stpLogic.isLogin() && !StpStaffUtil.stpLogic.isLogin()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "仅支持 jpg/png/webp 图片");
        }
        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件为空");
        }
        contentSecurityService.ensureImageSafe(bytes, extension);
        return Result.ok(new UploadResult(rustfsStorageService.upload(bytes, file.getContentType(), extension)));
    }

    public record UploadResult(String url) {
    }
}
