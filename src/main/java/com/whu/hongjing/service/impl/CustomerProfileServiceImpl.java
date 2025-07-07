package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerProfileMapper;
import com.whu.hongjing.pojo.entity.CustomerProfile;
import com.whu.hongjing.service.CustomerProfileService;
import org.springframework.stereotype.Service;

@Service
public class CustomerProfileServiceImpl extends ServiceImpl<CustomerProfileMapper, CustomerProfile> implements CustomerProfileService {
}