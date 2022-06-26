package com.ruyuan.eshop.inventory.controller;

import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.inventory.domain.request.AddProductStockRequest;
import com.ruyuan.eshop.inventory.domain.request.ModifyProductStockRequest;
import com.ruyuan.eshop.inventory.domain.request.SyncStockToCacheRequest;
import com.ruyuan.eshop.inventory.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 正向下单流程接口冒烟测试
 * @author zhonghuashishan
 * @version 1.0
 */
@RestController
@Slf4j
@RequestMapping("/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    /**
     * 新增商品库存
     * @return
     */
    @PostMapping("/addProductStock")
    public JsonResult<Boolean> addProductStock(@RequestBody AddProductStockRequest request) {
        inventoryService.addProductStock(request);
        return JsonResult.buildSuccess(true);
    }

    /**
     * 调整商品库存
     * @return
     */
    @PostMapping("/modifyProductStock")
    public JsonResult<Boolean> modifyProductStock(@RequestBody ModifyProductStockRequest request) {
        inventoryService.modifyProductStock(request);
        return JsonResult.buildSuccess(true);
    }

    /**
     * 同步商品sku库存数据到缓存
     * @return
     */
    @PostMapping("/syncStockToCache")
    public JsonResult<Boolean> syncStockToCache(@RequestBody SyncStockToCacheRequest request) {
        inventoryService.syncStockToCache(request);
        return JsonResult.buildSuccess(true);
    }

}
