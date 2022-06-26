package com.ruyuan.eshop.market.api.impl;

import com.ruyuan.eshop.common.constants.RedisLockKeyConstants;
import com.ruyuan.eshop.common.core.JsonResult;
import com.ruyuan.eshop.common.redis.RedisLock;
import com.ruyuan.eshop.market.api.MarketApi;
import com.ruyuan.eshop.market.dao.CouponDAO;
import com.ruyuan.eshop.market.domain.dto.CalculateOrderAmountDTO;
import com.ruyuan.eshop.market.domain.dto.UserCouponDTO;
import com.ruyuan.eshop.market.domain.entity.CouponDO;
import com.ruyuan.eshop.market.domain.query.UserCouponQuery;
import com.ruyuan.eshop.market.domain.request.CalculateOrderAmountRequest;
import com.ruyuan.eshop.market.domain.request.LockUserCouponRequest;
import com.ruyuan.eshop.market.domain.request.ReleaseUserCouponRequest;
import com.ruyuan.eshop.market.enums.CouponUsedStatusEnum;
import com.ruyuan.eshop.market.exception.MarketBizException;
import com.ruyuan.eshop.market.exception.MarketErrorCodeEnum;
import com.ruyuan.eshop.market.service.CouponService;
import com.ruyuan.eshop.market.service.MarketService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author zhonghuashishan
 * @version 1.0
 */
@Slf4j
@DubboService(version = "1.0.0", interfaceClass = MarketApi.class, retries = 0)
public class MarketApiImpl implements MarketApi {

    @Autowired
    private CouponService couponService;

    @Autowired
    private MarketService marketService;

    @Autowired
    private RedisLock redisLock;
    @Autowired
    private CouponDAO couponDAO;

    @Override
    public JsonResult<UserCouponDTO> getUserCoupon(UserCouponQuery userCouponQuery) {
        try {
            UserCouponDTO userCouponDTO = couponService.getUserCoupon(userCouponQuery);
            return JsonResult.buildSuccess(userCouponDTO);
        } catch (MarketBizException e) {
            log.error("biz error", e);
            return JsonResult.buildError(e.getErrorCode(), e.getErrorMsg());
        } catch (Exception e) {
            log.error("system error", e);
            return JsonResult.buildError(e.getMessage());
        }
    }

    @Override
    public JsonResult<Boolean> lockUserCoupon(LockUserCouponRequest lockUserCouponRequest) {
        try {
            Boolean result = couponService.lockUserCoupon(lockUserCouponRequest);
            return JsonResult.buildSuccess(result);
        } catch (MarketBizException e) {
            log.error("biz error", e);
            return JsonResult.buildError(e.getErrorCode(), e.getErrorMsg());
        } catch (Exception e) {
            log.error("system error", e);
            return JsonResult.buildError(e.getMessage());
        }
    }


    /**
     * 回退用户使用的优惠券
     */
    @Override
    public JsonResult<Boolean> releaseUserCoupon(ReleaseUserCouponRequest releaseUserCouponRequest) {
        log.info("开始执行回滚优惠券,couponId:{}", releaseUserCouponRequest.getCouponId());
        // 企业级接口的开发
        // 先进行完善的参数校验 -> 如果请求参数校验可以通过再往下走
        // checkReleaseUserCouponRequest(releaseUserCouponRequest) -> 校验

        //  针对一个细粒度的数据粒度，去加分布式锁
        String couponId = releaseUserCouponRequest.getCouponId();
        String key = RedisLockKeyConstants.RELEASE_COUPON_KEY + couponId;
        boolean lock = redisLock.lock(key);
        if (!lock) {
            throw new MarketBizException(MarketErrorCodeEnum.RELEASE_COUPON_FAILED);
        }

        // 加锁到底是为什么？是用来防止并发问题？还是说防止重复调用有一个幂等性的保障？
        // 幂等性保障，两个步骤一起来实现，分布式锁加锁 + 进行是否对同一个数据重复执行这个操作（幂等检查）
        // bad taste = 我根本没看到幂等检查在哪里
        // 进行幂等性检查
        String userId = releaseUserCouponRequest.getUserId();
        CouponDO couponAchieve = couponDAO.getUserCoupon(userId, couponId);
        if (CouponUsedStatusEnum.UN_USED.getCode().equals(couponAchieve.getUsed())) {
            log.info("当前用户未使用优惠券,不用回退,userId:{},couponId:{}", userId, couponId);
            return JsonResult.buildSuccess(true);
        }


        // 才是去执行具体的业务操作，企业级接口正规的操作流程
        try {
            //  执行释放优惠券
            Boolean result = couponService.releaseUserCoupon(releaseUserCouponRequest);
            return JsonResult.buildSuccess(result);
        } catch (MarketBizException e) {
            log.error("biz error", e);
            return JsonResult.buildError(e.getErrorCode(), e.getErrorMsg());
        } catch (Exception e) {
            log.error("system error", e);
            return JsonResult.buildError(e.getMessage());
        } finally {
            // 释放分布式锁
            redisLock.unlock(key);
        }
    }

    /**
     * 计算订单费用
     *
     * @param calculateOrderAmountRequest
     * @return
     */
    @Override
    public JsonResult<CalculateOrderAmountDTO> calculateOrderAmount(CalculateOrderAmountRequest calculateOrderAmountRequest) {
        try {
            CalculateOrderAmountDTO calculateOrderAmountDTO = marketService.calculateOrderAmount(calculateOrderAmountRequest);
            return JsonResult.buildSuccess(calculateOrderAmountDTO);
        } catch (MarketBizException e) {
            log.error("biz error", e);
            return JsonResult.buildError(e.getErrorCode(), e.getErrorMsg());
        } catch (Exception e) {
            log.error("system error", e);
            return JsonResult.buildError(e.getMessage());
        }
    }

}