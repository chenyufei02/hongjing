package com.whu.hongjing.pojo.dto;

import java.time.LocalDate;

/**
 * @param
 * @return
 */
public class CustomerAddDTO {
    private String name;
    private String gender;
    private String idType;
    private String idNumber;
    private LocalDate birthDate;
    private String nationality;
    private String occupation;
    private String phone;
    private String address;
    // 没有 createTime/updateTime
}
