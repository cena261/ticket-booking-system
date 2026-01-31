package com.cena.ddd.controller.resource;

import com.cena.ddd.application.service.event.EventAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
public class HiController {

    @Autowired
    private EventAppService eventAppService;
    @GetMapping("/hi")
    public String nihao(){
//        return "Nihao fine shyt";
        return eventAppService.sayHi("Tu u");
    }
}
