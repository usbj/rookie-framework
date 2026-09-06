package com.rookie.system.controller;

import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.vo.FileUploadVo;
import com.rookie.system.service.SysFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传/下载 Controller（轻量文件模块，不落库）。
 * <p>
 * 上传文件按「UUID + 原扩展名」存储到本地可配置路径（{@code rookie.upload.path}），
 * 返回存储名；下载按存储名流式返回，文件名白名单校验防路径穿越。
 * 接口仅需登录（SecurityConfig 全局兜底），不加按钮权限、不建菜单；
 * 不标注 {@link com.rookie.common.annotation.Log}：操作日志切面会序列化方法参数，
 * MultipartFile 的 getBytes() 会把整个文件读入内存再转 JSON，超大文件下开销不可接受。
 */
@RestController
@RequestMapping("/sys/file")
@Tag(name = "文件管理", description = "文件上传与下载（本地路径存储，可配置）")
public class SysFileController {

    @Autowired
    SysFileService sysFileService;

    /**
     * 上传文件（multipart/form-data，字段名 file）。
     * 返回存储名等元信息，前端凭存储名调用下载接口。
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public Result<FileUploadVo> uploadFile(@RequestParam("file") MultipartFile file) {
        FileUploadVo uploadVo = sysFileService.uploadFile(file);
        return Result.success(uploadVo);
    }

    /**
     * 下载文件：按存储名流式返回（attachment）。
     * originalName 可选，仅用于浏览器保存时的展示文件名，不做存储定位。
     */
    @GetMapping("/download/{storedName}")
    @Operation(summary = "下载文件")
    public ResponseEntity<Resource> downloadFile(@PathVariable String storedName,
                                                 @RequestParam(value = "originalName", required = false) String originalName) {
        return sysFileService.downloadFile(storedName, originalName);
    }
}
