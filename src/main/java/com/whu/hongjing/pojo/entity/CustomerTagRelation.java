package com.whu.hongjing.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("customer_tag_relation")
public class CustomerTagRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

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