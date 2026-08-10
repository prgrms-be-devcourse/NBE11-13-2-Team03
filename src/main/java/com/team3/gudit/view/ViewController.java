package com.team3.gudit.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/sales")
    public String saleList() {
        return "sale/list";
    }

    @GetMapping("/sales/{saleId}")
    public String saleDetail() {
        return "sale/detail";
    }

    @GetMapping("/mypage/purchases")
    public String purchaseList() {
        return "purchase/list";
    }

    @GetMapping("/mypage/purchases/{purchaseId}")
    public String purchaseDetail() {
        return "purchase/detail";
    }
}