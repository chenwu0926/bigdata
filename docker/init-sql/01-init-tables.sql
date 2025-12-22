-- 初始化数据库表

USE imooc_log;

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

-- 批处理统计表（原有）
CREATE TABLE IF NOT EXISTS `day_video_access_topn_stat` (
    `day` VARCHAR(10) NOT NULL COMMENT '日期',
    `cms_id` BIGINT NOT NULL COMMENT '课程ID',
    `times` BIGINT NOT NULL COMMENT '访问次数',
    PRIMARY KEY (`day`, `cms_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `day_video_city_access_topn_stat` (
    `day` VARCHAR(10) NOT NULL COMMENT '日期',
    `cms_id` BIGINT NOT NULL COMMENT '课程ID',
    `city` VARCHAR(50) NOT NULL COMMENT '城市',
    `times` BIGINT NOT NULL COMMENT '访问次数',
    `times_rank` INT NOT NULL COMMENT '排名',
    PRIMARY KEY (`day`, `cms_id`, `city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SELECT 'Database initialized successfully!' AS message;
