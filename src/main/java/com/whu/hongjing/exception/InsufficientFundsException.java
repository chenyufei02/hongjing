package com.whu.hongjing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 自定义异常，用于表示资金或份额不足
 */
@ResponseStatus(value = HttpStatus.BAD_REQUEST) // 让Spring MVC在遇到此异常时返回400错误码
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}