package com.skillbridge.controller;

import com.skillbridge.utils.CookieHelper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 首页与登出控制器。
 * <p>
 * 双入口设计：根路径展示角色选择门户页，教师与学生各自独立登录：
 * <ul>
 *   <li>教师登录：{@link TeacherController#showTeacherLogin} → /teacher/login</li>
 *   <li>学生登录：{@link StudentController#showStudentLogin} → /student/login</li>
 * </ul>
 * 统一登出后清 Cookie 并回到门户页，由用户重新选择入口。
 */
@Controller
public class IndexController {

    private final CookieHelper cookieHelper;

    public IndexController(CookieHelper cookieHelper) {
        this.cookieHelper = cookieHelper;
    }

    /** 根路径：展示角色选择门户页 */
    @GetMapping("/")
    public String home() {
        return "common/portal";
    }

    /** 统一登出：清除 JWT Cookie，回门户页重新选择入口 */
    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {
        cookieHelper.clearTokenCookie(response);
        return "redirect:/?logout=1";
    }
}
