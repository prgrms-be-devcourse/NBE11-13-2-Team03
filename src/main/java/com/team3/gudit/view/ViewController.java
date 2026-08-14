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

    @GetMapping("/admin/goods")
    public String adminGoodsList() {
        return "admin/goods-list";
    }

    @GetMapping("/admin/goods/new")
    public String adminGoodsCreate() {
        return "admin/goods-form";
    }

    @GetMapping("/admin/goods/{goodsId}/edit")
    public String adminGoodsEdit() {
        return "admin/goods-form";
    }

    @GetMapping("/admin/sales")
    public String adminSaleList() {
        return "admin/sale-list";
    }

    @GetMapping("/admin/sales/new")
    public String adminSaleCreate() {
        return "admin/sale-form";
    }

    @GetMapping("/admin/sales/{saleId}/edit")
    public String adminSaleEdit() {
        return "admin/sale-form";
    }

    @GetMapping("/payments")
    public String payment() {
        return "payment/payment";
    }

    @GetMapping("/payments/success")
    public String paymentSuccess() {
        return "payment/success";
    }

    @GetMapping("/payments/fail")
    public String paymentFail() {
        return "payment/fail";
    }
}