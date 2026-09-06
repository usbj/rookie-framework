package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.entity.SysJobLog;
import com.rookie.system.pojo.quarry.SysJobLogQuarry;
import com.rookie.system.pojo.quarry.SysJobQuarry;
import com.rookie.system.pojo.vo.SysJobVo;

/**
 * 定时任务服务：任务 CRUD + 动态启停 + 立即执行 + 执行日志查询。
 * <p>
 * 数据库为任务元数据唯一源，调度器（SysJobScheduler）状态与库保持一致：
 * 新增/编辑/启停落库成功后同步注册或取消调度；删除先取消调度并级联清理执行日志。
 */
public interface SysJobService {

    /** 分页查询任务列表 */
    PageInfo<SysJobVo> quarrySysJob(SysJobQuarry quarry);

    /** 按主键查询任务 */
    SysJobVo getSysJobById(Long jobId);

    /** 新增任务（校验配置合法性；启用则注册调度） */
    Boolean addSysJob(SysJobVo jobVo);

    /** 编辑任务（校验配置合法性；重新注册调度） */
    Boolean editSysJob(SysJobVo jobVo);

    /** 删除任务（取消调度 + 删除记录 + 级联清理执行日志） */
    Boolean deleteSysJobById(Long jobId);

    /** 修改任务状态（1启用 0停用，动态注册/取消调度） */
    Boolean changeSysJobStatus(Long jobId, Integer status);

    /** 立即执行一次（手动触发，不走 cron 调度；执行中会被防重拦截） */
    Boolean runSysJob(Long jobId);

    /** 分页查询任务执行日志 */
    PageInfo<SysJobLog> quarrySysJobLog(SysJobLogQuarry quarry);
}
