package com.ruyuan.eshop.fulfill.api.impl;

import com.alibaba.fastjson.JSONObject;
import com.ruyuan.eshop.common.bean.SpringApplicationContext;
import com.ruyuan.eshop.common.constants.RocketMqConstant;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.enums.OrderStatusChangeEnum;
import com.ruyuan.eshop.fulfill.api.FulfillApi;
import com.ruyuan.eshop.fulfill.domain.request.CancelFulfillRequest;
import com.ruyuan.eshop.fulfill.domain.request.ReceiveFulfillRequest;
import com.ruyuan.eshop.fulfill.domain.request.TriggerOrderWmsShipEventRequest;
import com.ruyuan.eshop.fulfill.exception.FulfillBizException;
import com.ruyuan.eshop.fulfill.mq.producer.DefaultProducer;
import com.ruyuan.eshop.fulfill.service.FulfillService;
import com.ruyuan.eshop.fulfill.service.OrderWmsShipEventProcessor;
import com.ruyuan.eshop.fulfill.service.impl.OrderDeliveredWmsEventProcessor;
import com.ruyuan.eshop.fulfill.service.impl.OrderOutStockWmsEventProcessor;
import com.ruyuan.eshop.fulfill.service.impl.OrderSignedWmsEventProcessor;
import com.ruyuan.eshop.tms.api.TmsApi;
import com.ruyuan.eshop.wms.api.WmsApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zhonghuashishan
 * @version 1.0
 */
@Slf4j
@DubboService(version = "1.0.0", interfaceClass = FulfillApi.class, retries = 0)
public class FulfillApiImpl implements FulfillApi {

    @Autowired
    private SpringApplicationContext springApplicationContext;

    @Autowired
    private DefaultProducer defaultProducer;

    @Autowired
    private FulfillService fulfillService;

    @DubboReference(version = "1.0.0", retries = 0)
    private WmsApi wmsApi;

    @DubboReference(version = "1.0.0", retries = 0)
    private TmsApi tmsApi;


    @Override
    public JsonResult<Boolean> receiveOrderFulFill(ReceiveFulfillRequest request) {
        try {
            Boolean result = fulfillService.receiveOrderFulFill(request);
            return JsonResult.buildSuccess(result);
        } catch (FulfillBizException e) {
            log.error("biz error", e);
            return JsonResult.buildError(e.getErrorCode(), e.getErrorMsg());
        } catch (Exception e) {
            log.error("system error", e);
            return JsonResult.buildError(e.getMessage());
        }
    }

    @Override
    public JsonResult<Boolean> triggerOrderWmsShipEvent(TriggerOrderWmsShipEventRequest request) {
        log.info("???????????????????????????????????????request={}", JSONObject.toJSONString(request));

        //1??????????????????
        OrderStatusChangeEnum orderStatusChange = request.getOrderStatusChange();
        OrderWmsShipEventProcessor processor = getWmsShipEventProcessor(orderStatusChange);

        //2?????????
        if(null != processor) {
            processor.execute(request);
        }

        return JsonResult.buildSuccess(true);
    }


    @Override
    public JsonResult<Boolean> cancelFulfill(CancelFulfillRequest cancelFulfillRequest) {
        log.info("???????????????request={}",JSONObject.toJSONString(cancelFulfillRequest));

        //1??????????????????
        fulfillService.cancelFulfillOrder(cancelFulfillRequest.getOrderId());
        //2???????????????
        wmsApi.cancelPickGoods(cancelFulfillRequest.getOrderId());
        //3???????????????
        tmsApi.cancelSendOut(cancelFulfillRequest.getOrderId());

        return JsonResult.buildSuccess(true);
    }

    /**
     * ?????????????????????????????????
     * @param orderStatusChange
     * @return
     */
    private OrderWmsShipEventProcessor getWmsShipEventProcessor(OrderStatusChangeEnum orderStatusChange) {
        if (OrderStatusChangeEnum.ORDER_OUT_STOCKED.equals(orderStatusChange)) {
            //?????????????????????
            return springApplicationContext.getBean(OrderOutStockWmsEventProcessor.class);
        } else if (OrderStatusChangeEnum.ORDER_DELIVERED.equals(orderStatusChange)) {
            //?????????????????????
            return springApplicationContext.getBean(OrderDeliveredWmsEventProcessor.class);
        } else if (OrderStatusChangeEnum.ORDER_SIGNED.equals(orderStatusChange)) {
            //?????????????????????
            return springApplicationContext.getBean(OrderSignedWmsEventProcessor.class);
        }
        return null;
    }

}
