package com.ruyuan.eshop.tms.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ruyuan.eshop.common.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;

/**
 * 物流单
 * @author zhonghuashishan
 * @version 1.0
 */
@Data
@TableName("logistic_order")
public class LogisticOrderDO extends BaseEntity implements Serializable {

    /**
     * 接入方业务线标识  1, "自营商城"
     */
    private Integer businessIdentifier;

    /**
     * 订单号
     */
    private String orderId;

    /**
     * 商家id
     */
    private String sellerId;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 物流单号
     */
    private String logisticCode;
    /**
     * 物流单内容
     */
    private String content;
}
