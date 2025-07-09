package com.whu.hongjing.controller;

import com.whu.hongjing.pojo.dto.FundInfoDTO;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.pojo.vo.ApiResponseVO;
import com.whu.hongjing.service.FundInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.whu.hongjing.service.FundDataImportService;

import java.util.List;

@RestController
@RequestMapping("/fund-info")
@Tag(name = "基金信息管理", description = "提供基金信息的增删改查接口")
public class FundInfoController {

    @Autowired
    private FundInfoService fundInfoService;

    @Autowired
    private FundDataImportService fundDataImportService; // 注入新的导入服务

    @Operation(summary = "新增基金信息")
    @PostMapping("/add")
    public boolean addFundInfo(@RequestBody @Validated FundInfoDTO dto) {
        FundInfo fundInfo = new FundInfo();
        BeanUtils.copyProperties(dto, fundInfo);
        return fundInfoService.save(fundInfo);
    }


    // 现实中似乎不应该手动更新，应该全部来源于外部数据才对。 不应该提供手动更新的接口
    @Operation(summary = "更新基金信息")
    @PutMapping("/update")
    public boolean updateFundInfo(@RequestBody @Validated FundInfoDTO dto) {
        FundInfo fundInfo = new FundInfo();
        BeanUtils.copyProperties(dto, fundInfo);
        return fundInfoService.updateById(fundInfo);
    }

    // 现实中似乎不能手动删除 涉及到很多购买了的用户
    @Operation(summary = "根据基金代码删除基金信息")
    @DeleteMapping("/delete/{fundCode}")
    public boolean deleteFundInfo(@PathVariable String fundCode) {
        return fundInfoService.removeById(fundCode);
    }

    @Operation(summary = "根据基金代码查询基金信息")
    @GetMapping("/{fundCode}")
    public FundInfo getFundInfo(@PathVariable String fundCode) {
        return fundInfoService.getById(fundCode);
    }

    @Operation(summary = "查询所有基金信息列表")
    @GetMapping("/list")
    public List<FundInfo> listAllFundInfo() {
        return fundInfoService.list();
    }

    @PostMapping("/import-all")
    @Operation(summary = "【手动触发】从外部数据源导入所有公募基金数据")
    public ApiResponseVO importAllFunds() { // <-- 修改返回类型
        try {
            int count = fundDataImportService.importFundsFromDataSource();
            return new ApiResponseVO(true, "数据导入任务完成！共处理了 " + count + " 只基金。");
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponseVO(false, "数据导入失败: " + e.getMessage());
        }
    }
}