package com.ruyuan.eshop.customer.mq.consumer;

import com.ruyuan.eshop.customer.mq.config.RocketMQProperties;
import com.ruyuan.eshop.customer.mq.consumer.listener.AfterSaleCustomerAuditTopicListener;
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
     * 客服接收售后申请消费者
     */
    @Bean("afterSaleCustomerAudit")
    public DefaultMQPushConsumer afterSaleCustomerAudit(AfterSaleCustomerAuditTopicListener afterSaleCustomerAuditTopicListener)
            throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(AFTER_SALE_CUSTOMER_AUDIT_GROUP);
        consumer.setNamesrvAddr(rocketMQProperties.getNameServer());
        consumer.subscribe(AFTER_SALE_CUSTOMER_AUDIT_TOPIC, "*");
        consumer.registerMessageListener(afterSaleCustomerAuditTopicListener);
        consumer.start();
        return consumer;
    }
}