package com.ruyuan.eshop.order.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.ruyuan.eshop.common.constants.RocketMqConstant;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.exception.BaseBizException;
import com.ruyuan.eshop.common.message.ActualRefundMessage;
import com.ruyuan.eshop.common.utils.ExtJsonUtil;
import com.ruyuan.eshop.order.enums.AfterSaleStatusEnum;
import com.ruyuan.eshop.common.enums.AfterSaleTypeDetailEnum;
import com.ruyuan.eshop.common.enums.AfterSaleTypeEnum;
import com.ruyuan.eshop.common.enums.OrderStatusEnum;
import com.ruyuan.eshop.common.utils.ParamCheckUtil;
import com.ruyuan.eshop.order.dao.*;
import com.ruyuan.eshop.order.domain.dto.*;
import com.ruyuan.eshop.order.domain.entity.*;
import com.ruyuan.eshop.order.domain.request.LackItemRequest;
import com.ruyuan.eshop.order.domain.request.LackRequest;
import com.ruyuan.eshop.order.enums.*;
import com.ruyuan.eshop.order.exception.OrderBizException;
import com.ruyuan.eshop.order.exception.OrderErrorCodeEnum;
import com.ruyuan.eshop.order.manager.OrderNoManager;
import com.ruyuan.eshop.order.mq.producer.DefaultProducer;
import com.ruyuan.eshop.order.service.OrderLackService;
import com.ruyuan.eshop.order.service.amount.AfterSaleAmountService;
import com.ruyuan.eshop.product.api.ProductApi;
import com.ruyuan.eshop.product.domain.dto.ProductSkuDTO;
import com.ruyuan.eshop.product.domain.query.ProductSkuQuery;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class OrderLackServiceImpl implements OrderLackService {

    @Autowired
    private OrderInfoDAO orderInfoDAO;

    @Autowired
    private OrderItemDAO orderItemDAO;

    @Autowired
    private AfterSaleInfoDAO afterSaleInfoDAO;

    @Autowired
    private AfterSaleItemDAO afterSaleItemDAO;

    @Autowired
    private AfterSaleRefundDAO afterSaleRefundDAO;

    @Autowired
    private OrderNoManager orderNoManager;

    @Autowired
    private AfterSaleAmountService afterSaleAmountService;

    @DubboReference(version = "1.0.0")
    private ProductApi productApi;

    @Autowired
    private DefaultProducer defaultProducer;

    @Override
    public CheckLackDTO checkRequest(LackRequest request) throws OrderBizException {

        //1???????????????
        OrderInfoDO order = orderInfoDAO.getByOrderId(request.getOrderId());
        ParamCheckUtil.checkObjectNonNull(order, OrderErrorCodeEnum.ORDER_NOT_FOUND);

        //2???????????????????????????????????????
        // ????????????????????????????????????
        // ???1) ?????????????????????"?????????"
        //  (2) ????????????????????????

        // ?????????????????????"?????????"???????????????????????????
        // ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"??????"???
        // ??????????????????????????????????????????????????????????????????"?????????"?????????????????????????????????????????????
        if (!OrderStatusEnum.canLack().contains(order.getOrderStatus()) || isOrderLacked(order)) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_NOT_ALLOW_TO_LACK);
        }

        //3???????????????item
        List<OrderItemDO> orderItems = orderItemDAO.listByOrderId(request.getOrderId());

        //4???????????????????????????
        List<LackItemDTO> lackItems = new ArrayList<>();
        for (LackItemRequest itemRequest : request.getLackItems()) {
            lackItems.add(checkLackItem(order, orderItems, itemRequest));
        }

        //5???????????????
        return new CheckLackDTO(order, lackItems);
    }

    @Override
    public boolean isOrderLacked(OrderInfoDO order) {
        OrderExtJsonDTO orderExtJson = ExtJsonUtil.parseExtJson(order.getExtJson(),OrderExtJsonDTO.class);
        if(null != orderExtJson) {
            return orderExtJson.getLackFlag();
        }
        return false;
    }

    @Override
    public LackDTO executeLackRequest(LackRequest request, CheckLackDTO checkLackItemDTO) {
        OrderInfoDO order = checkLackItemDTO.getOrder();
        List<LackItemDTO> lackItems = checkLackItemDTO.getLackItems();

        //1????????????????????????
        AfterSaleInfoDO lackAfterSaleOrder = buildLackAfterSaleInfo(order);

        //2????????????????????????item
        List<AfterSaleItemDO> afterSaleItems = new ArrayList<>();
        lackItems.forEach(item -> {
            afterSaleItems.add(buildLackAfterSaleItem(order, lackAfterSaleOrder, item));
        });

        //3????????????????????????????????????
        Integer lackApplyRefundAmount = afterSaleAmountService.calculateOrderLackApplyRefundAmount(afterSaleItems);
        Integer lackRealRefundAmount = afterSaleAmountService.calculateOrderLackRealRefundAmount(afterSaleItems);
        lackAfterSaleOrder.setApplyRefundAmount(lackApplyRefundAmount);
        lackAfterSaleOrder.setRealRefundAmount(lackRealRefundAmount);

        //4????????????????????????
        AfterSaleRefundDO afterSaleRefund = buildLackAfterSaleRefundDO(order, lackAfterSaleOrder);

        //5?????????????????????????????????
        OrderExtJsonDTO lackExtJson = buildOrderLackExtJson(request, order, lackAfterSaleOrder);

        //6??????????????????,item????????????;
        TransactionMQProducer transactionMQProducer = defaultProducer.getProducer();

        transactionMQProducer.setTransactionListener(new TransactionListener() {

            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object o) {
                try {
                    saveAfterSaleData(lackAfterSaleOrder, afterSaleItems, afterSaleRefund, order, lackExtJson);
                    return LocalTransactionState.COMMIT_MESSAGE;
                } catch (BaseBizException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("system error", e);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }

            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                AfterSaleInfoDO afterSaleInfoDO = afterSaleInfoDAO.getOneByOrderId(Long.valueOf(order.getOrderId()));
                if(afterSaleInfoDO != null) {
                    return LocalTransactionState.COMMIT_MESSAGE;
                }
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }

        });

        //7??????????????????????????????
        ActualRefundMessage actualRefundMessage = new ActualRefundMessage();
        actualRefundMessage.setAfterSaleRefundId(afterSaleRefund.getId());
        actualRefundMessage.setOrderId(order.getOrderId());
        actualRefundMessage.setAfterSaleId(lackAfterSaleOrder.getAfterSaleId());

        Message msg = new Message(RocketMqConstant.ACTUAL_REFUND_TOPIC,
                JSONObject.toJSONString(actualRefundMessage).getBytes(StandardCharsets.UTF_8));

        try {
            TransactionSendResult result = transactionMQProducer.sendMessageInTransaction(msg, actualRefundMessage);
            if(!result.getLocalTransactionState().equals(LocalTransactionState.COMMIT_MESSAGE)) {
                throw new OrderBizException(OrderErrorCodeEnum.ORDER_PAY_CALLBACK_SEND_MQ_ERROR);
            }
        } catch(Exception e) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_PAY_CALLBACK_SEND_MQ_ERROR);
        }

        return new LackDTO(order.getOrderId(), lackAfterSaleOrder.getAfterSaleId());
    }

    @Transactional
    private void saveAfterSaleData(AfterSaleInfoDO lackAfterSaleOrder,
                                   List<AfterSaleItemDO> afterSaleItems,
                                   AfterSaleRefundDO afterSaleRefund,
                                   OrderInfoDO order,
                                   OrderExtJsonDTO lackExtJson) {
        afterSaleInfoDAO.save(lackAfterSaleOrder);
        afterSaleItemDAO.saveBatch(afterSaleItems);
        afterSaleRefundDAO.save(afterSaleRefund);
        orderInfoDAO.updateOrderExtJson(order.getOrderId(), lackExtJson);
    }

    /**
     * ??????????????????????????????
     *
     * @return
     */
    private OrderExtJsonDTO buildOrderLackExtJson(LackRequest request, OrderInfoDO order
            , AfterSaleInfoDO lackAfterSaleOrder) {

        OrderExtJsonDTO orderExtJson = new OrderExtJsonDTO();
        orderExtJson.setLackFlag(true);

        OrderLackInfoDTO lackInfo = new OrderLackInfoDTO();
        lackInfo.setLackItems(request.getLackItems());
        lackInfo.setOrderId(order.getOrderId());
        lackInfo.setApplyRefundAmount(lackInfo.getApplyRefundAmount());
        lackInfo.setRealRefundAmount(lackInfo.getRealRefundAmount());
        orderExtJson.setLackInfo(lackInfo);

        return orderExtJson;
    }

    /**
     * ?????????????????????
     *
     * @param order
     * @return
     */
    private AfterSaleInfoDO buildLackAfterSaleInfo(OrderInfoDO order) {

        //???????????????
        String userId = order.getUserId();
        AfterSaleInfoDO afterSaleInfoDO = new AfterSaleInfoDO();
        String afterSaleId = orderNoManager.genOrderId(OrderNoTypeEnum.AFTER_SALE.getCode(), userId);
        afterSaleInfoDO.setAfterSaleId(Long.valueOf(afterSaleId));
        afterSaleInfoDO.setOrderId(order.getOrderId());
        afterSaleInfoDO.setOrderSourceChannel(BusinessIdentifierEnum.SELF_MALL.getCode());
        afterSaleInfoDO.setUserId(userId);
        afterSaleInfoDO.setOrderType(OrderTypeEnum.NORMAL.getCode());
        afterSaleInfoDO.setAfterSaleType(AfterSaleTypeEnum.RETURN_MONEY.getCode());
        afterSaleInfoDO.setAfterSaleTypeDetail(AfterSaleTypeDetailEnum.LACK_REFUND.getCode());
        afterSaleInfoDO.setApplySource(AfterSaleApplySourceEnum.SYSTEM.getCode());
        afterSaleInfoDO.setAfterSaleStatus(AfterSaleStatusEnum.REVIEW_PASS.getCode());
        afterSaleInfoDO.setApplyTime(new Date());
        afterSaleInfoDO.setReviewTime(new Date());

        return afterSaleInfoDO;

    }

    /**
     * ?????????????????????item
     *
     * @param order
     * @return
     */
    private AfterSaleItemDO buildLackAfterSaleItem(OrderInfoDO order, AfterSaleInfoDO lackAfterSale
            , LackItemDTO lackItemDTO) {
        Integer lackNum = lackItemDTO.getLackNum();
        ProductSkuDTO productSku = lackItemDTO.getProductSku();
        OrderItemDO orderItem = lackItemDTO.getOrderItem();

        AfterSaleItemDO afterSaleItemDO = new AfterSaleItemDO();
        afterSaleItemDO.setAfterSaleId(lackAfterSale.getAfterSaleId());
        afterSaleItemDO.setOrderId(order.getOrderId());
        afterSaleItemDO.setProductName(productSku.getProductName());
        afterSaleItemDO.setSkuCode(productSku.getSkuCode());
        afterSaleItemDO.setReturnQuantity(lackNum);
        afterSaleItemDO.setProductImg(orderItem.getProductImg());
        afterSaleItemDO.setOriginAmount(orderItem.getOriginAmount());
        //??????sku??????????????????
        afterSaleItemDO.setApplyRefundAmount(orderItem.getSalePrice() * lackNum);
        afterSaleItemDO.setRealRefundAmount(afterSaleAmountService.calculateOrderItemLackRealRefundAmount(orderItem, lackNum));
        return afterSaleItemDO;
    }

    /**
     * ???????????????????????????
     *
     * @param order
     * @return
     */
    private AfterSaleRefundDO buildLackAfterSaleRefundDO(OrderInfoDO order, AfterSaleInfoDO afterSaleInfo) {

        //???????????????
        AfterSaleRefundDO AfterSaleRefundDO = new AfterSaleRefundDO();
        AfterSaleRefundDO.setAfterSaleId(String.valueOf(afterSaleInfo.getAfterSaleId()));
        AfterSaleRefundDO.setOrderId(order.getOrderId());
        AfterSaleRefundDO.setAccountType(AccountTypeEnum.THIRD.getCode());
        AfterSaleRefundDO.setPayType(order.getPayType());
        AfterSaleRefundDO.setRefundAmount(afterSaleInfo.getRealRefundAmount());
        AfterSaleRefundDO.setRefundStatus(RefundStatusEnum.UN_REFUND.getCode());

        return AfterSaleRefundDO;

    }

    /**
     * ???????????????
     *
     * @return
     */
    private LackItemDTO checkLackItem(OrderInfoDO order, List<OrderItemDO> orderItems, LackItemRequest request) {
        String skuCode = request.getSkuCode();
        Integer lackNum = request.getLackNum();

        //1???????????????
        ParamCheckUtil.checkStringNonEmpty(skuCode, OrderErrorCodeEnum.SKU_CODE_IS_NULL);
        ParamCheckUtil.checkIntMin(lackNum, 1, OrderErrorCodeEnum.LACK_NUM_IS_LT_0);

        //2???????????????sku
        String lockSkuCode = request.getSkuCode();

        ProductSkuQuery productSkuQuery = new ProductSkuQuery();
        productSkuQuery.setSkuCode(skuCode);
        productSkuQuery.setSellerId(order.getSellerId());
        JsonResult<ProductSkuDTO> skuJsonResult = productApi.getProductSku(productSkuQuery);
        if (!skuJsonResult.getSuccess()) {
            throw new OrderBizException(skuJsonResult.getErrorCode(), skuJsonResult.getErrorMessage());
        }
        ProductSkuDTO productSkuDTO = skuJsonResult.getData();
        ParamCheckUtil.checkObjectNonNull(productSkuDTO, OrderErrorCodeEnum.PRODUCT_SKU_CODE_ERROR, lockSkuCode);

        //3?????????item??????????????????sku item
        OrderItemDO lackItemDO = orderItems.stream().filter(item -> item.getSkuCode().equals(lockSkuCode))
                .findFirst().orElse(null);
        ParamCheckUtil.checkObjectNonNull(lackItemDO, OrderErrorCodeEnum.LACK_ITEM_NOT_IN_ORDER, lockSkuCode);

        //4???????????????????????????>=??????????????????
        if (lackItemDO.getSaleQuantity() <= request.getLackNum()) {
            throw new OrderBizException(OrderErrorCodeEnum.LACK_NUM_IS_GE_SKU_ORDER_ITEM_SIZE);
        }

        //5???????????????
        return new LackItemDTO(lackItemDO, lackNum, productSkuDTO);
    }

}
