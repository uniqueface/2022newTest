package com.ruyuan.eshop.wms.api.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.utils.ObjectUtil;
import com.ruyuan.eshop.common.utils.RandomUtil;
import com.ruyuan.eshop.tms.api.TmsApi;
import com.ruyuan.eshop.tms.domain.SendOutDTO;
import com.ruyuan.eshop.tms.domain.SendOutRequest;
import com.ruyuan.eshop.wms.api.WmsApi;
import com.ruyuan.eshop.wms.dao.DeliveryOrderDAO;
import com.ruyuan.eshop.wms.dao.DeliveryOrderItemDAO;
import com.ruyuan.eshop.wms.domain.PickDTO;
import com.ruyuan.eshop.wms.domain.PickGoodsRequest;
import com.ruyuan.eshop.wms.domain.dto.ScheduleDeliveryResult;
import com.ruyuan.eshop.wms.domain.entity.DeliveryOrderDO;
import com.ruyuan.eshop.wms.domain.entity.DeliveryOrderItemDO;
import com.ruyuan.eshop.wms.exception.WmsBizException;
import com.ruyuan.eshop.wms.exception.WmsErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author zhonghuashishan
 * @version 1.0
 */

@Slf4j
@DubboService(version = "1.0.0", interfaceClass = WmsApi.class,retries = 0)
public class WmsApiImpl implements WmsApi {

    @Autowired
    private DeliveryOrderDAO deliveryOrderDAO;

    @Autowired
    private DeliveryOrderItemDAO deliveryOrderItemDAO;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public JsonResult<PickDTO> pickGoods(PickGoodsRequest request) {
        log.info("捡货,orderId={},request={}",request.getOrderId(), JSONObject.toJSONString(request));

        String wmsException = request.getWmsException();
        if(StringUtils.isNotBlank(wmsException) && wmsException.equals("true")) {
            throw new WmsBizException("捡货异常！");
        }

        //1、捡货，调度出库
        ScheduleDeliveryResult result = scheduleDelivery(request);

        //2、存储出库单和出库单条目
        deliveryOrderDAO.save(result.getDeliveryOrder());
        deliveryOrderItemDAO.saveBatch(result.getDeliveryOrderItems());

        //3、构造返回参数
        return JsonResult.buildSuccess(new PickDTO(request.getOrderId()));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public JsonResult<Boolean> cancelPickGoods(String orderId) {
        log.info("取消捡货,orderId={}",orderId);

        //1、查询出库单
        List<DeliveryOrderDO> deliveryOrders = deliveryOrderDAO.listByOrderId(orderId);

        //2、移除出库单和条目
        if(CollectionUtils.isNotEmpty(deliveryOrders)) {
            for(DeliveryOrderDO order : deliveryOrders) {
                List<DeliveryOrderItemDO> items = deliveryOrderItemDAO.listByDeliveryOrderId(order.getDeliveryOrderId());
                for(DeliveryOrderItemDO item : items) {
                    deliveryOrderItemDAO.removeById(item.getId());
                }

                deliveryOrderDAO.removeById(order.getId());
            }
        }

        return JsonResult.buildSuccess(true);
    }

    /**
     * 调度出库
     * @param request
     */
    private ScheduleDeliveryResult scheduleDelivery(PickGoodsRequest request) {
        log.info("orderId={}的订单进行调度出库",request.getOrderId());

        //1、生成出库单ID
        String deliveryOrderId = genDeliveryOrderId();

        //2、生成出库单
        DeliveryOrderDO deliveryOrder = request.clone(DeliveryOrderDO.class);
        deliveryOrder.setDeliveryOrderId(deliveryOrderId);

        //3、生成出库单条目
        List<DeliveryOrderItemDO> deliveryOrderItems = ObjectUtil.convertList(request.getOrderItems(),
                DeliveryOrderItemDO.class);
        for(DeliveryOrderItemDO item : deliveryOrderItems) {
            item.setDeliveryOrderId(deliveryOrderId);
        }

        //4、sku调度出库
        // 这里仅仅只是模拟，假设有一个无限货物的仓库货柜(id = 1)
        for(DeliveryOrderItemDO item : deliveryOrderItems) {
            item.setPickingCount(item.getSaleQuantity());
            item.setSkuContainerId(1);
        }
        return new ScheduleDeliveryResult(deliveryOrder,deliveryOrderItems);
    }

    /**
     * 生成履约单id
     * @return
     */
    private String genDeliveryOrderId() {
        return RandomUtil.genRandomNumber(10);
    }



}
