package com.whu.hongjing.controller;

import com.whu.hongjing.pojo.dto.FundInfoDTO;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.FundInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fund-info")
@Tag(name = "基金信息管理", description = "提供基金信息的增删改查接口")
public class FundInfoController {

    @Autowired
    private FundInfoService fundInfoService;

    @Operation(summary = "新增基金信息")
    @PostMapping("/add")
    public boolean addFundInfo(@RequestBody @Validated FundInfoDTO dto) {
        FundInfo fundInfo = new FundInfo();
        BeanUtils.copyProperties(dto, fundInfo);
        return fundInfoService.save(fundInfo);
    }

    @Operation(summary = "更新基金信息")
    @PutMapping("/update")
    public boolean updateFundInfo(@RequestBody @Validated FundInfoDTO dto) {
        FundInfo fundInfo = new FundInfo();
        BeanUtils.copyProperties(dto, fundInfo);
        return fundInfoService.updateById(fundInfo);
    }

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
}