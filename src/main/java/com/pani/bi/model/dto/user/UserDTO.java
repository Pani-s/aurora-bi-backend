package com.pani.bi.model.dto.user;

import lombok.Data;

/**
 * @author Pani
 * @date Created in 2024/3/20 8:52
 * @description jwt用
 */
@Data
public class UserDTO {
    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户角色：user/admin/ban
     */
    private String userRole;
}
