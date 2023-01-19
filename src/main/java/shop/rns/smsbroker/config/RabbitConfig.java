package shop.rns.smsbroker.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static shop.rns.smsbroker.utils.rabbitmq.RabbitUtil.*;

@Configuration
public class RabbitConfig {
    @Bean
    public MessageConverter jsonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory){
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    // SMS QUEUE
    @Bean
    Queue smsWorkLGQueue(){
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE_NAME);
        args.put("x-dead-letter-routing-key", LG_WAIT_ROUTING_KEY);
        return new Queue(LG_WORK_QUEUE_NAME, true, false, false, args);
    }

    // SMS Exchange
    @Bean
    public DirectExchange smsExchange(){
        return new DirectExchange(SMS_EXCHANGE_NAME);
    }

    // SMS Binding
    @Bean
    public Binding bindingSmsLG(DirectExchange smsExchange, Queue smsWorkLGQueue){
        return BindingBuilder.bind(smsWorkLGQueue)
                .to(smsExchange)
                .with(LG_WORK_ROUTING_KEY);
    }

    // Send Server에게 응답 결과 전달하기 위한 큐
    @Bean
    Queue smsReceiveLGQueue(){
        Map<String, Object> args = new HashMap<>();
        return new Queue(LG_RECEIVE_QUEUE_NAME, true);
    }

    @Bean
    public DirectExchange smsReceiveExchange(){
        return new DirectExchange(RECEIVE_EXCHANGE_NAME);
    }

    @Bean
    public Binding bindingSmsReceiveLG(DirectExchange smsReceiveExchange, Queue smsReceiveLGQueue){
        return BindingBuilder.bind(smsReceiveLGQueue)
                .to(smsReceiveExchange)
                .with(LG_RECEIVE_ROUTING_KEY);
    }
    // DLX QUEUE
    @Bean
    public Queue smsWaitLGQueue(){
        return new Queue(LG_WAIT_QUEUE_NAME, true);
    }

    // DLX Exchange
    @Bean
    public DirectExchange dlxSMSExchange(){
        return new DirectExchange(DLX_EXCHANGE_NAME);
    }

    // DLX SMS Binding
    @Bean
    public Binding bindingDLXSmLG(DirectExchange dlxSMSExchange, Queue smsWaitLGQueue){
        return BindingBuilder.bind(smsWaitLGQueue)
                .to(dlxSMSExchange)
                .with(LG_WAIT_ROUTING_KEY);
    }
}
