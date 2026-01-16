package com.meitou.admin.controller.admin;

import com.meitou.admin.common.Result;
import com.meitou.admin.entity.BackendAccount;
import com.meitou.admin.service.admin.BackendAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端后台账号管理控制器
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AccountController {
    
    private final BackendAccountService accountService;
    
    /**
     * 获取账号列表
     * 
     * @return 账号列表
     */
    @GetMapping
    public Result<List<BackendAccount>> getAccounts() {
        List<BackendAccount> accounts = accountService.getAccounts();
        return Result.success(accounts);
    }
    
    /**
     * 创建账号
     * 
     * @param account 账号信息
     * @return 创建的账号
     */
    @PostMapping
    public Result<BackendAccount> createAccount(@RequestBody BackendAccount account) {
        BackendAccount created = accountService.createAccount(account);
        return Result.success("创建成功", created);
    }
    
    /**
     * 更新账号
     * 
     * @param id 账号ID
     * @param account 账号信息
     * @return 更新后的账号
     */
    @PutMapping("/{id}")
    public Result<BackendAccount> updateAccount(@PathVariable Long id, @RequestBody BackendAccount account) {
        BackendAccount updated = accountService.updateAccount(id, account);
        return Result.success("更新成功", updated);
    }
    
    /**
     * 删除账号
     * 
     * @param id 账号ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return Result.success("删除成功");
    }
}

