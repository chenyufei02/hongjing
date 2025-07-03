package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.FundTransactionMapper;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.service.FundTransactionService;
import org.springframework.stereotype.Service;

@Service
public class FundTransactionServiceImpl extends ServiceImpl<FundTransactionMapper, FundTransaction> implements FundTransactionService {
}