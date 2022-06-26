package com.ruyuan.eshop.fulfill.builder;

import com.ruyuan.eshop.common.utils.ObjectUtil;
import com.ruyuan.eshop.fulfill.domain.entity.OrderFulfillDO;
import com.ruyuan.eshop.fulfill.domain.entity.OrderFulfillItemDO;
import com.ruyuan.eshop.fulfill.domain.request.ReceiveFulfillRequest;
import lombok.Data;

import java.util.List;

/**
 * 订单履约数据构造器
 * @author zhonghuashishan
 * @version 1.0
 */
@Data
public class FulfillDataBuilder {

    /**
     * 订单履约
     */
    private OrderFulfillDO orderFulFill;

    /**
     * 订单履约条目
     */
    private List<OrderFulfillItemDO> orderFulFillItems;

    /**
     * 接受订单履约请求
     */
    private ReceiveFulfillRequest receiveFulFillRequest;


    public FulfillDataBuilder(ReceiveFulfillRequest receiveFulFillRequest) {
        this.receiveFulFillRequest = receiveFulFillRequest;
    }

    public static FulfillDataBuilder builder(ReceiveFulfillRequest receiveFulfillRequest) {
        return new FulfillDataBuilder(receiveFulfillRequest);
    }

    public FulfillDataBuilder buildOrderFulfill(String fulfillId) {
        orderFulFill = receiveFulFillRequest.clone(OrderFulfillDO.class);
        orderFulFill.setFulfillId(fulfillId);
        return this;
    }

    public FulfillDataBuilder buildOrderFulfillItem() {
        orderFulFillItems = ObjectUtil
                .convertList(receiveFulFillRequest.getReceiveOrderItems(), OrderFulfillItemDO.class);

        //设置履约单ID
        for(OrderFulfillItemDO item : orderFulFillItems) {
            item.setFulfillId(orderFulFill.getFulfillId());
        }

        return this;
    }

}
