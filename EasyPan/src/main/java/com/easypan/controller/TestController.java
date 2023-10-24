package com.easypan.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TestController
 *
 * @author Jia Yaoyi
 * @date 2023/10/12
 */

@RestController
public class TestController {

    @RequestMapping("/test")
    public String test1(){
        return "test01";
    }
}
