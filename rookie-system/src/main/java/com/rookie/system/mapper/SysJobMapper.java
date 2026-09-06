package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysJob;
import com.rookie.system.pojo.quarry.SysJobQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysJobMapper {

    /** 分页查询任务列表（PageHelper 拦截分页） */
    List<SysJob> quarrySysJob(SysJobQuarry quarry);

    /** 按主键查询任务 */
    SysJob getSysJobById(Long jobId);

    /** 查询全部启用任务（应用启动时注册调度用） */
    List<SysJob> getAllEnabledJobs();

    /** 新增任务（useGeneratedKeys 回填 jobId） */
    Boolean addSysJob(SysJob job);

    /** 编辑任务（动态列） */
    Boolean editSysJob(SysJob job);

    /** 删除任务 */
    Boolean deleteSysJobById(Long jobId);

    /** 修改任务状态（1启用 0停用） */
    Boolean changeSysJobStatus(Long jobId, Integer status);
}
