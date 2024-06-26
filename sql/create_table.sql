# 数据库初始化
# @author pani
#

-- 创建库
create database if not exists bi_ai;

-- 切换库
use bi_ai;

-- 用户表
-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_userAccount (userAccount)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 图表信息表
create table if not exists chart
(
    id          bigint auto_increment comment 'id' primary key,
    goal        text                               null comment '分析目标',
    chartData   text                               null comment '图表数据',
    chartType   varchar(128)                       null comment '图表类型',
    genChart    text                               null comment '生成的图表数据',
    genResult   text                               null comment '生成的分析结论',
    userId      bigint                             null comment '创建用户 id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    `name`      varchar(128)                       null comment '图表名称',
    execMessage text                               null comment '执行信息',
    chartState  int      default 0                 not null comment '生成状态：0:等待1:运行中2:失败3:成功'
) comment '图表信息表' collate = utf8mb4_unicode_ci;

-- 用户表单数据csv格式
create table chart_raw_csv
(
    chartId  bigint            not null comment 'chart图表id'
        primary key,
    csvData  text              not null comment 'csv格式的数据',
    isDelete tinyint default 0 not null comment '是否删除（考虑到之后可能可以让用户修改原数据）'
)
    comment '用户表单数据csv格式的原数据' collate = utf8mb4_unicode_ci;

-- 表AI数据
create table chart_gen_result
(
    chartId   bigint        not null
        primary key,
    genChart  varchar(4096) not null comment '生成的图表可视化数据',
    genResult varchar(4096) not null comment '生成的分析结果'
)
    comment '生成的图标可视化数据和结论' collate = utf8mb4_unicode_ci;

