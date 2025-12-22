-- 实时计算结果表

-- PV/UV 统计表
CREATE TABLE IF NOT EXISTS `realtime_pv_uv_stat` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `window_start` DATETIME NOT NULL COMMENT '窗口开始时间',
    `window_end` DATETIME NOT NULL COMMENT '窗口结束时间',
    `cms_type` VARCHAR(50) NOT NULL COMMENT '内容类型',
    `cms_id` BIGINT NOT NULL COMMENT '内容ID',
    `pv` BIGINT NOT NULL DEFAULT 0 COMMENT '页面访问量',
    `uv` BIGINT NOT NULL DEFAULT 0 COMMENT '独立访客数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_window_end` (`window_end`),
    INDEX `idx_cms` (`cms_type`, `cms_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时PV/UV统计';

-- 热门课程 TopN 表
CREATE TABLE IF NOT EXISTS `realtime_hot_course_stat` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `window_start` DATETIME NOT NULL COMMENT '窗口开始时间',
    `window_end` DATETIME NOT NULL COMMENT '窗口结束时间',
    `cms_type` VARCHAR(50) NOT NULL COMMENT '内容类型',
    `cms_id` BIGINT NOT NULL COMMENT '内容ID',
    `access_count` BIGINT NOT NULL DEFAULT 0 COMMENT '访问次数',
    `rank_num` INT NOT NULL COMMENT '排名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_window_end` (`window_end`),
    INDEX `idx_rank` (`window_end`, `rank_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门课程TopN';

-- 在线人数统计表
CREATE TABLE IF NOT EXISTS `realtime_online_user_stat` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `window_start` DATETIME NOT NULL COMMENT '窗口开始时间',
    `window_end` DATETIME NOT NULL COMMENT '窗口结束时间',
    `online_count` BIGINT NOT NULL DEFAULT 0 COMMENT '在线人数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_window_end` (`window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线人数统计';

-- 查询示例
-- 查看最近的 PV/UV 统计
-- SELECT * FROM realtime_pv_uv_stat ORDER BY window_end DESC LIMIT 20;

-- 查看热门课程
-- SELECT * FROM realtime_hot_course_stat WHERE rank_num <= 10 ORDER BY window_end DESC, rank_num;

-- 查看在线人数趋势
-- SELECT window_end, online_count FROM realtime_online_user_stat ORDER BY window_end DESC LIMIT 60;
