// 文件位置: src/main/java/com/whu/hongjing/pojo/vo/DashboardFilteredResultVO.java
package com.whu.hongjing.pojo.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.entity.Customer;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class DashboardFilteredResultVO {

    // 用于存放图表数据
    private Map<String, List<TagVO>> chartData;

    // 用于存放客户列表的分页结果
    private Page<Customer> customerPage;
}