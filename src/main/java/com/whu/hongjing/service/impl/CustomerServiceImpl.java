package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerMapper;
import com.whu.hongjing.pojo.entity.Customer;
//import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.CustomerTagRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import java.util.ArrayList;
import java.util.List;
//import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import com.whu.hongjing.pojo.vo.ProfitLossVO;
import java.util.Arrays;


@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private CustomerTagRelationService customerTagRelationService;

    // 删除的方法
    @Override
    public boolean removeCustomer(Long id) {
        return customerMapper.deleteById(id) > 0;  // MP提供的方法
    }

    // 更新（编辑）的方法
    @Override
    public boolean updateCustomer(Customer customer) {
        return customerMapper.updateById(customer) > 0;  // MP提供的方法 根据已有的ID更新
    }

    // 新增的方法
    @Override
    public boolean save(Customer entity) {
        return super.save(entity);  // MP提供的方法
    }

    // 唯一保留下来的根据ID查找的方法
    @Override
    public Customer getCustomerById(Long id) {
        return customerMapper.selectById(id);
    }



    /**
     * 分页查询客户列表的实现，增加了动态条件查询和稳定的排序
     */
    @Override
    public Page<Customer> getCustomerPage(
            Page<Customer> page, Long customerId, String name,
            String idNumber, String tagName)
    {
        QueryWrapper<Customer> queryWrapper = new QueryWrapper<>();  // 默认的初始查询语句 类似select * from Customer表

        // 保证按ID查询是优先级最高的查询，只要有ID的时候就先按ID判断返回
        if (customerId != null) {
            queryWrapper.eq("id", customerId);  // where id = customerId

            // 1、MP自动拦截此.page调用，通过传入的querywrapper查询语句统计到总记录数传给page
            // 2、然后加上page里的参数（当前页 也就是想要第pageNum页的数据, 每页pageSize条数据）--》  limit pageSize offset (pageNum-1)*pageSize
            //    返回当前页和当前页要的数据
            // 3、MP根据total和当前的pagesize自动计算出总页数，传给page。这样此时的page对象就是一个完整的对象了，包含了：
                // 总记录数(MP根据queryWrapper计算来)
                // 总页数(MP根据queryWrapper计算来)
                // 当前页数(原page创建的时候传入的参数带来)
                // 每页记录数(原page创建的时候传入的参数带来)
            return this.page(page, queryWrapper);  // 返回这个修改之后的page对象 传回当前页的数据
        }


        if (StringUtils.hasText(tagName)) {
            // 单个多个tagName都要统一转换为列表，才好调用多标签查询方法
            List<String> tagList = Arrays.asList(tagName.split(" "));
            // 这里调用了外面根据tags查询客户ID的方法
            List<Long> customerIds = customerTagRelationService.findCustomerIdsByTags(tagList);

            if (customerIds.isEmpty()) {
                // 如果根据标签没有找到任何客户，直接返回空结果，避免无效查询
                return new Page<>(page.getCurrent(), page.getSize(), 0);
            }
            queryWrapper.in("id", customerIds);  // 把根据TAGS模糊查询（in）的SQL语句添加进querywrapper
        }


        if (StringUtils.hasText(name)) {
            queryWrapper.like("name", name);  // 把根据name模糊的SQL语句添加进querywrapper
        }
        if (StringUtils.hasText(idNumber)) {
            queryWrapper.like("id_number", idNumber);
        }

        queryWrapper.orderByAsc("id");

        return this.page(page, queryWrapper);
    }



    @Override
    public Page<ProfitLossVO> getProfitLossPage(Page<ProfitLossVO> page, Long customerId, String customerName, String sortField, String sortOrder) {
        // 将 customerId 参数传递给Mapper层
        return baseMapper.getProfitLossPage(page, customerId, customerName, sortField, sortOrder);
    }


    @Override
    public ProfitLossVO getProfitLossVO(Long customerId) {
        return baseMapper.getProfitLossVOByCustomerId(customerId);
    }


}
