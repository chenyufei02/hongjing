package com.whu.hongjing.controller;

import com.whu.hongjing.pojo.vo.ApiResponseVO; // <-- 1. 导入我们新的VO类
import com.whu.hongjing.service.TagRefreshService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/tags")
@Tag(name = "客户标签管理", description = "提供标签的刷新与查询接口")
public class TagController {

    @Autowired
    private TagRefreshService tagRefreshService;

    @PostMapping("/refresh/{customerId}")
    @Operation(summary = "【手动触发】刷新指定客户的所有标签")
    public ApiResponseVO refreshCustomerTags(@PathVariable Long customerId) { // <-- 2. 修改返回类型为 ApiResponseVO
        try {
            tagRefreshService.refreshTagsForCustomer(customerId);
            // 3. 返回一个结构清晰的 ApiResponseVO 对象
            return new ApiResponseVO(true, "客户 " + customerId + " 的标签刷新任务已成功触发并执行完毕！");
        } catch (Exception e) {
            return new ApiResponseVO(false, "标签刷新失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发一次对所有客户的全量画像刷新。(异步操作，接口会立即返回，后台将开始执行耗时的批量任务。)
     */
    @PostMapping("/refresh-all")
    @Operation(summary = "【手动触发】刷新所有客户的标签（异步后台任务）")
    public ApiResponseVO refreshAllCustomerTags() {
        try {
            // 直接调用我们的异步批量服务
            tagRefreshService.refreshAllTagsAtomically();
            // 立即返回成功响应，告知前端任务已启动
            return new ApiResponseVO(true, "全量标签刷新任务已成功执行完毕！");
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponseVO(false, "启动全量刷新任务失败: " + e.getMessage());
        }
    }


}