package com.ruyuan.eshop.inventory.domain.request;

import com.ruyuan.eshop.common.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * 添加商品库存
 * @author zhonghuashishan
 * @version 1.0
 */
@Data
public class AddProductStockRequest extends BaseEntity implements Serializable {

    /**
     * 商品sku编号
     */
    private String skuCode;

    /**
     * 销售库存
     */
    private Long saleStockQuantity;

}
