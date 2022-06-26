package com.ruyuan.eshop.fulfill;

import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.enums.OrderStatusChangeEnum;
import com.ruyuan.eshop.fulfill.api.FulfillApi;
import com.ruyuan.eshop.fulfill.domain.event.OrderDeliveredWmsEvent;
import com.ruyuan.eshop.fulfill.domain.event.OrderOutStockWmsEvent;
import com.ruyuan.eshop.fulfill.domain.event.OrderSignedWmsEvent;
import com.ruyuan.eshop.fulfill.domain.request.ReceiveFulfillRequest;
import com.ruyuan.eshop.fulfill.domain.request.ReceiveOrderItemRequest;
import com.ruyuan.eshop.fulfill.domain.request.TriggerOrderWmsShipEventRequest;
import com.ruyuan.eshop.tms.api.TmsApi;
import com.ruyuan.eshop.tms.domain.SendOutRequest;
import org.apache.dubbo.config.annotation.DubboReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SpringBootTest(classes = FulfillApplication.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class FulfillApiTest {

    @DubboReference(version = "1.0.0")
    private FulfillApi fulfillApi;

    @DubboReference(version = "1.0.0")
    private TmsApi tmsApi;


    @Test
    public void triggerOrderWmsShipEvent() throws Exception {

        String orderId = "1011250000000010000";
        OrderStatusChangeEnum orderStatusChange = OrderStatusChangeEnum.ORDER_OUT_STOCKED;
        OrderOutStockWmsEvent wmsEvent1 = new OrderOutStockWmsEvent();
        wmsEvent1.setOrderId(orderId);
        wmsEvent1.setOutStockTime(new Date());

        TriggerOrderWmsShipEventRequest request = new TriggerOrderWmsShipEventRequest(
                orderId,"11",orderStatusChange,wmsEvent1
        );
        fulfillApi.triggerOrderWmsShipEvent(request);


        orderStatusChange = OrderStatusChangeEnum.ORDER_DELIVERED;
        OrderDeliveredWmsEvent wmsEvent2 = new OrderDeliveredWmsEvent();
        wmsEvent2.setOrderId(orderId);
        wmsEvent2.setDelivererNo("rc2019");
        wmsEvent2.setDelivererName("张三");
        wmsEvent2.setDelivererPhone("19100012112");

        request = new TriggerOrderWmsShipEventRequest(
                orderId,"11",orderStatusChange,wmsEvent2
        );

        fulfillApi.triggerOrderWmsShipEvent(request);


        orderStatusChange = OrderStatusChangeEnum.ORDER_SIGNED;
        OrderSignedWmsEvent wmsEvent3 = new OrderSignedWmsEvent();
        wmsEvent3.setOrderId(orderId);
        wmsEvent3.setSignedTime(new Date());

        request = new TriggerOrderWmsShipEventRequest(
                orderId,"11",orderStatusChange,wmsEvent3
        );

        fulfillApi.triggerOrderWmsShipEvent(request);
    }


    /**
     * 触发接收订单履约
     */
    @Test
    public void triggerReceiveOrderFulFill() throws Exception {

        String orderId = "1021120832956929100";
        String fulfillException = "";
        String wmsException = "";
        String tmsException = "true";

        ReceiveFulfillRequest request = buildReceiveFulFillRequest(orderId);
        request.setFulfillException(fulfillException);
        request.setWmsException(wmsException);
        request.setTmsException(tmsException);

        Thread.sleep(10000);

    }

    public ReceiveFulfillRequest buildReceiveFulFillRequest(String orderId) {

        //构造请求
        ReceiveFulfillRequest request = ReceiveFulfillRequest.builder()
                .businessIdentifier(1)
                .orderId(orderId)
                .sellerId("1")
                .userId("1")
                .deliveryType(1)
                .receiverName("1")
                .receiverPhone("1")
                .receiverProvince("1")
                .receiverCity("1")
                .receiverArea("1")
                .receiverStreet("1")
                .receiverDetailAddress("1")
                .receiverLat(new BigDecimal("1"))
                .receiverLon(new BigDecimal("1"))
                .payType(1)
                .payAmount(111)
                .totalAmount(111)
                .receiveOrderItems(buildReceiveOrderItemRequest(orderId))
                .deliveryAmount(11)
                .build();
        return request;
    }


    private List<ReceiveOrderItemRequest> buildReceiveOrderItemRequest(String orderId) {

        List<ReceiveOrderItemRequest> itemRequests = new ArrayList<>();

        ReceiveOrderItemRequest request = ReceiveOrderItemRequest.builder()
                .skuCode("1")
                .productName("1")
                .salePrice(1)
                .saleQuantity(1)
                .productUnit("1")
                .payAmount(111)
                .originAmount(111)
                .build();
        itemRequests.add(request);

        return itemRequests;
    }
}
