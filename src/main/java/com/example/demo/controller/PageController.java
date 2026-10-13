package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/")
public class PageController {

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String home() {
        return "index";
    }

    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    public String submit() {
        return "redirect:/dashboard";
    }

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String dashboard() {
        return "dashboard";
    }

    @RequestMapping(value = "/timelog", method = RequestMethod.GET)
    public String timelog() {
        return "timelog";
    }

    @RequestMapping(value = "/qr-scanner", method = RequestMethod.GET)
    public String qrScanner() {
        return "qr-scanner";
    }
}
