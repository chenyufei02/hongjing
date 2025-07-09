package com.whu.hongjing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    /**
     * 当用户访问网站根路径 ("/") 时，显示我们的主界面
     * @param model 用于向页面传递数据
     * @return 返回主页的模板文件名
     */
    @GetMapping("/")
    public String index(Model model) {
        // 将当前URI放入Model中，用于侧边栏高亮显示"工作台"
        model.addAttribute("activeUri", "/");
        // 返回 src/main/resources/templates/index.html 页面
        return "index";
    }
}