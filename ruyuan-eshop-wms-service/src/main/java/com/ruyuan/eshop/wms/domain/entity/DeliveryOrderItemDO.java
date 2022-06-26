package com.ruyuan.eshop.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ruyuan.eshop.common.domain.BaseEntity;
import io.swagger.models.auth.In;
import lombok.Data;

import java.io.Serializable;

/**
 * 出库单条目
 * @author zhonghuashishan
 * @version 1.0
 */
@Data
@TableName("delivery_order_item")
public class DeliveryOrderItemDO extends BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 出库单ID
     */
    private String deliveryOrderId;

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

    /**
     * 拣货数量
     */
    private Integer pickingCount;

    /**
     * 捡货仓库货柜ID
     */
    private Integer skuContainerId;
}
