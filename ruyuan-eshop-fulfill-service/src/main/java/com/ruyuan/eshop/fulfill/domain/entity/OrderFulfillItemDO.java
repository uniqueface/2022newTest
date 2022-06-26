package com.ruyuan.eshop.fulfill.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ruyuan.eshop.common.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 订单履约条目
 * </p>
 *
 * @author zhonghuashishan
 */
@Data
@TableName("order_fulfill_item")
public class OrderFulfillItemDO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 履约单ID
     */
    private String fulfillId;

    /**
     * 商品id
     */
    private String skuCode;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 销售单价
     */
    private Integer salePrice;

    /**
     * 销售数量
     */
    private Integer saleQuantity;

    /**
     * 商品单位
     */
    private String productUnit;

    /**
     * 付款金额
     */
    private Integer payAmount;

    /**
     * 当前商品支付原总价
     */
    private Integer originAmount;
}
