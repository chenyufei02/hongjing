package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.FundInfoMapper;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.FundInfoService;
import org.springframework.stereotype.Service;

@Service
public class FundInfoServiceImpl extends ServiceImpl<FundInfoMapper, FundInfo> implements FundInfoService {
}