package com.ruyuan.eshop.inventory.service.impl;

import com.ruyuan.eshop.inventory.dao.ProductStockLogDAO;
import com.ruyuan.eshop.inventory.domain.dto.DeductStockDTO;
import com.ruyuan.eshop.inventory.domain.entity.ProductStockDO;
import com.ruyuan.eshop.inventory.domain.request.AddProductStockRequest;
import com.ruyuan.eshop.inventory.exception.InventoryBizException;
import com.ruyuan.eshop.inventory.exception.InventoryErrorCodeEnum;
import com.ruyuan.eshop.inventory.tcc.LockMysqlStockTccService;
import com.ruyuan.eshop.inventory.tcc.LockRedisStockTccService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 扣减商品库存处理器
 * @author zhonghuashishan
 * @version 1.0
 */
@Slf4j
@Component
public class DeductProductStockProcessor {

    @Autowired
    private LockMysqlStockTccService lockMysqlStockTccService;

    @Autowired
    private LockRedisStockTccService lockRedisStockTccService;

    @Autowired
    private ProductStockLogDAO productStockLogDAO;

    /**
     * 执行扣减商品库存逻辑
     */
//    @GlobalTransactional(rollbackFor = Exception.class)
    public void doDeduct(DeductStockDTO deductStock) {
        //1、执行执行mysql库存扣减
        boolean result = lockMysqlStockTccService
                .deductStock(null,deductStock);
        if(!result) {
            throw new InventoryBizException(InventoryErrorCodeEnum.PRODUCT_SKU_STOCK_NOT_FOUND_ERROR);
        }

        //2、执行redis库存扣减
        result = lockRedisStockTccService.deductStock(null,deductStock);
        if(!result) {
            throw new InventoryBizException(InventoryErrorCodeEnum.PRODUCT_SKU_STOCK_NOT_FOUND_ERROR);
        }
    }
}
