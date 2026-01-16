package com.meitou.admin.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.User;
import com.meitou.admin.service.admin.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端用户管理控制器
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    /**
     * 获取用户列表
     * 根据站点ID（医美类、电商类、生活服务类）筛选用户
     * 
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @param search 搜索关键词（可选）
     * @param page 当前页码（可选，默认1）
     * @param size 每页数量（可选，默认10）
     * @return 分页用户列表
     */
    @GetMapping
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<IPage<User>> getUsers(
            @RequestParam(required = true) Long siteId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        // SiteContext 已由 @SiteScope 注解自动设置
        IPage<User> users = userService.getUsers(siteId, search, page, size);
        return Result.success(users);
    }
    
    /**
     * 获取用户详情
     * 
     * @param id 用户ID
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @return 用户信息
     */
    @GetMapping("/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<User> getUser(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        User user = userService.getUserById(id);
        // 验证用户的siteId是否与请求参数一致
        if (user != null && !user.getSiteId().equals(siteId)) {
            return Result.error("用户不属于该站点");
        }
        return Result.success(user);
    }
    
    /**
     * 创建用户
     * 
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @param user 用户信息
     * @return 创建的用户
     */
    @PostMapping
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<User> createUser(
            @RequestParam(required = true) Long siteId,
            @RequestBody User user) {
        // SiteContext 已由 @SiteScope 注解自动设置
        // 确保用户的siteId与请求参数一致
        user.setSiteId(siteId);
        User created = userService.createUser(user);
        return Result.success("创建成功", created);
    }
    
    /**
     * 更新用户
     * 
     * @param id 用户ID
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @param user 用户信息
     * @return 更新后的用户
     */
    @PutMapping("/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<User> updateUser(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId,
            @RequestBody User user) {
        // SiteContext 已由 @SiteScope 注解自动设置
        // 确保用户的siteId与请求参数一致
        user.setSiteId(siteId);
        User updated = userService.updateUser(id, user);
        return Result.success("更新成功", updated);
    }
    
    /**
     * 删除用户
     * 
     * @param id 用户ID
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<Void> deleteUser(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        userService.deleteUser(id);
        return Result.success("删除成功");
    }
    
    /**
     * 赠送积分
     * 
     * @param id 用户ID
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @param points 积分数量
     * @return 更新后的用户
     */
    @PostMapping("/{id}/gift-points")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<User> giftPoints(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId,
            @RequestParam Integer points) {
        // SiteContext 已由 @SiteScope 注解自动设置
        User user = userService.giftPoints(id, points, siteId);
        return Result.success("赠送成功", user);
    }
}

