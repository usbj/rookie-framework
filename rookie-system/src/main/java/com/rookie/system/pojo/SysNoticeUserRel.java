package com.rookie.system.pojo;

public class SysNoticeUserRel {

    private Long id;

    private Long noticeId;

    private Long userId;

    public SysNoticeUserRel() {
    }

    public SysNoticeUserRel(Long id, Long noticeId, Long userId) {
        this.id = id;
        this.noticeId = noticeId;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}