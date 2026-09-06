package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.system.pojo.quarry.NoticeQuarry;
import com.rookie.system.pojo.vo.SysNoticeVo;

public interface SysNoticeService {

    PageInfo<SysNoticeVo> quarrySysNotice(NoticeQuarry quarry);

    SysNoticeVo getSysNoticeInfo(Long noticeId);

    Boolean addSysNoticeInfo(SysNoticeVo vo);

    Boolean editSysNoticeInfo(SysNoticeVo vo);

    Boolean deleteSysNoticeInfo(Long[] noticeIds);

    Boolean publishSysNotice(Long noticeId);

    Boolean revokeSysNotice(Long noticeId);

    PageInfo<SysNoticeVo> getMyNotices(Long userId);

    Long countUnreadNotices(Long userId);

    Boolean markAsRead(Long noticeId, Long userId);

    Boolean confirmNotice(Long noticeId, Long userId);
}
