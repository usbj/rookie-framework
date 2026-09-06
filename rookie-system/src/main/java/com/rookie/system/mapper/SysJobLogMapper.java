package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysJobLog;
import com.rookie.system.pojo.quarry.SysJobLogQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysJobLogMapper {

    /** 分页查询任务执行日志（PageHelper 拦截分页） */
    List<SysJobLog> quarrySysJobLog(SysJobLogQuarry quarry);

    /** 新增执行日志 */
    Boolean addSysJobLog(SysJobLog jobLog);

    /** 删除某任务的全部执行日志（删除任务时级联清理） */
    Boolean deleteSysJobLogByJobId(Long jobId);
}
