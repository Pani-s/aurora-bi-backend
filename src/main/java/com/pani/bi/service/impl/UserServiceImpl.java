package com.pani.bi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.constant.CommonConstant;
import com.pani.bi.constant.RedisConstant;
import com.pani.bi.constant.UserConstant;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.exception.ThrowUtils;
import com.pani.bi.mapper.UserMapper;
import com.pani.bi.model.dto.user.UserDTO;
import com.pani.bi.model.dto.user.UserPwdUpdateMyRequest;
import com.pani.bi.model.dto.user.UserQueryRequest;
import com.pani.bi.model.entity.User;
import com.pani.bi.model.enums.UserRoleEnum;
import com.pani.bi.model.vo.LoginUserVO;
import com.pani.bi.model.vo.UserVO;
import com.pani.bi.service.UserService;
import com.pani.bi.utils.JwtUtil;
import com.pani.bi.utils.SqlUtils;
import com.pani.bi.utils.UserUitls;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 *
 * @author pani
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "kookv";
    private static final int PASSWORD_LEN = 3;

    @Override
    public boolean resetPassword(Long userId) {

        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该用户不存在！");
        }

        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + "123456").getBytes());

        user.setUserPassword(encryptPassword);
        boolean update = this.updateById(user);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重置密码失败，数据库错误");
        }
        return update;

    }

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < PASSWORD_LEN || checkPassword.length() < PASSWORD_LEN) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public String userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 3) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录用户的登录态
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("userName", user.getUserName());
        userMap.put("userRole", user.getUserRole());
        String token = JwtUtil.genToken(userMap);
        //把token存储到redis中
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        operations.set(RedisConstant.LOGIN_USER_KEY + token, token,
                20, TimeUnit.MINUTES);
        return token;
        //        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        //        return this.getLoginUserVO(user);
    }


    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 已被拦截，所以不用判断
        UserDTO currentUser = UserUitls.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        User user = this.getById(userId);
        if (user == null) {
            log.error("数据库未查到该用户相关的信息??!!");
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return user;
    }


    //    /**
    //     * 获取当前登录用户
    //     *
    //     * @param request
    //     * @return
    //     */
    /*
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }
    */


    //    /**
    //     * 是否为管理员
    //     *
    //     * @param request
    //     * @return
    //     */
    //    @Override
    //    public boolean isAdmin(HttpServletRequest request) {
    //        // 仅管理员可查询
    //        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
    //        User user = (User) userObj;
    //        return isAdmin(user);
    //    }

    /**
     * 是否为管理员
     *
     * @param
     * @return
     */
    @Override
    public boolean isAdmin() {
        UserDTO user = UserUitls.getUser();
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

//    /**
//     * 用户注销
//     *
//     * @param request
//     */
//    @Override
//    public boolean userLogout(HttpServletRequest request) {
//        if (request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE) == null) {
//            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
//        }
//        // 移除登录态
//        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
//        return true;
//    }

    /**
     * 用户注销
     *
     * @param token token
     */
    @Override
    public boolean userLogout(String token) {
        //删除redis中对应的token
        try {
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            operations.getOperations().delete(RedisConstant.LOGIN_USER_KEY + token);
            return true;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public boolean changePwd(UserPwdUpdateMyRequest userPwdUpdateMyRequest, HttpServletRequest request , String token) {
        String userPassword = userPwdUpdateMyRequest.getUserPassword();
        String newPassword = userPwdUpdateMyRequest.getNewPassword();
        String checkedNewPassword = userPwdUpdateMyRequest.getCheckedNewPassword();

        //检查两次输入的密码是否一致
        if (StringUtils.isAnyBlank(userPassword, newPassword, checkedNewPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userPassword.length() < PASSWORD_LEN || newPassword.length() < PASSWORD_LEN ||
                checkedNewPassword.length() < PASSWORD_LEN) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度错误或过短");
        }
        ThrowUtils.throwIf(!(newPassword.equals(checkedNewPassword)),
                ErrorCode.PARAMS_ERROR, "两次新密码输入不一致");

        //检查旧密码是否一致
        User loginUser = this.getLoginUser(request);
        //要是只得到密码字段就好了TAT,,,噢噢噢用queryWrapper
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", loginUser.getId());
        queryWrapper.select("id", "userPassword");
        User user = this.getOne(queryWrapper);
        //        User user = this.getById(loginUser.getId());
        String encryptOldPwd = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        ThrowUtils.throwIf(!(encryptOldPwd.equals(user.getUserPassword())),
                ErrorCode.PARAMS_ERROR, "用户旧密码错误");
        //终于可以开始改密码了
        String encryptNewPwd = DigestUtils.md5DigestAsHex((SALT + newPassword).getBytes());
        user.setUserPassword(encryptNewPwd);
        this.updateById(user);

        //删除redis中对应的token
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        operations.getOperations().delete(RedisConstant.LOGIN_USER_KEY + token);

        //用户退出应该是前端做
        return true;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();

        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);

        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }
}
