package com.rookie.system.pojo.vo;

/**
 * 文件上传结果 VO（轻量文件模块，不落库）。
 * <p>
 * 上传接口只返回文件元信息，文件实体按 {@code storedName} 落在本地配置路径，
 * 前端/调用方后续凭 {@code storedName} 走下载接口取文件。
 */
public class FileUploadVo {

    /** 存储名（UUID + 原扩展名，如 a1b2c3.png），下载接口按此名取文件；只含字母数字 ._- */
    private String storedName;

    /** 原始文件名（仅用于展示与下载命名，不参与存储定位） */
    private String originalName;

    /** 文件大小（字节） */
    private Long size;

    /** 小写扩展名（无扩展名时为空字符串） */
    private String ext;

    public FileUploadVo() {
    }

    public FileUploadVo(String storedName, String originalName, Long size, String ext) {
        this.storedName = storedName;
        this.originalName = originalName;
        this.size = size;
        this.ext = ext;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }
}
