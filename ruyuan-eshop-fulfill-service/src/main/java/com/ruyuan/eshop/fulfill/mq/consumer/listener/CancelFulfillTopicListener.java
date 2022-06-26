package com.ruyuan.eshop.fulfill.mq.consumer.listener;

import com.alibaba.fastjson.JSON;
import com.ruyuan.eshop.fulfill.domain.request.CancelFulfillRequest;
import com.ruyuan.eshop.fulfill.service.FulfillService;
import com.ruyuan.eshop.tms.api.TmsApi;
import com.ruyuan.eshop.wms.api.WmsApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 取消订单履约消息
 * @author zhonghuashishan
 * @version 1.0
 */
@Slf4j
@Component
public class CancelFulfillTopicListener implements MessageListenerConcurrently {

    @Autowired
    private FulfillService fulfillService;

    @DubboReference(version = "1.0.0", retries = 0)
    private WmsApi wmsApi;

    @DubboReference(version = "1.0.0", retries = 0)
    private TmsApi tmsApi;


    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        try {
            for(MessageExt messageExt : msgs) {
                String message = new String(messageExt.getBody());
                CancelFulfillRequest request =
                        JSON.parseObject(message, CancelFulfillRequest.class);

                //1、取消履约单
                fulfillService.cancelFulfillOrder(request.getOrderId());
                //2、取消捡货
                wmsApi.cancelPickGoods(request.getOrderId());
                //3、取消发货
                tmsApi.cancelSendOut(request.getOrderId());
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            log.error("消费取消订单履约消息", e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }
}
