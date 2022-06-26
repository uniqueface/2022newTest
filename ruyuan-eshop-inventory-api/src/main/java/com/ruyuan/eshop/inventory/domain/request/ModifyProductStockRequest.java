package com.ruyuan.eshop.inventory.domain.request;

import com.ruyuan.eshop.common.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * 调整商品sku库存请求
 * @author zhonghuashishan
 * @version 1.0
 */
@Data
public class ModifyProductStockRequest extends BaseEntity implements Serializable {

    /**
     * 商品sku编号
     */
    private String skuCode;

    /**
     * 销售库存增量（可正，可负）
     */
    private Long stockIncremental;
}
