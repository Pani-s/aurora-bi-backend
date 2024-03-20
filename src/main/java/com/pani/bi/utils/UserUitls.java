package com.pani.bi.utils;

import com.pani.bi.model.dto.user.UserDTO;

/**
 * @author Pani
 * @date Created in 2024/3/20 8:53
 * @description ThreadLocal
 */
public class UserUitls {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
