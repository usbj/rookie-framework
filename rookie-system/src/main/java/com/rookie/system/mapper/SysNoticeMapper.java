package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysNotice;
import com.rookie.system.pojo.quarry.NoticeQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysNoticeMapper {

    List<SysNotice> quarrySysNotice(NoticeQuarry quarry);

    Boolean addSysNotice(SysNotice sysNotice);

    Boolean editSysNoticeInfo(SysNotice sysNotice);

    Boolean deleteSysNoticeById(Long noticeId);

    SysNotice getSysNoticeInfoById(Long noticeId);

    Boolean softDeleteSysNotice(Long noticeId);

    List<SysNotice> getNoticesForUser(Long userId);

    /**
     * 统计当前用户可见且未读的通知数（懒加载后铃铛徽标独立计数，不能依赖已加载分页列表）。
     * 可见范围与 getNoticesForUser 一致（ALL / 分组 / 指定成员），未读 = 无 read_time 记录。
     */
    Long countUnreadNoticesForUser(Long userId);
}
