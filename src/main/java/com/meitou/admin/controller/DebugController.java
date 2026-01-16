package com.meitou.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.User;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final UserMapper userMapper;
    private final UserTransactionMapper userTransactionMapper;

    @GetMapping("/db")
    public Result<Map<String, Object>> checkDb() {
        Map<String, Object> data = new HashMap<>();

        // 1. Count all users (ignoring tenant)
        try {
            Long allUsers = userMapper.selectCountIgnoreTenant(new QueryWrapper<>());
            data.put("totalUsers_allSites", allUsers);
        } catch (Exception e) {
            data.put("totalUsers_error", e.getMessage());
        }

        // 2. Count users for site 1
        try {
            QueryWrapper<User> site1Wrapper = new QueryWrapper<>();
            site1Wrapper.eq("site_id", 1);
            Long site1Users = userMapper.selectCountIgnoreTenant(site1Wrapper);
            data.put("totalUsers_site1", site1Users);
        } catch (Exception e) {
            data.put("site1Users_error", e.getMessage());
        }

        // 3. Check transactions
        try {
            Long txCount = userTransactionMapper.selectCount(new QueryWrapper<>());
            data.put("transactions_count", txCount);
        } catch (Exception e) {
            data.put("transactions_error", e.getMessage());
        }
        
        // 4. Check transactions for site 1
        try {
            // Note: selectCount on mapper without IgnoreTenant will enforce current site (which is 0 for admin?)
            // We should use selectMapsIgnoreTenant to check raw data
            QueryWrapper<com.meitou.admin.entity.UserTransaction> txWrapper = new QueryWrapper<>();
            txWrapper.select("count(*) as total");
            txWrapper.eq("site_id", 1);
            List<Map<String, Object>> res = userTransactionMapper.selectMapsIgnoreTenant(txWrapper);
            data.put("transactions_site1", res);
        } catch (Exception e) {
            data.put("transactions_site1_error", e.getMessage());
        }

        return Result.success(data);
    }
}
