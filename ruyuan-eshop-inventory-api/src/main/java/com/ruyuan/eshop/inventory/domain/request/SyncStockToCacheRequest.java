package com.ruyuan.eshop.inventory.domain.request;

import com.ruyuan.eshop.common.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * 同步商品sku库存数据到缓存
 * @author zhonghuashishan
 * @version 1.0
 */
@Data
public class SyncStockToCacheRequest extends BaseEntity implements Serializable  {

    /**
     * 商品sku编号
     */
    private String skuCode;
}
