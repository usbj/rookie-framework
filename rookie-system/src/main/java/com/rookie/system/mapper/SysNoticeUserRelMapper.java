package com.rookie.system.mapper;

import com.rookie.system.pojo.SysNoticeUserRel;
import com.rookie.system.pojo.vo.NoticeTargetUserVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysNoticeUserRelMapper {

    Boolean insertSysNoticeUserRel(List<SysNoticeUserRel> sysNoticeUserRels);

    Boolean deleteSysNoticeUserRelByNoticeId(Long noticeId);

    List<SysNoticeUserRel> getSysNoticeUserRelByNoticeId(Long noticeId);

    /**
     * 按通知主键关联 sys_user 查询目标成员展示信息（userId/username/nickName/phoneNumber/status），
     * 供通知详情与编辑弹窗回显已选指定成员使用。
     */
    List<NoticeTargetUserVo> getTargetUsersByNoticeId(Long noticeId);
}