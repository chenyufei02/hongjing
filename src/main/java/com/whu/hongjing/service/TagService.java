package com.whu.hongjing.service;

public interface TagService {

    /**
     * 【核心方法】为单个客户刷新所有画像数据（指标+标签）。
     * 这是所有实时、单体更新的入口。
     */
    void refreshTagsForCustomer(Long customerId);

    /**
     * 【批量方法】刷新所有客户的画像数据（并行处理）。
     * 这是所有批量、定时任务的入口。
     */
    void refreshAllTagsAtomically();
}