package com.rookie.system.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.rookie.common.exception.ServiceException;
import com.rookie.system.pojo.vo.FileUploadVo;
import com.rookie.system.service.SysFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件存储服务实现（轻量文件模块，不落库）。
 * <p>
 * 存储约定：
 * <ul>
 *   <li>上传根路径 = 配置项 {@code rookie.upload.path}（application.yml），相对路径按应用工作目录解析；</li>
 *   <li>文件按「根路径/年/月/日」三层目录分层存储，头像多一层 {@code avatar/} 前缀（avatar/年/月/日）；</li>
 *   <li>存储名 = 系统名称（{@code rookie.system-name}）+ 年月日（yyyyMMdd）+ 毫秒时间戳 + 扩展名，
 *       例如 {@code rookie202608152011191234567.png}。年月日与毫秒时间戳同源于上传时刻，
 *       下载/删除时从存储名提取毫秒时间戳反推日期目录，无需额外记录路径；</li>
 *   <li>存储名只允许字母数字与 ._-（{@link #STORED_NAME_PATTERN}），
 *       下载/删除前统一校验并做 normalize + startsWith 双重防路径穿越。</li>
 * </ul>
 * 校验失败统一抛 {@link ServiceException}（500 + 可读消息），由全局异常处理器转成 Result 返回；
 * 因此所有校验必须在写响应之前完成，避免响应已提交后无法再返回 JSON 错误。
 */
@Service
public class SysFileServiceImpl implements SysFileService {

    private static final Logger log = LoggerFactory.getLogger(SysFileServiceImpl.class);

    /** 头像子目录（相对上传根路径） */
    private static final String AVATAR_SUB_DIR = "avatar";

    /** 头像大小上限：2MB */
    private static final long AVATAR_MAX_SIZE = 2 * 1024 * 1024L;

    /** 允许的头像图片扩展名（小写） */
    private static final Set<String> AVATAR_ALLOWED_EXTS = new HashSet<>(
            Arrays.asList("png", "jpg", "jpeg", "gif", "webp"));

    /** 存储名校验：只允许字母数字与 ._-，天然排除 / \ 与空白，杜绝路径穿越 */
    private static final Pattern STORED_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    /**
     * 存储名日期/时间戳解析：末尾 8 位日期 + 13 位毫秒时间戳（+ 可选扩展名）。
     * 上传时年月日与毫秒同源，此处从存储名反推日期目录；find 会尝试所有起点，
     * 系统名称即使含数字也能正确匹配到「日期 + 毫秒」这 21 位连续数字。
     */
    private static final Pattern STORED_NAME_DATE_PATTERN =
            Pattern.compile("(\\d{8})(\\d{13})(?:\\.[a-zA-Z0-9]+)?$");

    /** 存储名中的日期格式（yyyyMMdd） */
    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 上传根路径（application.yml 配置，可改，改后重启生效；默认 ./upload） */
    @Value("${rookie.upload.path:./upload}")
    private String uploadPath;

    /** 系统名称（application.yml 配置，作为上传文件名的前缀，默认 rookie） */
    @Value("${rookie.system-name:rookie}")
    private String systemName;

    @Override
    public FileUploadVo uploadFile(MultipartFile file) {
        return store(file, null);
    }

    @Override
    public ResponseEntity<Resource> downloadFile(String storedName, String originalName) {
        // 下载命名取原始文件名时只取 basename，防止原始文件名里夹带路径影响响应头
        String downloadName = StrUtil.isNotBlank(originalName)
                ? FileUtil.getName(originalName)
                : storedName;
        return buildFileResponse(storedName, null, downloadName, true);
    }

    @Override
    public FileUploadVo uploadAvatar(MultipartFile file) {
        return store(file, AVATAR_SUB_DIR);
    }

    @Override
    public ResponseEntity<Resource> downloadAvatar(String storedName) {
        // 头像按 inline 内联返回（浏览器可直接展示），不做附件下载命名
        return buildFileResponse(storedName, AVATAR_SUB_DIR, null, false);
    }

    @Override
    public void deleteAvatar(String storedName) {
        if (StrUtil.isBlank(storedName)) {
            return;
        }
        Path filePath = resolveFilePath(storedName, AVATAR_SUB_DIR);
        // 文件不存在时静默忽略（幂等删除），存在则尝试删除，失败仅告警不抛错（不影响主流程）
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除头像文件失败，storedName={} >>> {}", storedName, e.getMessage());
        }
    }

    /**
     * 通用存储：校验 → 生成存储名（系统名+年月日+毫秒时间戳）→ 按 年/月/日 分层落盘。
     * 扩展名只保留字母数字（防注入），原始文件名仅用于展示。
     *
     * @param file   待存储文件
     * @param subDir 子目录（null 表示根目录；avatar 表示 avatar/ 前缀）
     * @return 上传结果
     */
    private FileUploadVo store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(500, "上传文件不能为空");
        }
        String originalName = file.getOriginalFilename() == null ? "" : FileUtil.getName(file.getOriginalFilename());
        String ext = resolveExt(originalName);
        if (AVATAR_SUB_DIR.equals(subDir)) {
            checkAvatar(file, ext);
        }

        // 存储名 = 系统名称 + 年月日(yyyyMMdd) + 毫秒时间戳 + 扩展名；
        // 年月日与毫秒同源于上传时刻，目录 = 根/年/月/日（+ 子目录前缀），下载时由时间戳反推。
        // 极端并发下同一毫秒可能重名，冲突时时间戳 +1 重试（同毫秒内不跨天，日期不变）。
        Path root = resolveUploadRoot();
        String storedName;
        Path targetFile;
        long millis = System.currentTimeMillis();
        while (true) {
            LocalDate date = toLocalDate(millis);
            String datePart = date.format(DATE_PATTERN);
            storedName = systemName + datePart + millis + (ext.isEmpty() ? "" : "." + ext);
            targetFile = resolveDailyDir(root, subDir, date).resolve(storedName);
            if (!Files.exists(targetFile)) {
                break;
            }
            millis++;
        }

        try {
            Files.createDirectories(targetFile.getParent());
            // 绝对路径落盘：MultipartFile.transferTo 对相对路径的行为因容器而异，统一转绝对路径
            file.transferTo(targetFile.toAbsolutePath());
        } catch (IOException e) {
            throw new ServiceException(500, "文件保存失败", e.getMessage());
        }
        return new FileUploadVo(storedName, originalName, file.getSize(), ext);
    }

    /**
     * 构建文件响应：校验存储名合法性与文件存在性后，按附件/内联方式流式返回。
     * 所有校验在设置响应之前完成，保证失败时全局异常处理器还能正常返回 JSON。
     *
     * @param storedName   存储名
     * @param subDir       子目录（null 表示根目录）
     * @param downloadName 附件下载名（null 表示不设置 Content-Disposition）
     * @param asAttachment 是否按附件返回
     * @return 文件流响应
     */
    private ResponseEntity<Resource> buildFileResponse(String storedName, String subDir,
                                                       String downloadName, boolean asAttachment) {
        Path filePath = resolveFilePath(storedName, subDir);
        if (!Files.isRegularFile(filePath)) {
            throw new ServiceException(500, "文件不存在或已被删除");
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(resolveContentType(filePath));
        if (asAttachment && downloadName != null) {
            // RFC 5987：UTF-8 编码文件名，兼容中文；URLEncoder 的空格为 +，回退为 %20
            String encoded = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        }
        // FileSystemResource 自动提供 Content-Length，由 Spring 资源转换器输出流
        return builder.body(new FileSystemResource(filePath));
    }

    /**
     * 解析并校验存储名对应的物理路径：
     * 1. 名称白名单校验；
     * 2. 从存储名提取毫秒时间戳并反推 年/月/日 目录（与文件名内嵌日期比对，防手工伪造）；
     * 3. normalize 后必须仍位于目标目录内（防 ".." 绕出，白名单理论上已排除）。
     *
     * @param storedName 存储名（系统名 + yyyyMMdd + 毫秒时间戳 [+ 扩展名]）
     * @param subDir     子目录（null 表示根目录；avatar 表示 avatar/ 前缀）
     * @return 规范化后的绝对物理路径
     */
    private Path resolveFilePath(String storedName, String subDir) {
        if (StrUtil.isBlank(storedName) || !STORED_NAME_PATTERN.matcher(storedName).matches()
                || ".".equals(storedName) || "..".equals(storedName)) {
            throw new ServiceException(500, "非法的文件名称");
        }
        Matcher matcher = STORED_NAME_DATE_PATTERN.matcher(storedName);
        if (!matcher.find()) {
            throw new ServiceException(500, "非法的文件名称");
        }
        long millis;
        try {
            millis = Long.parseLong(matcher.group(2));
        } catch (NumberFormatException e) {
            throw new ServiceException(500, "非法的文件名称");
        }
        LocalDate date = toLocalDate(millis);
        // 时间戳反推的日期必须与存储名内嵌的 8 位日期一致，防止手工构造不一致的路径
        if (!date.format(DATE_PATTERN).equals(matcher.group(1))) {
            throw new ServiceException(500, "非法的文件名称");
        }
        Path root = resolveUploadRoot();
        Path targetDir = resolveDailyDir(root, subDir, date);
        Path filePath = targetDir.resolve(storedName).normalize();
        // 双重校验：normalize 后仍必须以目标目录为前缀，防止 ".." 绕出目录（白名单理论上已排除）
        if (!filePath.startsWith(targetDir)) {
            throw new ServiceException(500, "非法的文件名称");
        }
        return filePath;
    }

    /**
     * 拼装「根路径/子目录(可选)/年/月/日」目录。
     *
     * @param root   上传根路径（已规范化）
     * @param subDir 子目录（null 表示不加子目录）
     * @param date   日期（决定 年/月/日 三层）
     * @return 规范化目录路径
     */
    private Path resolveDailyDir(Path root, String subDir, LocalDate date) {
        return root
                .resolve(subDir == null ? "" : subDir)
                .resolve(String.valueOf(date.getYear()))
                .resolve(String.format("%02d", date.getMonthValue()))
                .resolve(String.format("%02d", date.getDayOfMonth()))
                .normalize();
    }

    /**
     * 毫秒时间戳转本地日期（与上传时同一时区口径，保证反推目录一致）。
     */
    private LocalDate toLocalDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * 解析上传根路径为规范化绝对路径（相对路径按应用工作目录解析）。
     */
    private Path resolveUploadRoot() {
        return Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    /**
     * 从原始文件名提取小写扩展名；只保留字母数字（如 "tar.gz" 取 "gz"），非法字符视为无扩展名。
     */
    private String resolveExt(String originalName) {
        String ext = FileUtil.extName(originalName);
        if (ext == null) {
            return "";
        }
        ext = ext.toLowerCase();
        return ext.matches("^[a-zA-Z0-9]+$") ? ext : "";
    }

    /**
     * 头像专用校验：大小上限 2MB + 仅允许图片扩展名。
     */
    private void checkAvatar(MultipartFile file, String ext) {
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new ServiceException(500, "头像大小不能超过2MB");
        }
        if (!AVATAR_ALLOWED_EXTS.contains(ext)) {
            throw new ServiceException(500, "头像仅支持 png/jpg/jpeg/gif/webp 格式");
        }
    }

    /**
     * 按扩展名推断 Content-Type；未知类型统一按二进制流处理。
     */
    private MediaType resolveContentType(Path filePath) {
        String ext = FileUtil.extName(filePath.getFileName().toString());
        if (ext != null) {
            String lower = ext.toLowerCase();
            if ("png".equals(lower) || "jpg".equals(lower) || "jpeg".equals(lower)
                    || "gif".equals(lower) || "webp".equals(lower) || "bmp".equals(lower)
                    || "ico".equals(lower)) {
                return MediaType.parseMediaType("image/" + lower);
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
