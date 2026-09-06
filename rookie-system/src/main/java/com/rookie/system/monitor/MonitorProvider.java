package com.rookie.system.monitor;

/**
 * 监控数据提供者（服务监控扩展点）。
 * <p>
 * 服务监控采用可插拔设计：每种监控源（服务器、未来的 Redis / MySQL 等中间件）
 * 实现本接口并注册为 Spring Bean，聚合接口会自动收集全部提供者返回给前端。
 * <p>
 * 新增监控源只需两步：
 * <ol>
 *   <li>实现本接口（type 唯一、title 用于前端展示、collect 返回该监控源的实时数据）；</li>
 *   <li>前端在监控页的 type → 展示组件映射中登记对应的渲染卡片（未知类型有占位兜底）。</li>
 * </ol>
 */
public interface MonitorProvider {

    /**
     * 监控源类型标识（全局唯一，前端据此选择渲染组件，如 server / redis / mysql）。
     *
     * @return 类型标识
     */
    String type();

    /**
     * 监控源展示名称（前端卡片标题，如「服务器」「Redis」）。
     *
     * @return 展示名称
     */
    String title();

    /**
     * 采集该监控源的实时数据（数据结构由各提供者自定，前端按 type 对应解析）。
     *
     * @return 实时监控数据
     */
    Object collect();
}
