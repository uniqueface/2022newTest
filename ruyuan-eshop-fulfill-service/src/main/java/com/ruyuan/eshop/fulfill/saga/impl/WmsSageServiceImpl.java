package com.ruyuan.eshop.fulfill.saga.impl;

import com.alibaba.fastjson.JSONObject;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.utils.ObjectUtil;
import com.ruyuan.eshop.fulfill.domain.request.ReceiveFulfillRequest;
import com.ruyuan.eshop.fulfill.exception.FulfillBizException;
import com.ruyuan.eshop.fulfill.exception.FulfillErrorCodeEnum;
import com.ruyuan.eshop.fulfill.saga.WmsSagaService;
import com.ruyuan.eshop.wms.api.WmsApi;
import com.ruyuan.eshop.wms.domain.PickDTO;
import com.ruyuan.eshop.wms.domain.PickGoodsRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("wmsSageService")
@Slf4j
public class WmsSageServiceImpl implements WmsSagaService {

    @DubboReference(version = "1.0.0",retries = 0)
    private WmsApi wmsApi;

    @Override
    public Boolean pickGoods(ReceiveFulfillRequest request) {
        log.info("捡货，request={}", JSONObject.toJSONString(request));

        //调用wms系统进行捡货
        JsonResult<PickDTO> jsonResult = wmsApi
                .pickGoods(buildPickGoodsRequest(request));

        log.info("捡货结果，jsonResult={}", JSONObject.toJSONString(jsonResult));
        if(!jsonResult.getSuccess()) {
            throw new FulfillBizException(FulfillErrorCodeEnum.WMS_IS_ERROR);
        }

        return true;
    }

    @Override
    public Boolean pickGoodsCompensate(ReceiveFulfillRequest request) {

        log.info("补偿捡货，request={}", JSONObject.toJSONString(request));

        //调用wms系统进行捡货
        JsonResult<Boolean> jsonResult = wmsApi
                .cancelPickGoods(request.getOrderId());

        log.info("补偿捡货结果，jsonResult={}", JSONObject.toJSONString(jsonResult));
        if(!jsonResult.getSuccess()) {
            throw new FulfillBizException(FulfillErrorCodeEnum.WMS_IS_ERROR);
        }

        return true;
    }



    private PickGoodsRequest buildPickGoodsRequest(ReceiveFulfillRequest fulfillRequest) {
        PickGoodsRequest request = fulfillRequest.clone(PickGoodsRequest.class);
        List<PickGoodsRequest.OrderItemRequest> itemRequests = ObjectUtil
                .convertList(fulfillRequest.getReceiveOrderItems(), PickGoodsRequest.OrderItemRequest.class);
        request.setOrderItems(itemRequests);
        return request;
    }
}
