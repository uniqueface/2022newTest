package com.ruyuan.eshop.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.ruyuan.eshop.address.api.AddressApi;
import com.ruyuan.eshop.common.constants.RedisLockKeyConstants;
import com.ruyuan.eshop.common.constants.RocketDelayedLevel;
import com.ruyuan.eshop.common.constants.RocketMqConstant;
import com.ruyuan.eshop.common.core.CloneDirection;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.enums.AmountTypeEnum;
import com.ruyuan.eshop.common.enums.DeleteStatusEnum;
import com.ruyuan.eshop.common.enums.OrderStatusEnum;
import com.ruyuan.eshop.common.enums.PayTypeEnum;
import com.ruyuan.eshop.common.exception.BaseBizException;
import com.ruyuan.eshop.common.message.PaidOrderSuccessMessage;
import com.ruyuan.eshop.common.message.PayOrderTimeoutDelayMessage;
import com.ruyuan.eshop.common.redis.RedisLock;
import com.ruyuan.eshop.common.utils.JsonUtil;
import com.ruyuan.eshop.common.utils.ObjectUtil;
import com.ruyuan.eshop.common.utils.ParamCheckUtil;
import com.ruyuan.eshop.inventory.api.InventoryApi;
import com.ruyuan.eshop.inventory.domain.request.DeductProductStockRequest;
import com.ruyuan.eshop.market.api.MarketApi;
import com.ruyuan.eshop.market.domain.dto.CalculateOrderAmountDTO;
import com.ruyuan.eshop.market.domain.request.CalculateOrderAmountRequest;
import com.ruyuan.eshop.order.dao.OrderDeliveryDetailDAO;
import com.ruyuan.eshop.order.dao.OrderInfoDAO;
import com.ruyuan.eshop.order.dao.OrderPaymentDetailDAO;
import com.ruyuan.eshop.order.domain.dto.*;
import com.ruyuan.eshop.order.domain.entity.OrderDeliveryDetailDO;
import com.ruyuan.eshop.order.domain.entity.OrderInfoDO;
import com.ruyuan.eshop.order.domain.entity.OrderPaymentDetailDO;
import com.ruyuan.eshop.order.domain.request.*;
import com.ruyuan.eshop.order.enums.*;
import com.ruyuan.eshop.order.exception.OrderBizException;
import com.ruyuan.eshop.order.exception.OrderErrorCodeEnum;
import com.ruyuan.eshop.order.manager.OrderManager;
import com.ruyuan.eshop.order.manager.OrderNoManager;
import com.ruyuan.eshop.order.mq.producer.DefaultProducer;
import com.ruyuan.eshop.order.service.OrderService;
import com.ruyuan.eshop.pay.api.PayApi;
import com.ruyuan.eshop.pay.domain.dto.PayOrderDTO;
import com.ruyuan.eshop.pay.domain.request.PayOrderRequest;
import com.ruyuan.eshop.pay.domain.request.PayRefundRequest;
import com.ruyuan.eshop.product.api.ProductApi;
import com.ruyuan.eshop.product.domain.dto.ProductSkuDTO;
import com.ruyuan.eshop.product.domain.query.ProductSkuQuery;
import com.ruyuan.eshop.risk.api.RiskApi;
import com.ruyuan.eshop.risk.domain.dto.CheckOrderRiskDTO;
import com.ruyuan.eshop.risk.domain.request.CheckOrderRiskRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author zhonghuashishan
 * @version 1.0
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderInfoDAO orderInfoDAO;

    @Autowired
    private OrderDeliveryDetailDAO orderDeliveryDetailDAO;

    @Autowired
    private OrderPaymentDetailDAO orderPaymentDetailDAO;

    @Autowired
    private OrderNoManager orderNoManager;

    @Autowired
    private DefaultProducer defaultProducer;

    @Autowired
    private RedisLock redisLock;

    /**
     * ????????????
     */
    @DubboReference(version = "1.0.0")
    private ProductApi productApi;

    /**
     * ????????????
     */
    @DubboReference(version = "1.0.0", retries = 0)
    private RiskApi riskApi;

    /**
     * ????????????
     */
    @DubboReference(version = "1.0.0", retries = 0)
    private PayApi payApi;

    /**
     * ????????????
     */
    @DubboReference(version = "1.0.0")
    private AddressApi addressApi;

    /**
     * ????????????
     */
    @DubboReference(version = "1.0.0", retries = 0)
    private MarketApi marketApi;

    @Autowired
    private OrderManager orderManager;

    /**
     * ?????????????????????
     *
     * @param genOrderIdRequest ?????????????????????
     * @return ?????????
     */
    @Override
    public GenOrderIdDTO genOrderId(GenOrderIdRequest genOrderIdRequest) {
        // ????????????
        String userId = genOrderIdRequest.getUserId();
        ParamCheckUtil.checkStringNonEmpty(userId);
        Integer businessIdentifier = genOrderIdRequest.getBusinessIdentifier();
        ParamCheckUtil.checkObjectNonNull(businessIdentifier);

        String orderId = orderNoManager.genOrderId(OrderNoTypeEnum.SALE_ORDER.getCode(), userId);
        GenOrderIdDTO genOrderIdDTO = new GenOrderIdDTO();
        genOrderIdDTO.setOrderId(orderId);
        return genOrderIdDTO;
    }

    /**
     * ????????????/??????????????????
     *
     * @param createOrderRequest ????????????????????????
     * @return ?????????
     */
    @Override
    public CreateOrderDTO createOrder(CreateOrderRequest createOrderRequest) {
        // 1???????????????
        checkCreateOrderRequestParam(createOrderRequest);

        // 2???????????????
        checkRisk(createOrderRequest);

        // 3?????????????????????
        List<ProductSkuDTO> productSkuList = listProductSkus(createOrderRequest);

        // 4?????????????????????
        CalculateOrderAmountDTO calculateOrderAmountDTO = calculateOrderAmount(createOrderRequest, productSkuList);

        // 5???????????????????????????
        checkRealPayAmount(createOrderRequest, calculateOrderAmountDTO);

        // 6??????????????????????????????????????????????????????????????????
        createOrder(createOrderRequest, productSkuList, calculateOrderAmountDTO);

        // 7?????????????????????????????????????????????????????????
        sendPayOrderTimeoutDelayMessage(createOrderRequest);

        // ??????????????????
        CreateOrderDTO createOrderDTO = new CreateOrderDTO();
        createOrderDTO.setOrderId(createOrderRequest.getOrderId());
        return createOrderDTO;
    }

    /**
     * ????????????????????????
     * @param createOrderRequest
     * @param productSkuList
     * @param calculateOrderAmountDTO
     */
    private void createOrder(CreateOrderRequest createOrderRequest, List<ProductSkuDTO> productSkuList,
                             CalculateOrderAmountDTO calculateOrderAmountDTO) {
        // ????????????????????????
        orderManager.createOrder(createOrderRequest, productSkuList, calculateOrderAmountDTO);

    }

    /**
     * ??????????????????????????????
     */
    private void checkCreateOrderRequestParam(CreateOrderRequest createOrderRequest) {
        ParamCheckUtil.checkObjectNonNull(createOrderRequest);

        // ??????ID
        String orderId = createOrderRequest.getOrderId();
        ParamCheckUtil.checkStringNonEmpty(orderId, OrderErrorCodeEnum.ORDER_ID_IS_NULL);

        // ???????????????
        Integer businessIdentifier = createOrderRequest.getBusinessIdentifier();
        ParamCheckUtil.checkObjectNonNull(businessIdentifier, OrderErrorCodeEnum.BUSINESS_IDENTIFIER_IS_NULL);
        if (BusinessIdentifierEnum.getByCode(businessIdentifier) == null) {
            throw new OrderBizException(OrderErrorCodeEnum.BUSINESS_IDENTIFIER_ERROR);
        }

        // ??????ID
        String userId = createOrderRequest.getUserId();
        ParamCheckUtil.checkStringNonEmpty(userId, OrderErrorCodeEnum.USER_ID_IS_NULL);

        // ????????????
        Integer orderType = createOrderRequest.getOrderType();
        ParamCheckUtil.checkObjectNonNull(businessIdentifier, OrderErrorCodeEnum.ORDER_TYPE_IS_NULL);
        if (OrderTypeEnum.getByCode(orderType) == null) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_TYPE_ERROR);
        }

        // ??????ID
        String sellerId = createOrderRequest.getSellerId();
        ParamCheckUtil.checkStringNonEmpty(sellerId, OrderErrorCodeEnum.SELLER_ID_IS_NULL);

        // ????????????
        Integer deliveryType = createOrderRequest.getDeliveryType();
        ParamCheckUtil.checkObjectNonNull(deliveryType, OrderErrorCodeEnum.USER_ADDRESS_ERROR);
        if (DeliveryTypeEnum.getByCode(deliveryType) == null) {
            throw new OrderBizException(OrderErrorCodeEnum.DELIVERY_TYPE_ERROR);
        }

        // ????????????
        String province = createOrderRequest.getProvince();
        String city = createOrderRequest.getCity();
        String area = createOrderRequest.getArea();
        String streetAddress = createOrderRequest.getStreet();
        ParamCheckUtil.checkStringNonEmpty(province, OrderErrorCodeEnum.USER_ADDRESS_ERROR);
        ParamCheckUtil.checkStringNonEmpty(city, OrderErrorCodeEnum.USER_ADDRESS_ERROR);
        ParamCheckUtil.checkStringNonEmpty(area, OrderErrorCodeEnum.USER_ADDRESS_ERROR);
        ParamCheckUtil.checkStringNonEmpty(streetAddress, OrderErrorCodeEnum.USER_ADDRESS_ERROR);

        // ??????ID
        String regionId = createOrderRequest.getRegionId();
        ParamCheckUtil.checkStringNonEmpty(regionId, OrderErrorCodeEnum.REGION_ID_IS_NULL);

        // ?????????
        BigDecimal lon = createOrderRequest.getLon();
        BigDecimal lat = createOrderRequest.getLat();
        ParamCheckUtil.checkObjectNonNull(lon, OrderErrorCodeEnum.USER_LOCATION_IS_NULL);
        ParamCheckUtil.checkObjectNonNull(lat, OrderErrorCodeEnum.USER_LOCATION_IS_NULL);

        // ???????????????
        String receiverName = createOrderRequest.getReceiverName();
        String receiverPhone = createOrderRequest.getReceiverPhone();
        ParamCheckUtil.checkStringNonEmpty(receiverName, OrderErrorCodeEnum.ORDER_RECEIVER_IS_NULL);
        ParamCheckUtil.checkStringNonEmpty(receiverPhone, OrderErrorCodeEnum.ORDER_RECEIVER_IS_NULL);

        // ?????????????????????
        String clientIp = createOrderRequest.getClientIp();
        ParamCheckUtil.checkStringNonEmpty(clientIp, OrderErrorCodeEnum.CLIENT_IP_IS_NULL);

        // ??????????????????
        List<CreateOrderRequest.OrderItemRequest> orderItemRequestList = createOrderRequest.getOrderItemRequestList();
        ParamCheckUtil.checkCollectionNonEmpty(orderItemRequestList, OrderErrorCodeEnum.ORDER_ITEM_IS_NULL);

        for (CreateOrderRequest.OrderItemRequest orderItemRequest : orderItemRequestList) {
            Integer productType = orderItemRequest.getProductType();
            Integer saleQuantity = orderItemRequest.getSaleQuantity();
            String skuCode = orderItemRequest.getSkuCode();
            ParamCheckUtil.checkObjectNonNull(productType, OrderErrorCodeEnum.ORDER_ITEM_PARAM_ERROR);
            ParamCheckUtil.checkObjectNonNull(saleQuantity, OrderErrorCodeEnum.ORDER_ITEM_PARAM_ERROR);
            ParamCheckUtil.checkStringNonEmpty(skuCode, OrderErrorCodeEnum.ORDER_ITEM_PARAM_ERROR);
        }

        // ??????????????????
        List<CreateOrderRequest.OrderAmountRequest> orderAmountRequestList = createOrderRequest.getOrderAmountRequestList();
        ParamCheckUtil.checkCollectionNonEmpty(orderAmountRequestList, OrderErrorCodeEnum.ORDER_AMOUNT_IS_NULL);

        for (CreateOrderRequest.OrderAmountRequest orderAmountRequest : orderAmountRequestList) {
            Integer amountType = orderAmountRequest.getAmountType();
            ParamCheckUtil.checkObjectNonNull(amountType, OrderErrorCodeEnum.ORDER_AMOUNT_TYPE_IS_NULL);

            if (AmountTypeEnum.getByCode(amountType) == null) {
                throw new OrderBizException(OrderErrorCodeEnum.ORDER_AMOUNT_TYPE_PARAM_ERROR);
            }
        }
        Map<Integer, Integer> orderAmountMap = orderAmountRequestList.stream()
                .collect(Collectors.toMap(CreateOrderRequest.OrderAmountRequest::getAmountType,
                        CreateOrderRequest.OrderAmountRequest::getAmount));

        // ??????????????????????????????
        if (orderAmountMap.get(AmountTypeEnum.ORIGIN_PAY_AMOUNT.getCode()) == null) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_ORIGIN_PAY_AMOUNT_IS_NULL);
        }
        // ????????????????????????
        if (orderAmountMap.get(AmountTypeEnum.SHIPPING_AMOUNT.getCode()) == null) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_SHIPPING_AMOUNT_IS_NULL);
        }
        // ??????????????????????????????
        if (orderAmountMap.get(AmountTypeEnum.REAL_PAY_AMOUNT.getCode()) == null) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_REAL_PAY_AMOUNT_IS_NULL);
        }
        if (StringUtils.isNotEmpty(createOrderRequest.getCouponId())) {
            // ???????????????????????????????????????
            if (orderAmountMap.get(AmountTypeEnum.COUPON_DISCOUNT_AMOUNT.getCode()) == null) {
                throw new OrderBizException(OrderErrorCodeEnum.ORDER_DISCOUNT_AMOUNT_IS_NULL);
            }
        }

        // ??????????????????
        List<CreateOrderRequest.PaymentRequest> paymentRequestList = createOrderRequest.getPaymentRequestList();
        ParamCheckUtil.checkCollectionNonEmpty(paymentRequestList, OrderErrorCodeEnum.ORDER_PAYMENT_IS_NULL);

        for (CreateOrderRequest.PaymentRequest paymentRequest : paymentRequestList) {
            Integer payType = paymentRequest.getPayType();
            Integer accountType = paymentRequest.getAccountType();
            if (payType == null || PayTypeEnum.getByCode(payType) == null) {
                throw new OrderBizException(OrderErrorCodeEnum.PAY_TYPE_PARAM_ERROR);
            }
            if (accountType == null || AccountTypeEnum.getByCode(accountType) == null) {
                throw new OrderBizException(OrderErrorCodeEnum.ACCOUNT_TYPE_PARAM_ERROR);
            }
        }

    }

    /**
     * ????????????
     */
    private void checkRisk(CreateOrderRequest createOrderRequest) {
        // ????????????????????????????????????
        CheckOrderRiskRequest checkOrderRiskRequest = createOrderRequest.clone(CheckOrderRiskRequest.class);
        JsonResult<CheckOrderRiskDTO> jsonResult = riskApi.checkOrderRisk(checkOrderRiskRequest);
        if (!jsonResult.getSuccess()) {
            throw new OrderBizException(jsonResult.getErrorCode(), jsonResult.getErrorMessage());
        }
    }



    /**
     * ??????????????????????????????
     */
    private List<ProductSkuDTO> listProductSkus(CreateOrderRequest createOrderRequest) {
        List<CreateOrderRequest.OrderItemRequest> orderItemRequestList = createOrderRequest.getOrderItemRequestList();
        List<ProductSkuDTO> productSkuList = new ArrayList<>();

        List<ProductSkuQuery> queries = new ArrayList<ProductSkuQuery>();

        for (CreateOrderRequest.OrderItemRequest orderItemRequest : orderItemRequestList) {
            String skuCode = orderItemRequest.getSkuCode();

            ProductSkuQuery productSkuQuery = new ProductSkuQuery();
            productSkuQuery.setSkuCode(skuCode);
            productSkuQuery.setSellerId(createOrderRequest.getSellerId());
            JsonResult<ProductSkuDTO> jsonResult = productApi.getProductSku(productSkuQuery);
            queries.add(productSkuQuery);

            if (!jsonResult.getSuccess()) {
                throw new OrderBizException(jsonResult.getErrorCode(), jsonResult.getErrorMessage());
            }
            ProductSkuDTO productSkuDTO = jsonResult.getData();
            // sku?????????
            if (productSkuDTO == null) {
                throw new OrderBizException(OrderErrorCodeEnum.PRODUCT_SKU_CODE_ERROR, skuCode);
            }
            productSkuList.add(productSkuDTO);
        }

        // List<ProductSkuDTO> results = productApi.getProductSkus(queries);

        return productSkuList;
    }

    /**
     * ??????????????????
     * ?????????????????????????????????????????????????????????????????????
     *
     * @param createOrderRequest ????????????
     * @param productSkuList     ????????????
     */
    private CalculateOrderAmountDTO calculateOrderAmount(CreateOrderRequest createOrderRequest, List<ProductSkuDTO> productSkuList) {
        CalculateOrderAmountRequest calculateOrderPriceRequest = createOrderRequest.clone(CalculateOrderAmountRequest.class, CloneDirection.FORWARD);

        // ??????????????????????????????
        Map<String, ProductSkuDTO> productSkuDTOMap = productSkuList.stream().collect(Collectors.toMap(ProductSkuDTO::getSkuCode, Function.identity()));
        calculateOrderPriceRequest.getOrderItemRequestList().forEach(item -> {
            String skuCode = item.getSkuCode();
            ProductSkuDTO productSkuDTO = productSkuDTOMap.get(skuCode);
            item.setProductId(productSkuDTO.getProductId());
            item.setSalePrice(productSkuDTO.getSalePrice());
        });

        // ????????????????????????????????????
        JsonResult<CalculateOrderAmountDTO> jsonResult = marketApi.calculateOrderAmount(calculateOrderPriceRequest);

        // ????????????????????????
        if (!jsonResult.getSuccess()) {
            throw new OrderBizException(jsonResult.getErrorCode(), jsonResult.getErrorMessage());
        }
        CalculateOrderAmountDTO calculateOrderAmountDTO = jsonResult.getData();
        if (calculateOrderAmountDTO == null) {
            throw new OrderBizException(OrderErrorCodeEnum.CALCULATE_ORDER_AMOUNT_ERROR);
        }
        // ??????????????????
        List<OrderAmountDTO> orderAmountList = ObjectUtil.convertList(calculateOrderAmountDTO.getOrderAmountList(), OrderAmountDTO.class);
        if (orderAmountList == null || orderAmountList.isEmpty()) {
            throw new OrderBizException(OrderErrorCodeEnum.CALCULATE_ORDER_AMOUNT_ERROR);
        }

        // ????????????????????????
        List<OrderAmountDetailDTO> orderItemAmountList = ObjectUtil.convertList(calculateOrderAmountDTO.getOrderAmountDetail(), OrderAmountDetailDTO.class);
        if (orderItemAmountList == null || orderItemAmountList.isEmpty()) {
            throw new OrderBizException(OrderErrorCodeEnum.CALCULATE_ORDER_AMOUNT_ERROR);
        }
        return calculateOrderAmountDTO;
    }

    /**
     * ????????????????????????
     */
    private void checkRealPayAmount(CreateOrderRequest createOrderRequest, CalculateOrderAmountDTO calculateOrderAmountDTO) {
        List<CreateOrderRequest.OrderAmountRequest> originOrderAmountRequestList = createOrderRequest.getOrderAmountRequestList();
        Map<Integer, CreateOrderRequest.OrderAmountRequest> originOrderAmountMap =
                originOrderAmountRequestList.stream().collect(Collectors.toMap(
                        CreateOrderRequest.OrderAmountRequest::getAmountType, Function.identity()));
        // ????????????????????????
        Integer originRealPayAmount = originOrderAmountMap.get(AmountTypeEnum.REAL_PAY_AMOUNT.getCode()).getAmount();


        List<CalculateOrderAmountDTO.OrderAmountDTO> orderAmountDTOList = calculateOrderAmountDTO.getOrderAmountList();
        Map<Integer, CalculateOrderAmountDTO.OrderAmountDTO> orderAmountMap =
                orderAmountDTOList.stream().collect(Collectors.toMap(CalculateOrderAmountDTO.OrderAmountDTO::getAmountType, Function.identity()));
        // ?????????????????????????????????
        Integer realPayAmount = orderAmountMap.get(AmountTypeEnum.REAL_PAY_AMOUNT.getCode()).getAmount();

        if (!originRealPayAmount.equals(realPayAmount)) {
            // ??????????????????
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_CHECK_REAL_PAY_AMOUNT_FAIL);
        }
    }





    /**
     * ?????????????????????????????????????????????????????????????????????
     */
    private void sendPayOrderTimeoutDelayMessage(CreateOrderRequest createOrderRequest) {
        PayOrderTimeoutDelayMessage message = new PayOrderTimeoutDelayMessage();

        message.setOrderId(createOrderRequest.getOrderId());
        message.setBusinessIdentifier(createOrderRequest.getBusinessIdentifier());
        message.setCancelType(OrderCancelTypeEnum.TIMEOUT_CANCELED.getCode());
        message.setUserId(createOrderRequest.getUserId());
        message.setOrderType(createOrderRequest.getOrderType());
        message.setOrderStatus(OrderStatusEnum.CREATED.getCode());

        String msgJson = JsonUtil.object2Json(message);
        defaultProducer.sendMessage(RocketMqConstant.PAY_ORDER_TIMEOUT_DELAY_TOPIC, msgJson,
                RocketDelayedLevel.DELAYED_30m, "??????????????????????????????");
    }

    /**
     * ???????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrePayOrderDTO prePayOrder(PrePayOrderRequest prePayOrderRequest) {
        // ????????????
        checkPrePayOrderRequestParam(prePayOrderRequest);

        String orderId = prePayOrderRequest.getOrderId();
        Integer payAmount = prePayOrderRequest.getPayAmount();

        // ??????????????????????????????????????????????????????????????????
        String key = RedisLockKeyConstants.ORDER_PAY_KEY + orderId;
        boolean lock = redisLock.lock(key);
        if (!lock) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_PRE_PAY_ERROR);
        }
        try {
            // ???????????????????????????
            checkPrePayOrderInfo(orderId, payAmount);

            // ?????????????????????????????????
            PayOrderRequest payOrderRequest = prePayOrderRequest.clone(PayOrderRequest.class);
            JsonResult<PayOrderDTO> jsonResult = payApi.payOrder(payOrderRequest);
            if (!jsonResult.getSuccess()) {
                throw new OrderBizException(OrderErrorCodeEnum.ORDER_PRE_PAY_ERROR);
            }
            PayOrderDTO payOrderDTO = jsonResult.getData();

            // ?????????????????????????????????
            updateOrderPaymentInfo(payOrderDTO);

            return payOrderDTO.clone(PrePayOrderDTO.class);
        } finally {
            // ??????????????????
            redisLock.unlock(key);
        }
    }

    /**
     * ??????????????????????????????
     * @param orderId
     * @param payAmount
     */
    private void checkPrePayOrderInfo(String orderId, Integer payAmount) {
        // ??????????????????
        OrderInfoDO orderInfoDO = orderInfoDAO.getByOrderId(orderId);
        OrderPaymentDetailDO orderPaymentDetailDO = orderPaymentDetailDAO.getPaymentDetailByOrderId(orderId);
        if (orderInfoDO == null || orderPaymentDetailDO == null) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_INFO_IS_NULL);
        }

        // ????????????????????????
        if (!payAmount.equals(orderInfoDO.getPayAmount())) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_PAY_AMOUNT_ERROR);
        }

        // ????????????????????????
        if (!OrderStatusEnum.CREATED.getCode().equals(orderInfoDO.getOrderStatus())) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_STATUS_ERROR);
        }

        // ????????????????????????
        if (PayStatusEnum.PAID.getCode().equals(orderPaymentDetailDO.getPayStatus())) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_PAY_STATUS_IS_PAID);
        }

        // ???????????????????????????????????????
        Date curDate = new Date();
        if (curDate.after(orderInfoDO.getExpireTime())) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_PRE_PAY_EXPIRE_ERROR);
        }
    }

    /**
     * ???????????????????????????
     */
    private void checkPrePayOrderRequestParam(PrePayOrderRequest prePayOrderRequest) {

        String userId = prePayOrderRequest.getUserId();
        ParamCheckUtil.checkStringNonEmpty(userId, OrderErrorCodeEnum.USER_ID_IS_NULL);

        String businessIdentifier = prePayOrderRequest.getBusinessIdentifier();
        ParamCheckUtil.checkStringNonEmpty(businessIdentifier, OrderErrorCodeEnum.BUSINESS_IDENTIFIER_ERROR);

        Integer payType = prePayOrderRequest.getPayType();
        ParamCheckUtil.checkObjectNonNull(payType, OrderErrorCodeEnum.PAY_TYPE_PARAM_ERROR);
        if (PayTypeEnum.getByCode(payType) == null) {
            throw new OrderBizException(OrderErrorCodeEnum.PAY_TYPE_PARAM_ERROR);
        }

        String orderId = prePayOrderRequest.getOrderId();
        ParamCheckUtil.checkStringNonEmpty(orderId, OrderErrorCodeEnum.ORDER_ID_IS_NULL);

        Integer payAmount = prePayOrderRequest.getPayAmount();
        ParamCheckUtil.checkObjectNonNull(payAmount, OrderErrorCodeEnum.PAY_TYPE_PARAM_ERROR);
    }

    /**
     * ?????????????????????????????????
     */
    private void updateOrderPaymentInfo(PayOrderDTO payOrderDTO) {
        // ???????????????????????????
        String orderId = payOrderDTO.getOrderId();
        Integer payType = payOrderDTO.getPayType();
        String outTradeNo = payOrderDTO.getOutTradeNo();
        Date payTime = new Date();

        // ???????????????????????????
        updateOrder(orderId, payType, payTime);
        // ??????????????????????????????
        updateOrderPaymentDetail(orderId, payType, payTime, outTradeNo);

        // ??????????????????????????????????????????
        List<OrderInfoDO> subOrderInfoList = orderInfoDAO.listByParentOrderId(orderId);
        if (subOrderInfoList == null || subOrderInfoList.isEmpty()) {
            return;
        }

        // ???????????????????????????????????????????????????????????????????????????
        List<OrderInfoDO> tempSubOrderInfoList = new ArrayList<>();
        List<String> tempSubOrderIds = new ArrayList<>();
        getSubOrderDataList(subOrderInfoList, tempSubOrderInfoList, tempSubOrderIds,
                payType, payTime);
        // ???????????????????????????
        updateSubOrders(tempSubOrderInfoList);
        // ?????????????????????????????????
        updateSubOrderPaymentDetails(tempSubOrderIds, payType, payTime, outTradeNo);
    }

    private void updateOrder(String orderId, Integer payType, Date payTime) {
        OrderInfoDO orderInfoDO = orderInfoDAO.getByOrderId(orderId);
        orderInfoDO.setPayType(payType);
        orderInfoDO.setPayTime(payTime);
        orderInfoDAO.updateById(orderInfoDO);
    }

    private void updateOrderPaymentDetail(String orderId,
                                          Integer payType,
                                          Date payTime,
                                          String outTradeNo) {
        OrderPaymentDetailDO orderPaymentDetailDO = orderPaymentDetailDAO.getPaymentDetailByOrderId(orderId);
        orderPaymentDetailDO.setPayTime(payTime);
        orderPaymentDetailDO.setPayType(payType);
        orderPaymentDetailDO.setOutTradeNo(outTradeNo);
        orderPaymentDetailDAO.updateById(orderPaymentDetailDO);
    }

    private void getSubOrderDataList(List<OrderInfoDO> subOrders,
                                     List<OrderInfoDO> tempSubOrders,
                                     List<String> tempSubOrderIds,
                                     Integer payType,
                                     Date payTime) {
        for (OrderInfoDO subOrderInfoDO : subOrders) {
            subOrderInfoDO.setPayType(payType);
            subOrderInfoDO.setPayTime(payTime);
            tempSubOrders.add(subOrderInfoDO);
            tempSubOrderIds.add(subOrderInfoDO.getOrderId());
        }
    }

    private void updateSubOrders(List<OrderInfoDO> subOrders) {
        orderInfoDAO.updateBatchById(subOrders);
    }

    private void updateSubOrderPaymentDetails(List<String> tempSubOrderIds,
                                              Integer payType,
                                              Date payTime,
                                              String outTradeNo) {
        List<OrderPaymentDetailDO> orderPaymentDetailDOList =
                orderPaymentDetailDAO.listByOrderIds(tempSubOrderIds);
        if(orderPaymentDetailDOList != null && !orderPaymentDetailDOList.isEmpty()) {
            List<OrderPaymentDetailDO> tempSubOrderPaymentDetailList = new ArrayList<>();
            for(OrderPaymentDetailDO subOrderPaymentDetailDO : orderPaymentDetailDOList) {
                subOrderPaymentDetailDO.setPayTime(payTime);
                subOrderPaymentDetailDO.setPayType(payType);
                subOrderPaymentDetailDO.setOutTradeNo(outTradeNo);
                tempSubOrderPaymentDetailList.add(subOrderPaymentDetailDO);
            }
            orderPaymentDetailDAO.updateBatchById(tempSubOrderPaymentDetailList);
        }
    }

    /**
     * ????????????
     * ???????????????2???????????????????????????????????????????????????????????????????????????or??????
     * ??????????????????????????????????????????????????????????????????
     */
    @Override
    public void payCallback(PayCallbackRequest payCallbackRequest) {
        // ??????????????????????????????
        String orderId = payCallbackRequest.getOrderId();
        Integer payType = payCallbackRequest.getPayType();

        // ??????????????????????????????????????????
        OrderInfoDO orderInfoDO = orderInfoDAO.getByOrderId(orderId);
        OrderPaymentDetailDO orderPaymentDetailDO = orderPaymentDetailDAO
                .getPaymentDetailByOrderId(orderId);

        // ????????????
        checkPayCallbackRequestParam(payCallbackRequest,
                orderInfoDO, orderPaymentDetailDO);

        // ???????????????????????????????????????????????????
        List<String> redisKeyList = Lists.newArrayList();
        payCallbackMultiLock(redisKeyList, orderId);

        // ????????????????????????????????????????????????????????????????????????????????????????????????
        if (PayStatusEnum.PAID.getCode().equals(orderPaymentDetailDO.getPayStatus())) {
            if (payType.equals(orderPaymentDetailDO.getPayType())) {
                return;
            }
        }

        // ??????????????????
        try {
            Integer orderStatus = orderInfoDO.getOrderStatus();
            if (OrderStatusEnum.CREATED.getCode().equals(orderStatus)) {
                doPayCallback(payCallbackRequest, orderInfoDO, orderPaymentDetailDO, orderId);
            } else if(OrderStatusEnum.CANCELED.getCode().equals(orderStatus)) {
                payCallbackCancel(orderInfoDO, orderPaymentDetailDO, payType);
            } else {
                payCallbackRefund(orderInfoDO, orderPaymentDetailDO);
            }
        } catch (Exception e) {
            throw new OrderBizException(e.getMessage());
        } finally {
            // ??????????????????
            redisLock.unMultiLock(redisKeyList);
        }
    }

    private void doPayCallback(PayCallbackRequest payCallbackRequest,
                               OrderInfoDO orderInfoDO,
                               OrderPaymentDetailDO orderPaymentDetailDO,
                               String orderId) throws Exception {
        TransactionMQProducer transactionMQProducer = defaultProducer.getProducer();
        setupPaidOrderSuccessMessageListener(transactionMQProducer, payCallbackRequest,
                orderInfoDO, orderPaymentDetailDO, orderId);
        sendPaidOrderSuccessMessage(transactionMQProducer, orderInfoDO);
    }

    private void setupPaidOrderSuccessMessageListener(TransactionMQProducer transactionMQProducer,
                                                      PayCallbackRequest payCallbackRequest,
                                                      OrderInfoDO orderInfoDO,
                                                      OrderPaymentDetailDO orderPaymentDetailDO,
                                                      String orderId) {
        transactionMQProducer.setTransactionListener(new TransactionListener() {

            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object o) {
                try {
                    orderManager.updateOrderStatusPaid(payCallbackRequest, orderInfoDO, orderPaymentDetailDO);
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
                // ??????????????????????????????
                OrderInfoDO orderInfoDO = orderInfoDAO.getByOrderId(orderId);
                if(orderInfoDO != null
                        && OrderStatusEnum.PAID.getCode().equals(orderInfoDO.getOrderStatus())) {
                    return LocalTransactionState.COMMIT_MESSAGE;
                }
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }

        });
    }

    private void payCallbackMultiLock(List<String> redisKeyList,
                                      String orderId) {
        // ???????????????????????????????????????????????????
        String orderPayKey = RedisLockKeyConstants.ORDER_PAY_KEY + orderId;
        // ?????????????????????????????????????????????????????????????????????????????????
        String cancelOrderKey = RedisLockKeyConstants.CANCEL_KEY + orderId;
        redisKeyList.add(orderPayKey);
        redisKeyList.add(cancelOrderKey);
        boolean lock = redisLock.multiLock(redisKeyList);
        if (!lock) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_PAY_CALLBACK_ERROR);
        }
    }

    private void payCallbackRefund(OrderInfoDO orderInfoDO,
                                   OrderPaymentDetailDO orderPaymentDetailDO) {
        // ????????????
        executeOrderRefund(orderInfoDO, orderPaymentDetailDO);
        throw new OrderBizException(OrderErrorCodeEnum.ORDER_CANCEL_PAY_CALLBACK_REPEAT_ERROR);
    }

    private void payCallbackCancel(OrderInfoDO orderInfoDO,
                                   OrderPaymentDetailDO orderPaymentDetailDO,
                                   Integer payType) {
        // ????????????????????????????????????
        Integer payStatus = orderPaymentDetailDO.getPayStatus();
        if (PayStatusEnum.UNPAID.getCode().equals(payStatus)) {
            // ????????????
            executeOrderRefund(orderInfoDO, orderPaymentDetailDO);
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_CANCEL_PAY_CALLBACK_ERROR);
        } else if (PayStatusEnum.PAID.getCode().equals(payStatus)) {
            if (payType.equals(orderPaymentDetailDO.getPayType())) {
                throw new OrderBizException(OrderErrorCodeEnum.ORDER_CANCEL_PAY_CALLBACK_PAY_TYPE_SAME_ERROR);
            } else {
                throw new OrderBizException(OrderErrorCodeEnum.ORDER_CANCEL_PAY_CALLBACK_PAY_TYPE_NO_SAME_ERROR);
            }
        }
    }

    /**
     * ??????????????????
     */
    private void executeOrderRefund(OrderInfoDO orderInfoDO, OrderPaymentDetailDO orderPaymentDetailDO) {
        PayRefundRequest payRefundRequest = new PayRefundRequest();
        payRefundRequest.setOrderId(orderInfoDO.getOrderId());
        payRefundRequest.setRefundAmount(orderPaymentDetailDO.getPayAmount());
        payRefundRequest.setOutTradeNo(orderPaymentDetailDO.getOutTradeNo());
        payApi.executeRefund(payRefundRequest);
    }

    /**
     * ????????????????????????????????????
     */
    private void checkPayCallbackRequestParam(PayCallbackRequest payCallbackRequest,
                                              OrderInfoDO orderInfoDO,
                                              OrderPaymentDetailDO orderPaymentDetailDO) {
        ParamCheckUtil.checkObjectNonNull(payCallbackRequest);

        // ?????????
        String orderId = payCallbackRequest.getOrderId();
        ParamCheckUtil.checkStringNonEmpty(orderId);

        // ????????????
        Integer payAmount = payCallbackRequest.getPayAmount();
        ParamCheckUtil.checkObjectNonNull(payAmount);

        // ???????????????????????????
        String outTradeNo = payCallbackRequest.getOutTradeNo();
        ParamCheckUtil.checkStringNonEmpty(outTradeNo);

        // ????????????
        Integer payType = payCallbackRequest.getPayType();
        ParamCheckUtil.checkObjectNonNull(payType);
        if (PayTypeEnum.getByCode(payType) == null) {
            throw new OrderBizException(OrderErrorCodeEnum.PAY_TYPE_PARAM_ERROR);
        }

        // ??????ID
        String merchantId = payCallbackRequest.getMerchantId();
        ParamCheckUtil.checkStringNonEmpty(merchantId);

        // ????????????
        if (orderInfoDO == null || orderPaymentDetailDO == null) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_INFO_IS_NULL);
        }
        if (!payAmount.equals(orderInfoDO.getPayAmount())) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_CALLBACK_PAY_AMOUNT_ERROR);
        }
    }

    /**
     * ????????????????????????????????????????????????????????????
     */
    private void sendPaidOrderSuccessMessage(TransactionMQProducer transactionMQProducer, OrderInfoDO orderInfoDO)
            throws MQClientException {
        PaidOrderSuccessMessage message = new PaidOrderSuccessMessage();
        String orderId = orderInfoDO.getOrderId();
        message.setOrderId(orderId);

        String topic = RocketMqConstant.PAID_ORDER_SUCCESS_TOPIC;
        byte[] body = JSON.toJSONString(message).getBytes(StandardCharsets.UTF_8);
        Message msg = new Message(topic, body);

        TransactionSendResult result = transactionMQProducer.sendMessageInTransaction(msg, orderInfoDO);
        if(!result.getLocalTransactionState().equals(LocalTransactionState.COMMIT_MESSAGE)) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_PAY_CALLBACK_SEND_MQ_ERROR);
        }
    }

    @Override
    public boolean removeOrders(List<String> orderIds) {
        //1?????????id????????????
        List<OrderInfoDO> orders = orderInfoDAO.listByOrderIds(orderIds);
        if (CollectionUtils.isEmpty(orders)) {
            return true;
        }

        //2?????????????????????????????????
        orders.forEach(order -> {
            if (!canRemove(order)) {
                throw new OrderBizException(OrderErrorCodeEnum.ORDER_CANNOT_REMOVE);
            }
        });

        //3???????????????????????????
        List<Long> ids = orders.stream().map(OrderInfoDO::getId).collect(Collectors.toList());
        orderInfoDAO.softRemoveOrders(ids);

        return true;
    }

    private boolean canRemove(OrderInfoDO order) {
        return OrderStatusEnum.canRemoveStatus().contains(order.getOrderStatus()) &&
                DeleteStatusEnum.NO.getCode().equals(order.getDeleteStatus());
    }

    @Override
    public boolean adjustDeliveryAddress(AdjustDeliveryAddressRequest request) {
        //1?????????id????????????
        OrderInfoDO order = orderInfoDAO.getByOrderId(request.getOrderId());
        ParamCheckUtil.checkObjectNonNull(order, OrderErrorCodeEnum.ORDER_NOT_FOUND);

        //2??????????????????????????????
        if (!OrderStatusEnum.unOutStockStatus().contains(order.getOrderStatus())) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_NOT_ALLOW_TO_ADJUST_ADDRESS);
        }

        //3???????????????????????????
        OrderDeliveryDetailDO orderDeliveryDetail = orderDeliveryDetailDAO.getByOrderId(request.getOrderId());
        if (null == orderDeliveryDetail) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_DELIVERY_NOT_FOUND);
        }

        //4???????????????????????????????????????????????????
        if (orderDeliveryDetail.getModifyAddressCount() > 0) {
            throw new OrderBizException(OrderErrorCodeEnum.ORDER_DELIVERY_ADDRESS_HAS_BEEN_ADJUSTED);
        }

        //5???????????????????????????
        orderDeliveryDetailDAO.updateDeliveryAddress(orderDeliveryDetail.getId()
                , orderDeliveryDetail.getModifyAddressCount(), request);

        return true;
    }
}