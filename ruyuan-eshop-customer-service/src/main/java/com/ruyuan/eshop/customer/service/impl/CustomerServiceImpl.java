package com.ruyuan.eshop.customer.service.impl;

import com.ruyuan.eshop.common.constants.RedisLockKeyConstants;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.redis.RedisLock;
import com.ruyuan.eshop.common.utils.ParamCheckUtil;
import com.ruyuan.eshop.customer.dao.CustomerReceivesAfterSaleInfoDAO;
import com.ruyuan.eshop.customer.domain.entity.CustomerReceivesAfterSaleInfoDO;
import com.ruyuan.eshop.customer.domain.request.CustomerReceiveAfterSaleRequest;
import com.ruyuan.eshop.customer.domain.request.CustomerReviewReturnGoodsRequest;
import com.ruyuan.eshop.customer.exception.CustomerBizException;
import com.ruyuan.eshop.customer.exception.CustomerErrorCodeEnum;
import com.ruyuan.eshop.customer.service.CustomerService;
import com.ruyuan.eshop.order.api.AfterSaleApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * @author zhonghuashishan
 * @version 1.0
 */
@Service
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    @DubboReference(version = "1.0.0")
    private AfterSaleApi afterSaleApi;

    @Autowired
    private RedisLock redisLock;

    @Autowired
    private CustomerReceivesAfterSaleInfoDAO customerReceivesAfterSaleInfoDAO;

    @Override
    public JsonResult<Boolean> receiveAfterSale(CustomerReceiveAfterSaleRequest customerReceiveAfterSaleRequest) {
        //  1、校验入参
        checkCustomerReceiveAfterSaleRequest(customerReceiveAfterSaleRequest);

        //  2、分布式锁
        String afterSaleId = customerReceiveAfterSaleRequest.getAfterSaleId();
        String key = RedisLockKeyConstants.REFUND_KEY + afterSaleId;
        boolean lock = redisLock.lock(key);
        if (!lock) {
            throw new CustomerBizException(CustomerErrorCodeEnum.PROCESS_RECEIVE_AFTER_SALE_REPEAT);
        }

        try {
            // 3、保存售后申请数据
            CustomerReceivesAfterSaleInfoDO customerReceivesAfterSaleInfoDO =
                    customerReceiveAfterSaleRequest.clone(CustomerReceivesAfterSaleInfoDO.class);
            customerReceivesAfterSaleInfoDAO.save(customerReceivesAfterSaleInfoDO);
            log.info("客服保存售后申请信息成功,afterSaleId:{}", customerReceiveAfterSaleRequest.getAfterSaleId());
            return JsonResult.buildSuccess(true);
        } catch (Exception e) {
            throw new CustomerBizException(CustomerErrorCodeEnum.SAVE_AFTER_SALE_INFO_FAILED);
        } finally {
            //  4、放锁
            redisLock.unlock(key);
        }
    }

    @Override
    public JsonResult<Boolean> customerAudit(CustomerReviewReturnGoodsRequest customerReviewReturnGoodsRequest) {
        return afterSaleApi.receiveCustomerAuditResult(customerReviewReturnGoodsRequest);
    }

    private void checkCustomerReceiveAfterSaleRequest(CustomerReceiveAfterSaleRequest customerReceiveAfterSaleRequest) {
        ParamCheckUtil.checkStringNonEmpty(customerReceiveAfterSaleRequest.getUserId(), CustomerErrorCodeEnum.USER_ID_IS_NULL);
        ParamCheckUtil.checkStringNonEmpty(customerReceiveAfterSaleRequest.getOrderId(), CustomerErrorCodeEnum.ORDER_ID_IS_NULL);
        ParamCheckUtil.checkStringNonEmpty(customerReceiveAfterSaleRequest.getAfterSaleId(), CustomerErrorCodeEnum.AFTER_SALE_ID_IS_NULL);
        ParamCheckUtil.checkObjectNonNull(customerReceiveAfterSaleRequest.getAfterSaleRefundId(), CustomerErrorCodeEnum.AFTER_SALE_REFUND_ID_IS_NULL);
        ParamCheckUtil.checkObjectNonNull(customerReceiveAfterSaleRequest.getAfterSaleType(), CustomerErrorCodeEnum.AFTER_SALE_TYPE_IS_NULL);
        ParamCheckUtil.checkObjectNonNull(customerReceiveAfterSaleRequest.getReturnGoodAmount(), CustomerErrorCodeEnum.RETURN_GOOD_AMOUNT_IS_NULL);
        ParamCheckUtil.checkObjectNonNull(customerReceiveAfterSaleRequest.getApplyRefundAmount(), CustomerErrorCodeEnum.APPLY_REFUND_AMOUNT_IS_NULL);
    }
}
