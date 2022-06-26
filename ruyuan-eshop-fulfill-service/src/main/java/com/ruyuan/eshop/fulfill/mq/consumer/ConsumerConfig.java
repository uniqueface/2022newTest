package com.ruyuan.eshop.fulfill.mq.consumer;

import com.ruyuan.eshop.fulfill.mq.config.RocketMQProperties;
import com.ruyuan.eshop.fulfill.mq.consumer.listener.CancelFulfillTopicListener;
import com.ruyuan.eshop.fulfill.mq.consumer.listener.TriggerOrderFulfillTopicListener;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.ruyuan.eshop.common.constants.RocketMqConstant.*;

/**
 * @author zhonghuashishan
 * @version 1.0
 */
@Configuration
public class ConsumerConfig {

    @Autowired
    private RocketMQProperties rocketMQProperties;


    /**
     * 触发订单履约消息消费者
     * @param triggerOrderFulfillTopicListener
     * @return
     * @throws MQClientException
     */
    @Bean("triggerOrderFulfillConsumer")
    public DefaultMQPushConsumer triggerOrderFulfillConsumer(TriggerOrderFulfillTopicListener triggerOrderFulfillTopicListener)
            throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(TRIGGER_ORDER_FULFILL_CONSUMER_GROUP);
        consumer.setNamesrvAddr(rocketMQProperties.getNameServer());
        consumer.subscribe(TRIGGER_ORDER_FULFILL_TOPIC, "*");
        consumer.registerMessageListener(triggerOrderFulfillTopicListener);
        consumer.start();
        return consumer;
    }


    /**
     * 取消履约消息消费者
     * @param cancelFulfillTopicListener
     * @return
     * @throws MQClientException
     */
    @Bean("cancelFulfillConsumer")
    public DefaultMQPushConsumer cancelFulfillConsumer(CancelFulfillTopicListener cancelFulfillTopicListener)
            throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(CANCEL_FULFILL_CONSUMER_GROUP);
        consumer.setNamesrvAddr(rocketMQProperties.getNameServer());
        consumer.subscribe(CANCEL_FULFILL_TOPIC, "*");
        consumer.registerMessageListener(cancelFulfillTopicListener);
        consumer.start();
        return consumer;
    }

}