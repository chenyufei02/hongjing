package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerTagRelationMapper;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.service.CustomerTagRelationService;
import org.springframework.stereotype.Service;

@Service
public class CustomerTagRelationServiceImpl extends ServiceImpl<CustomerTagRelationMapper, CustomerTagRelation> implements CustomerTagRelationService {
}