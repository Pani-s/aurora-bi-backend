package com.pani.bi.aop;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.constant.RedisConstant;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.model.dto.user.UserDTO;
import com.pani.bi.utils.JwtUtil;

import com.pani.bi.utils.ThreadLocalUtil;

import com.pani.bi.utils.UserUitls;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Pani
 * @date Created in 2024/3/20 9:10
 * @description
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.toString().equals(request.getMethod())) {
            System.out.println("OPTIONS请求，放行");
            return true;
        }
        //1. 令牌验证
        String token = request.getHeader("Authorization");
        if (StrUtil.isBlank(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        try {
            //2.从redis中获取相同的token
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String key = RedisConstant.LOGIN_USER_KEY + token;
            String redisToken = operations.get(key);
            if (redisToken == null){
                //token已经失效了
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            Map<String, Object> userMap = JwtUtil.parseToken(token);
            //3.把User存储到ThreadLocal中
            UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
            UserUitls.saveUser(userDTO);
            log.info("token拦截器--用户信息：{}",userDTO);
//            ThreadLocalUtil.set(userMap);

            //4.刷新token有效期
            stringRedisTemplate.expire(key, RedisConstant.LOGIN_USER_TTL, TimeUnit.MINUTES);

            //放行
            return true;
        } catch (Exception e) {
            log.error("出错：{}",e.getMessage());
            //http响应状态码为401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            //不放行
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserUitls.removeUser();
    }


//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        /*
//        if (HttpMethod.OPTIONS.toString().equals(request.getMethod())) {
//            System.out.println("OPTIONS请求，放行");
//            return true;
//        }
//        */
//        String token = request.getHeader("authorization");
//        if (JwtUtils.verify(token)) {
//            return true;
//        }
//        // 失败我们跳转回登录页面
//        /*
//        request.setAttribute("msg","登录出错");
//        request.getRemoteHost();
//        request.getRequestDispatcher("/login").forward(request,response);
//        */
//        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
////        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
//        return false;
//    }

}
