// 文件位置: src/main/java/com/whu/hongjing/pojo/vo/ApiResponseVO.java
package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通用API响应对象")
public class ApiResponseVO<T> { // 【1. 新增泛型 <T>】

    @Schema(description = "操作是否成功", example = "true")
    private boolean success;

    @Schema(description = "返回的消息文本", example = "操作成功！")
    private String message;

    @Schema(description = "携带的额外数据")
    private T data; // 【2. 新增一个可以存放任何类型数据的字段】

    // --- 【3. 新增多个方便使用的构造函数】 ---

    public ApiResponseVO(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ApiResponseVO(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    // 提供两个静态工厂方法，让代码更优雅
    public static <T> ApiResponseVO<T> success(String message, T data) {
        return new ApiResponseVO<>(true, message, data);
    }

    public static <T> ApiResponseVO<T> error(String message) {
        return new ApiResponseVO<>(false, message, null);
    }
}