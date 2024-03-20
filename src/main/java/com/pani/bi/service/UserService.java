package com.pani.bi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pani.bi.model.dto.user.UserPwdUpdateMyRequest;
import com.pani.bi.model.dto.user.UserQueryRequest;
import com.pani.bi.model.entity.User;
import com.pani.bi.model.vo.LoginUserVO;
import com.pani.bi.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户服务
 *
 * @author pani
 */
public interface UserService extends IService<User> {

    /**
     * 重置密码
     *
     * @param userId
     * @return
     */
    boolean resetPassword(Long userId);

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return token
     */
    String userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    //    /**
    //     * 获取当前登录用户（允许未登录）
    //     *
    //     * @param request
    //     * @return
    //     */
    //    User getLoginUserPermitNull(HttpServletRequest request);

    //    /**
    //     * 是否为管理员
    //     *
    //     * @param request
    //     * @return
    //     */
    //    boolean isAdmin(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @return
     */
    boolean isAdmin();

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

//    /**
//     * 用户注销
//     *
//     * @param request
//     * @return
//     */
//    boolean userLogout(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param token token
     * @return
     */
    boolean userLogout(String token);



    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param userList
     * @return
     */
    List<UserVO> getUserVO(List<User> userList);

    /**
     * 个人用户修改密码
     *
     * @param userPwdUpdateMyRequest
     * @param request
     * @return
     */
    boolean changePwd(UserPwdUpdateMyRequest userPwdUpdateMyRequest, HttpServletRequest request
    , String token);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

}
