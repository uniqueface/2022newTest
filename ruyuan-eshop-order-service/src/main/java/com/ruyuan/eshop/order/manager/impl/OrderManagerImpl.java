package com.ruyuan.eshop.order.manager.impl;

import com.ruyuan.eshop.address.api.AddressApi;
import com.ruyuan.eshop.address.domain.dto.AddressDTO;
import com.ruyuan.eshop.address.domain.query.AddressQuery;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.enums.AmountTypeEnum;
import com.ruyuan.eshop.common.enums.OrderOperateTypeEnum;
import com.ruyuan.eshop.common.enums.OrderStatusEnum;
import com.ruyuan.eshop.common.utils.JsonUtil;
import com.ruyuan.eshop.common.utils.ObjectUtil;
import com.ruyuan.eshop.inventory.api.InventoryApi;
import com.ruyuan.eshop.inventory.domain.request.DeductProductStockRequest;
import com.ruyuan.eshop.market.api.MarketApi;
import com.ruyuan.eshop.market.domain.dto.CalculateOrderAmountDTO;
import com.ruyuan.eshop.market.domain.dto.UserCouponDTO;
import com.ruyuan.eshop.market.domain.query.UserCouponQuery;
import com.ruyuan.eshop.market.domain.request.LockUserCouponRequest;
import com.ruyuan.eshop.order.builder.FullOrderData;
import com.ruyuan.eshop.order.builder.NewOrderBuilder;
import com.ruyuan.eshop.order.config.OrderProperties;
import com.ruyuan.eshop.order.dao.*;
import com.ruyuan.eshop.order.domain.entity.*;
import com.ruyuan.eshop.order.domain.request.CreateOrderRequest;
import com.ruyuan.eshop.order.domain.request.PayCallbackRequest;
import com.ruyuan.eshop.order.enums.OrderNoTypeEnum;
import com.ruyuan.eshop.order.enums.PayStatusEnum;
import com.ruyuan.eshop.order.enums.SnapshotTypeEnum;
import com.ruyuan.eshop.order.exception.OrderBizException;
import com.ruyuan.eshop.order.manager.OrderManager;
import com.ruyuan.eshop.order.manager.OrderNoManager;
import com.ruyuan.eshop.order.service.impl.NewOrderDataHolder;
import com.ruyuan.eshop.product.domain.dto.ProductSkuDTO;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author zhonghuashishan
 * @version 1.0
 */
@Service
public class OrderManagerImpl implements OrderManager {

    @Autowired
    private OrderInfoDAO orderInfoDAO;

    @Autowired
    private OrderItemDAO orderItemDAO;

    @Autowired
    private OrderPaymentDetailDAO orderPaymentDetailDAO;

    @Autowired
    private OrderOperateLogDAO orderOperateLogDAO;

    @Autowired
    private OrderAmountDAO orderAmountDAO;

    @Autowired
    private OrderAmountDetailDAO orderAmountDetailDAO;

    @Autowired
    private OrderDeliveryDetailDAO orderDeliveryDetailDAO;

    @Autowired
    private OrderSnapshotDAO orderSnapshotDAO;

    @Autowired
    private OrderProperties orderProperties;

    /**
     * ????????????
     */
    @DubboReference(version = "1.0.0", retries = 0)
    private MarketApi marketApi;


    /**
     * ????????????
     */
    @DubboReference(version = "1.0.0")
    private AddressApi addressApi;


    @Autowired
    private OrderNoManager orderNoManager;

    /**
     * ????????????
     */
    @DubboReference(version = "1.0.0", retries = 0)
    private InventoryApi inventoryApi;


    /**
     * ??????????????????????????????
     * @param payCallbackRequest
     * @param orderInfoDO
     * @param orderPaymentDetailDO
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateOrderStatusPaid(PayCallbackRequest payCallbackRequest,
                                      OrderInfoDO orderInfoDO,
                                      OrderPaymentDetailDO orderPaymentDetailDO) {
        // ??????????????????
        updateOrderStatus(orderInfoDO, OrderStatusEnum.PAID.getCode());
        // ????????????????????????
        updateOrderPaymentDetail(orderPaymentDetailDO);
        // ??????????????????????????????
        saveOrderOperateLog(orderInfoDO.getOrderId(),
                orderInfoDO.getOrderStatus(), OrderStatusEnum.PAID.getCode());

        // ???????????????????????????
        List<OrderInfoDO> subOrderInfoDOList = orderInfoDAO
                .listByParentOrderId(orderInfoDO.getOrderId());
        if (subOrderInfoDOList == null || subOrderInfoDOList.isEmpty()) {
            return;
        }

        // ??????????????????????????????????????????
        updateOrderStatus(orderInfoDO, OrderStatusEnum.INVALID.getCode());
        // ??????????????????????????????
        saveOrderOperateLog(orderInfoDO.getOrderId(),
                orderInfoDO.getOrderStatus(), OrderStatusEnum.INVALID.getCode());

        // ???????????????????????????
        for (OrderInfoDO subOrderInfo : subOrderInfoDOList) {
            // ????????????????????????
            updateOrderStatus(subOrderInfo, OrderStatusEnum.PAID.getCode());
            // ????????????????????????????????????
            updateSubOrderPaymentDetail(subOrderInfo);
            // ??????????????????????????????
            saveOrderOperateLog(subOrderInfo.getOrderId(),
                    subOrderInfo.getOrderStatus(), OrderStatusEnum.PAID.getCode());
        }
    }

    private void updateOrderStatus(OrderInfoDO orderInfoDO, Integer orderStatus) {
        orderInfoDO.setOrderStatus(orderStatus);
        orderInfoDAO.updateById(orderInfoDO);
    }

    private void updateOrderPaymentDetail(OrderPaymentDetailDO orderPaymentDetailDO) {
        orderPaymentDetailDO.setPayStatus(PayStatusEnum.PAID.getCode());
        orderPaymentDetailDAO.updateById(orderPaymentDetailDO);
    }

    private void updateSubOrderPaymentDetail(OrderInfoDO subOrderInfo) {
        String subOrderId = subOrderInfo.getOrderId();
        OrderPaymentDetailDO subOrderPaymentDetailDO =
                orderPaymentDetailDAO.getPaymentDetailByOrderId(subOrderId);
        if (subOrderPaymentDetailDO != null) {
            subOrderPaymentDetailDO.setPayStatus(PayStatusEnum.PAID.getCode());
            orderPaymentDetailDAO.updateById(subOrderPaymentDetailDO);
        }
    }

    private OrderOperateLogDO saveOrderOperateLog(String orderId,
                                     Integer preOrderStatus,
                                     Integer currentOrderStatus) {
        OrderOperateLogDO orderOperateLogDO = new OrderOperateLogDO();
        orderOperateLogDO.setOrderId(orderId);
        orderOperateLogDO.setOperateType(OrderOperateTypeEnum.PAID_ORDER.getCode());
        orderOperateLogDO.setPreStatus(preOrderStatus);
        orderOperateLogDO.setCurrentStatus(currentOrderStatus);
        orderOperateLogDO.setRemark("????????????????????????"
                + orderOperateLogDO.getPreStatus() + "-"
                + orderOperateLogDO.getCurrentStatus());
        orderOperateLogDAO.save(orderOperateLogDO);

        return orderOperateLogDO;
    }

    /**
     * ????????????
     * @param createOrderRequest
     * @param productSkuList
     * @param calculateOrderAmountDTO
     */
    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public void createOrder(CreateOrderRequest createOrderRequest, List<ProductSkuDTO> productSkuList, CalculateOrderAmountDTO calculateOrderAmountDTO) {
        // ???????????????
        lockUserCoupon(createOrderRequest);

        // ????????????
        deductProductStock(createOrderRequest);

        // ????????????????????????
        addNewOrder(createOrderRequest, productSkuList, calculateOrderAmountDTO);
    }


    /**
     * ?????????????????????
     */
    private void lockUserCoupon(CreateOrderRequest createOrderRequest) {
        String couponId = createOrderRequest.getCouponId();
        if (StringUtils.isEmpty(couponId)) {
            return;
        }
        LockUserCouponRequest lockUserCouponRequest = createOrderRequest.clone(LockUserCouponRequest.class);
        // ???????????????????????????????????????
        JsonResult<Boolean> jsonResult = marketApi.lockUserCoupon(lockUserCouponRequest);
        // ?????????????????????????????????
        if (!jsonResult.getSuccess()) {
            throw new OrderBizException(jsonResult.getErrorCode(), jsonResult.getErrorMessage());
        }
    }

    /**
     * ??????????????????
     *
     * @param createOrderRequest ????????????
     */
    private void deductProductStock(CreateOrderRequest createOrderRequest) {
        String orderId = createOrderRequest.getOrderId();
        List<DeductProductStockRequest.OrderItemRequest> orderItemRequestList = ObjectUtil.convertList(
                createOrderRequest.getOrderItemRequestList(), DeductProductStockRequest.OrderItemRequest.class);

        DeductProductStockRequest lockProductStockRequest = new DeductProductStockRequest();
        lockProductStockRequest.setOrderId(orderId);
        lockProductStockRequest.setOrderItemRequestList(orderItemRequestList);
        JsonResult<Boolean> jsonResult = inventoryApi.deductProductStock(lockProductStockRequest);
        // ??????????????????????????????
        if (!jsonResult.getSuccess()) {
            throw new OrderBizException(jsonResult.getErrorCode(), jsonResult.getErrorMessage());
        }
    }

    /**
     * ??????????????????????????????
     */
    private void addNewOrder(CreateOrderRequest createOrderRequest, List<ProductSkuDTO> productSkuList, CalculateOrderAmountDTO calculateOrderAmountDTO) {

        // ?????????????????????
        NewOrderDataHolder newOrderDataHolder = new NewOrderDataHolder();

        // ???????????????
        FullOrderData fullMasterOrderData = addNewMasterOrder(createOrderRequest, productSkuList, calculateOrderAmountDTO);

        // ????????????????????????NewOrderData?????????
        newOrderDataHolder.appendOrderData(fullMasterOrderData);


        // ??????????????????????????????????????????????????????????????????
        Map<Integer, List<ProductSkuDTO>> productTypeMap = productSkuList.stream().collect(Collectors.groupingBy(ProductSkuDTO::getProductType));
        if (productTypeMap.keySet().size() > 1) {
            for (Integer productType : productTypeMap.keySet()) {
                // ???????????????
                FullOrderData fullSubOrderData = addNewSubOrder(fullMasterOrderData, productType);

                // ????????????????????????NewOrderData?????????
                newOrderDataHolder.appendOrderData(fullSubOrderData);
            }
        }

        // ????????????????????????
        // ????????????
        List<OrderInfoDO> orderInfoDOList = newOrderDataHolder.getOrderInfoDOList();
        if (!orderInfoDOList.isEmpty()) {
            orderInfoDAO.saveBatch(orderInfoDOList);
        }

        // ????????????
        List<OrderItemDO> orderItemDOList = newOrderDataHolder.getOrderItemDOList();
        if (!orderItemDOList.isEmpty()) {
            orderItemDAO.saveBatch(orderItemDOList);
        }

        // ??????????????????
        List<OrderDeliveryDetailDO> orderDeliveryDetailDOList = newOrderDataHolder.getOrderDeliveryDetailDOList();
        if (!orderDeliveryDetailDOList.isEmpty()) {
            orderDeliveryDetailDAO.saveBatch(orderDeliveryDetailDOList);
        }

        // ??????????????????
        List<OrderPaymentDetailDO> orderPaymentDetailDOList = newOrderDataHolder.getOrderPaymentDetailDOList();
        if (!orderPaymentDetailDOList.isEmpty()) {
            orderPaymentDetailDAO.saveBatch(orderPaymentDetailDOList);
        }

        // ??????????????????
        List<OrderAmountDO> orderAmountDOList = newOrderDataHolder.getOrderAmountDOList();
        if (!orderAmountDOList.isEmpty()) {
            orderAmountDAO.saveBatch(orderAmountDOList);
        }

        // ??????????????????
        List<OrderAmountDetailDO> orderAmountDetailDOList = newOrderDataHolder.getOrderAmountDetailDOList();
        if (!orderAmountDetailDOList.isEmpty()) {
            orderAmountDetailDAO.saveBatch(orderAmountDetailDOList);
        }

        // ??????????????????????????????
        List<OrderOperateLogDO> orderOperateLogDOList = newOrderDataHolder.getOrderOperateLogDOList();
        if (!orderOperateLogDOList.isEmpty()) {
            orderOperateLogDAO.saveBatch(orderOperateLogDOList);
        }

        // ??????????????????
        List<OrderSnapshotDO> orderSnapshotDOList = newOrderDataHolder.getOrderSnapshotDOList();
        if (!orderSnapshotDOList.isEmpty()) {
            orderSnapshotDAO.saveBatch(orderSnapshotDOList);
        }
    }

    /**
     * ???????????????????????????
     */
    private FullOrderData addNewMasterOrder(CreateOrderRequest createOrderRequest, List<ProductSkuDTO> productSkuList, CalculateOrderAmountDTO calculateOrderAmountDTO) {

        NewOrderBuilder newOrderBuilder = new NewOrderBuilder(createOrderRequest, productSkuList, calculateOrderAmountDTO, orderProperties);
        FullOrderData fullOrderData = newOrderBuilder.buildOrder()
                .buildOrderItems()
                .buildOrderDeliveryDetail()
                .buildOrderPaymentDetail()
                .buildOrderAmount()
                .buildOrderAmountDetail()
                .buildOperateLog()
                .buildOrderSnapshot()
                .build();

        // ????????????
        OrderInfoDO orderInfoDO = fullOrderData.getOrderInfoDO();

        // ??????????????????
        List<OrderItemDO> orderItemDOList = fullOrderData.getOrderItemDOList();

        // ??????????????????
        List<OrderAmountDO> orderAmountDOList = fullOrderData.getOrderAmountDOList();

        // ??????????????????
        OrderDeliveryDetailDO orderDeliveryDetailDO = fullOrderData.getOrderDeliveryDetailDO();
        String detailAddress = getDetailAddress(orderDeliveryDetailDO);
        orderDeliveryDetailDO.setDetailAddress(detailAddress);

        // ??????????????????????????????
        OrderOperateLogDO orderOperateLogDO = fullOrderData.getOrderOperateLogDO();
        String remark = "??????????????????0-10";
        orderOperateLogDO.setRemark(remark);

        // ??????????????????????????????
        List<OrderSnapshotDO> orderSnapshotDOList = fullOrderData.getOrderSnapshotDOList();
        for (OrderSnapshotDO orderSnapshotDO : orderSnapshotDOList) {
            // ???????????????
            if (orderSnapshotDO.getSnapshotType().equals(SnapshotTypeEnum.ORDER_COUPON.getCode())) {
                String couponId = orderInfoDO.getCouponId();
                String userId = orderInfoDO.getUserId();
                UserCouponQuery userCouponQuery = new UserCouponQuery();
                userCouponQuery.setCouponId(couponId);
                userCouponQuery.setUserId(userId);
                JsonResult<UserCouponDTO> jsonResult = marketApi.getUserCoupon(userCouponQuery);
                if (jsonResult.getSuccess()) {
                    UserCouponDTO userCouponDTO = jsonResult.getData();
                    if (userCouponDTO != null) {
                        orderSnapshotDO.setSnapshotJson(JsonUtil.object2Json(userCouponDTO));
                    }
                } else {
                    orderSnapshotDO.setSnapshotJson(JsonUtil.object2Json(couponId));
                }
            }
            // ??????????????????
            else if (orderSnapshotDO.getSnapshotType().equals(SnapshotTypeEnum.ORDER_AMOUNT.getCode())) {
                orderSnapshotDO.setSnapshotJson(JsonUtil.object2Json(orderAmountDOList));
            }
            // ??????????????????
            else if (orderSnapshotDO.getSnapshotType().equals(SnapshotTypeEnum.ORDER_ITEM.getCode())) {
                orderSnapshotDO.setSnapshotJson(JsonUtil.object2Json(orderItemDOList));
            }
        }

        return fullOrderData;
    }

    /**
     * ??????????????????????????????
     */
    private String getDetailAddress(OrderDeliveryDetailDO orderDeliveryDetailDO) {
        String provinceCode = orderDeliveryDetailDO.getProvince();
        String cityCode = orderDeliveryDetailDO.getCity();
        String areaCode = orderDeliveryDetailDO.getArea();
        String streetCode = orderDeliveryDetailDO.getStreet();
        AddressQuery query = new AddressQuery();
        query.setProvinceCode(provinceCode);
        query.setCityCode(cityCode);
        query.setAreaCode(areaCode);
        query.setStreetCode(streetCode);
        JsonResult<AddressDTO> jsonResult = addressApi.queryAddress(query);
        if (!jsonResult.getSuccess() || jsonResult.getData() == null) {
            return orderDeliveryDetailDO.getDetailAddress();
        }

        AddressDTO addressDTO = jsonResult.getData();
        StringBuilder detailAddress = new StringBuilder();
        if (StringUtils.isNotEmpty(addressDTO.getProvince())) {
            detailAddress.append(addressDTO.getProvince());
        }
        if (StringUtils.isNotEmpty(addressDTO.getCity())) {
            detailAddress.append(addressDTO.getCity());
        }
        if (StringUtils.isNotEmpty(addressDTO.getArea())) {
            detailAddress.append(addressDTO.getArea());
        }
        if (StringUtils.isNotEmpty(addressDTO.getStreet())) {
            detailAddress.append(addressDTO.getStreet());
        }
        if (StringUtils.isNotEmpty(orderDeliveryDetailDO.getDetailAddress())) {
            detailAddress.append(orderDeliveryDetailDO.getDetailAddress());
        }
        return detailAddress.toString();
    }

    /**
     * ????????????
     *
     * @param fullOrderData ????????????
     * @param productType   ????????????
     */
    private FullOrderData addNewSubOrder(FullOrderData fullOrderData, Integer productType) {

        // ????????????
        OrderInfoDO orderInfoDO = fullOrderData.getOrderInfoDO();
        // ???????????????
        List<OrderItemDO> orderItemDOList = fullOrderData.getOrderItemDOList();
        // ?????????????????????
        OrderDeliveryDetailDO orderDeliveryDetailDO = fullOrderData.getOrderDeliveryDetailDO();
        // ?????????????????????
        List<OrderPaymentDetailDO> orderPaymentDetailDOList = fullOrderData.getOrderPaymentDetailDOList();
        // ?????????????????????
        List<OrderAmountDO> orderAmountDOList = fullOrderData.getOrderAmountDOList();
        // ?????????????????????
        List<OrderAmountDetailDO> orderAmountDetailDOList = fullOrderData.getOrderAmountDetailDOList();
        // ?????????????????????????????????
        OrderOperateLogDO orderOperateLogDO = fullOrderData.getOrderOperateLogDO();
        // ?????????????????????
        List<OrderSnapshotDO> orderSnapshotDOList = fullOrderData.getOrderSnapshotDOList();


        // ????????????
        String orderId = orderInfoDO.getOrderId();
        // ??????ID
        String userId = orderInfoDO.getUserId();

        // ?????????????????????????????????
        String subOrderId = orderNoManager.genOrderId(OrderNoTypeEnum.SALE_ORDER.getCode(), userId);

        // ????????????????????????
        FullOrderData subFullOrderData = new FullOrderData();

        // ????????????????????????????????????????????????
        List<OrderItemDO> subOrderItemDOList = orderItemDOList.stream()
                .filter(orderItemDO -> productType.equals(orderItemDO.getProductType()))
                .collect(Collectors.toList());

        // ?????????????????????
        Integer subTotalAmount = 0;
        Integer subRealPayAmount = 0;
        for (OrderItemDO subOrderItemDO : subOrderItemDOList) {
            subTotalAmount += subOrderItemDO.getOriginAmount();
            subRealPayAmount += subOrderItemDO.getPayAmount();
        }

        // ???????????????
        OrderInfoDO newSubOrderInfo = orderInfoDO.clone(OrderInfoDO.class);
        newSubOrderInfo.setId(null);
        newSubOrderInfo.setOrderId(subOrderId);
        newSubOrderInfo.setParentOrderId(orderId);
        newSubOrderInfo.setOrderStatus(OrderStatusEnum.INVALID.getCode());
        newSubOrderInfo.setTotalAmount(subTotalAmount);
        newSubOrderInfo.setPayAmount(subRealPayAmount);
        subFullOrderData.setOrderInfoDO(newSubOrderInfo);

        // ????????????
        List<OrderItemDO> newSubOrderItemList = new ArrayList<>();
        for (OrderItemDO orderItemDO : subOrderItemDOList) {
            OrderItemDO newSubOrderItem = orderItemDO.clone(OrderItemDO.class);
            newSubOrderItem.setId(null);
            newSubOrderItem.setOrderId(subOrderId);
            String subOrderItemId = getSubOrderItemId(orderItemDO.getOrderItemId(), subOrderId);
            newSubOrderItem.setOrderItemId(subOrderItemId);
            newSubOrderItemList.add(newSubOrderItem);
        }
        subFullOrderData.setOrderItemDOList(newSubOrderItemList);

        // ????????????????????????
        OrderDeliveryDetailDO newSubOrderDeliveryDetail = orderDeliveryDetailDO.clone(OrderDeliveryDetailDO.class);
        newSubOrderDeliveryDetail.setId(null);
        newSubOrderDeliveryDetail.setOrderId(subOrderId);
        subFullOrderData.setOrderDeliveryDetailDO(newSubOrderDeliveryDetail);


        Map<String, OrderItemDO> subOrderItemMap = subOrderItemDOList.stream()
                .collect(Collectors.toMap(OrderItemDO::getOrderItemId, Function.identity()));

        // ???????????????????????????
        Integer subTotalOriginPayAmount = 0;
        Integer subTotalCouponDiscountAmount = 0;
        Integer subTotalRealPayAmount = 0;

        // ??????????????????
        List<OrderAmountDetailDO> subOrderAmountDetailList = new ArrayList<>();
        for (OrderAmountDetailDO orderAmountDetailDO : orderAmountDetailDOList) {
            String orderItemId = orderAmountDetailDO.getOrderItemId();
            if (!subOrderItemMap.containsKey(orderItemId)) {
                continue;
            }
            OrderAmountDetailDO subOrderAmountDetail = orderAmountDetailDO.clone(OrderAmountDetailDO.class);
            subOrderAmountDetail.setId(null);
            subOrderAmountDetail.setOrderId(subOrderId);
            String subOrderItemId = getSubOrderItemId(orderItemId, subOrderId);
            subOrderAmountDetail.setOrderItemId(subOrderItemId);
            subOrderAmountDetailList.add(subOrderAmountDetail);

            Integer amountType = orderAmountDetailDO.getAmountType();
            Integer amount = orderAmountDetailDO.getAmount();
            if (AmountTypeEnum.ORIGIN_PAY_AMOUNT.getCode().equals(amountType)) {
                subTotalOriginPayAmount += amount;
            }
            if (AmountTypeEnum.COUPON_DISCOUNT_AMOUNT.getCode().equals(amountType)) {
                subTotalCouponDiscountAmount += amount;
            }
            if (AmountTypeEnum.REAL_PAY_AMOUNT.getCode().equals(amountType)) {
                subTotalRealPayAmount += amount;
            }
        }
        subFullOrderData.setOrderAmountDetailDOList(subOrderAmountDetailList);

        // ??????????????????
        List<OrderAmountDO> subOrderAmountList = new ArrayList<>();
        for (OrderAmountDO orderAmountDO : orderAmountDOList) {
            Integer amountType = orderAmountDO.getAmountType();
            OrderAmountDO subOrderAmount = orderAmountDO.clone(OrderAmountDO.class);
            subOrderAmount.setId(null);
            subOrderAmount.setOrderId(subOrderId);
            if (AmountTypeEnum.ORIGIN_PAY_AMOUNT.getCode().equals(amountType)) {
                subOrderAmount.setAmount(subTotalOriginPayAmount);
                subOrderAmountList.add(subOrderAmount);
            }
            if (AmountTypeEnum.COUPON_DISCOUNT_AMOUNT.getCode().equals(amountType)) {
                subOrderAmount.setAmount(subTotalCouponDiscountAmount);
                subOrderAmountList.add(subOrderAmount);
            }
            if (AmountTypeEnum.REAL_PAY_AMOUNT.getCode().equals(amountType)) {
                subOrderAmount.setAmount(subTotalRealPayAmount);
                subOrderAmountList.add(subOrderAmount);
            }
        }
        subFullOrderData.setOrderAmountDOList(subOrderAmountList);

        // ??????????????????
        List<OrderPaymentDetailDO> subOrderPaymentDetailDOList = new ArrayList<>();
        for (OrderPaymentDetailDO orderPaymentDetailDO : orderPaymentDetailDOList) {
            OrderPaymentDetailDO subOrderPaymentDetail = orderPaymentDetailDO.clone(OrderPaymentDetailDO.class);
            subOrderPaymentDetail.setId(null);
            subOrderPaymentDetail.setOrderId(subOrderId);
            subOrderPaymentDetail.setPayAmount(subTotalRealPayAmount);
            subOrderPaymentDetailDOList.add(subOrderPaymentDetail);
        }
        subFullOrderData.setOrderPaymentDetailDOList(subOrderPaymentDetailDOList);

        // ??????????????????????????????
        OrderOperateLogDO subOrderOperateLogDO = orderOperateLogDO.clone(OrderOperateLogDO.class);
        subOrderOperateLogDO.setId(null);
        subOrderOperateLogDO.setOrderId(subOrderId);
        subFullOrderData.setOrderOperateLogDO(subOrderOperateLogDO);

        // ????????????????????????
        List<OrderSnapshotDO> subOrderSnapshotDOList = new ArrayList<>();
        for (OrderSnapshotDO orderSnapshotDO : orderSnapshotDOList) {
            OrderSnapshotDO subOrderSnapshotDO = orderSnapshotDO.clone(OrderSnapshotDO.class);
            subOrderSnapshotDO.setId(null);
            subOrderSnapshotDO.setOrderId(subOrderId);
            if (SnapshotTypeEnum.ORDER_AMOUNT.getCode().equals(orderSnapshotDO.getSnapshotType())) {
                subOrderSnapshotDO.setSnapshotJson(JsonUtil.object2Json(subOrderAmountList));
            } else if (SnapshotTypeEnum.ORDER_ITEM.getCode().equals(orderSnapshotDO.getSnapshotType())) {
                subOrderSnapshotDO.setSnapshotJson(JsonUtil.object2Json(subOrderItemDOList));
            }
            subOrderSnapshotDOList.add(subOrderSnapshotDO);
        }
        subFullOrderData.setOrderSnapshotDOList(subOrderSnapshotDOList);
        return subFullOrderData;
    }

    /**
     * ??????????????????orderItemId???
     */
    private String getSubOrderItemId(String orderItemId, String subOrderId) {
        String postfix = orderItemId.substring(orderItemId.indexOf("_"));
        return subOrderId + postfix;
    }


}