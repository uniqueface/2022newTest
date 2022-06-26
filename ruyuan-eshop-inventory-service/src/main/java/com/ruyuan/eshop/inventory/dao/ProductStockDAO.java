package com.ruyuan.eshop.inventory.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruyuan.eshop.common.dao.BaseDAO;
import com.ruyuan.eshop.inventory.domain.entity.ProductStockDO;
import com.ruyuan.eshop.inventory.mapper.ProductStockMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 库存中心的商品库存表 Mapper 接口
 * </p>
 *
 * @author zhonghuashishan
 */
@Repository
public class ProductStockDAO extends BaseDAO<ProductStockMapper, ProductStockDO> {

    @Autowired
    private ProductStockMapper productStockMapper;

    /**
     * 根据skuCode查询商品库存记录
     * @param skuCode
     * @return
     */
    public ProductStockDO getBySkuCode(String skuCode) {
        QueryWrapper<ProductStockDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sku_code", skuCode);
        return getOne(queryWrapper);
    }

    /**
     * 扣减商品库存
     * @param skuCode
     * @param saleQuantity
     * @return
     */
    public int deductProductStock(String skuCode, Integer saleQuantity) {
        return productStockMapper.deductProductStock(skuCode, saleQuantity);
    }

    /**
     * 扣减销售库存
     * @param skuCode
     * @param saleQuantity
     * @param originSaleStock
     * @return
     */
    public int deductSaleStock(String skuCode, Integer saleQuantity
            , Integer originSaleStock) {
        return productStockMapper.deductSaleStock(skuCode,saleQuantity,originSaleStock);
    }

    /**
     * 增加锁定库存
     * @param skuCode
     * @param saleQuantity
     * @param originSaledStock
     * @return
     */
    public int increaseSaledStock(String skuCode, Integer saleQuantity
            , Integer originSaledStock) {
        return productStockMapper.increaseSaledStock(skuCode,saleQuantity,originSaledStock);
    }

    /**
     * 还原销售库存
     * @param skuCode
     * @param saleQuantity
     * @param originSaleStock
     * @return
     */
    public int restoreSaleStock(String skuCode, Integer saleQuantity,
                         Integer originSaleStock) {
        return productStockMapper.restoreSaleStock(skuCode,saleQuantity,originSaleStock);
    }

    /**
     * 释放商品库存
     * @param skuCode
     * @param saleQuantity
     * @return
     */
    public int releaseProductStock(String skuCode, Integer saleQuantity) {
        return productStockMapper.releaseProductStock(skuCode, saleQuantity);
    }

    /**
     * 调整商品库存数量
     * @param skuCode
     * @param originSaleQuantity
     * @param saleIncremental
     * @return
     */
    public int modifyProductStock(String skuCode,Long originSaleQuantity,Long saleIncremental) {
        return productStockMapper.modifyProductStock(skuCode,originSaleQuantity,saleIncremental);
    }


}
