package com.ruyuan.eshop.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ruyuan.eshop.common.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 客服接收售后申请信息表
 * </p>
 *
 * @author zhonghuashishan
 */
@Data
@TableName("customer_receives_after_sales_info")
public class CustomerReceivesAfterSaleInfoDO extends BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 订单id
     */
    private String orderId;

    /**
     * 售后id
     */
    private String afterSaleId;

    /**
     * 售后支付单id
     */
    private Long afterSaleRefundId;

    /**
     * 售后类型 1 退款  2 退货
     */
    private Integer afterSaleType;

    /**
     * 实际退款金额
     */
    private Integer returnGoodAmount;

    /**
     * 申请退款金额
     */
    private Integer applyRefundAmount;


}
