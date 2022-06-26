package com.ruyuan.eshop.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ruyuan.eshop.common.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 库存中心的商品库存表
 * </p>
 *
 * @author zhonghuashishan
 */
@Data
@TableName("inventory_product_stock")
public class ProductStockDO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品sku编号
     */
    private String skuCode;

    /**
     * 销售库存
     */
    private Long saleStockQuantity;

    /**
     * 已销售库存
     */
    private Long saledStockQuantity;
}
