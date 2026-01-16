package com.meitou.admin.util;

import java.util.regex.Pattern;

/**
 * 密码验证工具类
 */
public class PasswordValidator {

    // 最低6位数密码即可（不区分特殊符号，大小写）
    private static final String PASSWORD_PATTERN = "^.{6,}$";
    private static final Pattern PATTERN = Pattern.compile(PASSWORD_PATTERN);

    /**
     * 验证密码强度
     * @param password 密码
     * @return 是否符合要求
     */
    public static boolean validate(String password) {
        if (password == null) {
            return false;
        }
        return PATTERN.matcher(password).matches();
    }
}
