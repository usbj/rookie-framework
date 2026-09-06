package com.rookie.system.pojo.vo;

import com.rookie.common.pojo.entity.SysNoticeGroup;

import java.util.List;

public class SysNoticeVo {

    private Long noticeId;

    private String title;

    private String content;

    private String noticeType;

    private String level;

    private String publishScope;

    private String status;

    private Integer isTop;

    private Integer needConfirm;

    private String publishTime;

    private String expireTime;

    private String routePath;

    private String remark;

    private String createBy;

    private String createTime;

    private List<Long> groupIds;

    private List<SysNoticeGroup> noticeGroups;

    /**
     * 发布范围为"指定成员"(publish_scope=USER)时携带的目标用户主键集合，提交时使用。
     */
    private List<Long> targetUserIds;

    /**
     * 详情回显用：按 sys_notice_user_rel 关联 sys_user 查出的目标用户展示信息，
     * 供编辑弹窗回显已选成员的昵称/用户名等。
     */
    private List<NoticeTargetUserVo> targetUsers;

    private Boolean hasRead;

    private Boolean hasConfirmed;

    public SysNoticeVo() {
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(String noticeType) {
        this.noticeType = noticeType;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getPublishScope() {
        return publishScope;
    }

    public void setPublishScope(String publishScope) {
        this.publishScope = publishScope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getIsTop() {
        return isTop;
    }

    public void setIsTop(Integer isTop) {
        this.isTop = isTop;
    }

    public Integer getNeedConfirm() {
        return needConfirm;
    }

    public void setNeedConfirm(Integer needConfirm) {
        this.needConfirm = needConfirm;
    }

    public String getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(String publishTime) {
        this.publishTime = publishTime;
    }

    public String getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public List<Long> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<Long> groupIds) {
        this.groupIds = groupIds;
    }

    public List<SysNoticeGroup> getNoticeGroups() {
        return noticeGroups;
    }

    public void setNoticeGroups(List<SysNoticeGroup> noticeGroups) {
        this.noticeGroups = noticeGroups;
    }

    public List<Long> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<Long> targetUserIds) {
        this.targetUserIds = targetUserIds;
    }

    public List<NoticeTargetUserVo> getTargetUsers() {
        return targetUsers;
    }

    public void setTargetUsers(List<NoticeTargetUserVo> targetUsers) {
        this.targetUsers = targetUsers;
    }

    public Boolean getHasRead() {
        return hasRead;
    }

    public void setHasRead(Boolean hasRead) {
        this.hasRead = hasRead;
    }

    public Boolean getHasConfirmed() {
        return hasConfirmed;
    }

    public void setHasConfirmed(Boolean hasConfirmed) {
        this.hasConfirmed = hasConfirmed;
    }
}
