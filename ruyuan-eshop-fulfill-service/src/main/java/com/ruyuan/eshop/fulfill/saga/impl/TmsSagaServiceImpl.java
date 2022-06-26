package com.ruyuan.eshop.fulfill.saga.impl;

import com.alibaba.fastjson.JSONObject;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.utils.ObjectUtil;
import com.ruyuan.eshop.fulfill.dao.OrderFulfillDAO;
import com.ruyuan.eshop.fulfill.domain.entity.OrderFulfillDO;
import com.ruyuan.eshop.fulfill.domain.request.ReceiveFulfillRequest;
import com.ruyuan.eshop.fulfill.exception.FulfillBizException;
import com.ruyuan.eshop.fulfill.exception.FulfillErrorCodeEnum;
import com.ruyuan.eshop.fulfill.saga.TmsSagaService;
import com.ruyuan.eshop.tms.api.TmsApi;
import com.ruyuan.eshop.tms.domain.SendOutDTO;
import com.ruyuan.eshop.tms.domain.SendOutRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 * @author zhonghuashishan
 * @version 1.0
 */
@Service("tmsSagaService")
@Slf4j
public class TmsSagaServiceImpl implements TmsSagaService {

    @DubboReference(version = "1.0.0",retries = 0)
    private TmsApi tmsApi;

    @Autowired
    private OrderFulfillDAO orderFulfillDAO;

    @Override
    public Boolean sendOut(ReceiveFulfillRequest request) {

        log.info("发货，request={}", JSONObject.toJSONString(request));

        //1、调用tms进行发货
        JsonResult<SendOutDTO> jsonResult = tmsApi.sendOut(buildSendOutRequest(request));
        if(!jsonResult.getSuccess()) {
            throw new FulfillBizException(FulfillErrorCodeEnum.TMS_IS_ERROR);
        }

        log.info("发货结果，jsonResult={}", JSONObject.toJSONString(jsonResult));
        if(!jsonResult.getSuccess()) {
            throw new FulfillBizException(FulfillErrorCodeEnum.WMS_IS_ERROR);
        }

        //2、查询履约单
        OrderFulfillDO orderFulfill = orderFulfillDAO.getOne(request.getOrderId());

        //3、存储物流单号
        String logisticsCode = jsonResult.getData().getLogisticsCode();
        orderFulfillDAO.saveLogisticsCode(orderFulfill.getFulfillId(),logisticsCode);

        return true;
    }

    @Override
    public Boolean sendOutCompensate(ReceiveFulfillRequest request) {
        log.info("补偿发货，request={}", JSONObject.toJSONString(request));

        //调用tms进行补偿发货
        JsonResult<Boolean> jsonResult = tmsApi.cancelSendOut(request.getOrderId());
        if(!jsonResult.getSuccess()) {
            throw new FulfillBizException(FulfillErrorCodeEnum.TMS_IS_ERROR);
        }

        log.info("补偿发货结果，jsonResult={}", JSONObject.toJSONString(jsonResult));
        if(!jsonResult.getSuccess()) {
            throw new FulfillBizException(FulfillErrorCodeEnum.TMS_IS_ERROR);
        }

        return true;
    }

    private SendOutRequest buildSendOutRequest(ReceiveFulfillRequest fulfillRequest) {
        SendOutRequest request = fulfillRequest.clone(SendOutRequest.class);
        List<SendOutRequest.OrderItemRequest> itemRequests = ObjectUtil
                .convertList(fulfillRequest.getReceiveOrderItems(),SendOutRequest.OrderItemRequest.class);
        request.setOrderItems(itemRequests);
        return request;
    }
}
