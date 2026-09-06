package com.rookie.system.service;

import com.rookie.system.pojo.vo.FileUploadVo;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务（轻量文件模块，不落库）。
 * <p>
 * 职责：文件写入本地可配置路径、按存储名流式读取、删除存储文件。
 * 上传根路径由配置项 {@code rookie.upload.path} 指定（application.yml，改后重启生效），
 * 文件按「根路径/年/月/日」三层目录分层存储，用户头像落在根路径下 {@code avatar/年/月/日}。
 * 存储名 = 系统名称（{@code rookie.system-name}）+ 年月日 + 毫秒时间戳 + 扩展名，
 * 只允许字母数字与 ._- 字符；年月日与毫秒同源于上传时刻，下载/删除时由存储名反推日期目录。
 */
public interface SysFileService {

    /**
     * 上传普通文件到上传根目录（不落库）。
     *
     * @param file 待上传文件（multipart），空文件直接拒绝
     * @return 上传结果（存储名 / 原始文件名 / 大小 / 扩展名）
     */
    FileUploadVo uploadFile(MultipartFile file);

    /**
     * 按存储名流式下载普通文件（attachment 响应）。
     *
     * @param storedName   存储名（只允许字母数字 ._-）
     * @param originalName 可选，下载时展示的原始文件名（仅用于 Content-Disposition 命名，不做存储定位）
     * @return 文件流响应；文件不存在或名称非法时抛 {@link com.rookie.common.exception.ServiceException}
     */
    ResponseEntity<Resource> downloadFile(String storedName, String originalName);

    /**
     * 上传用户头像到上传根目录下 {@code avatar/} 子目录（不落库）。
     * 仅允许图片类型（png/jpg/jpeg/gif/webp），大小上限 2MB。
     *
     * @param file 待上传的图片文件
     * @return 上传结果（存储名等）
     */
    FileUploadVo uploadAvatar(MultipartFile file);

    /**
     * 按存储名读取头像图片（inline 响应，供前端 blob 加载展示）。
     *
     * @param storedName 头像存储名（只允许字母数字 ._-）
     * @return 图片流响应；文件不存在或名称非法时抛 {@link com.rookie.common.exception.ServiceException}
     */
    ResponseEntity<Resource> downloadAvatar(String storedName);

    /**
     * 删除 {@code avatar/} 子目录下的头像文件（更换头像时清理旧文件）。
     * 文件不存在时静默忽略（幂等），不会抛异常。
     *
     * @param storedName 头像存储名
     */
    void deleteAvatar(String storedName);
}
