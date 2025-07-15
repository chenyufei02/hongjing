package com.whu.hongjing.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 对应展示客户标签的标签表（年龄 职业 性别 风险 持仓风格 交易行为等所有标签）
 * 设置为窄表：
 *    1、一个客户可以对应多行标签类型，方便随时扩展添加一行新的标签类型，而不用像宽表一样修改数据表底层结构（新增一列标签）。
 *    2、所有标签可以用同一套增删改查逻辑，每次增加新标签的时候不需要每次都修改实体类，为其添加一个字段属性。
 *    3、有的客户刚创建的时候有些标签可能还没有，如果用宽表就需要给这些用户的每个格子都填上大量的null，但是窄表就压根不创建这些行出来。
 *    4、宽表在进行不定数量的标签组合查询客户名单时，需要进行大量的WHERE AND，如果对每种查询组合都建立索引将非常耗时间和内存维护。而窄表只需要建
 *       一个万能的 (tag_name, customer_id)复合索引 用where tagname in (tagnames)就可以快速查出任意组合的标签类型，然后用customer索引快速分组
 *
 *
 * @return
 * @author yufei
 * @since 2025/7/14
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("customer_tag_relation")
public class CustomerTagRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;  // 非唯一键 一个客户可以有多条静态标签数据

    private String tagName;

    private String tagCategory;

    private LocalDateTime createTime;

    // 构造一个方便使用的构造函数
    public CustomerTagRelation(Long customerId, String tagName, String tagCategory) {
        this.customerId = customerId;
        this.tagName = tagName;
        this.tagCategory = tagCategory;
    }
}