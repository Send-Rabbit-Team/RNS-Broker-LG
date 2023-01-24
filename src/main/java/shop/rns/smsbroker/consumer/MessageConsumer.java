package shop.rns.smsbroker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import shop.rns.smsbroker.config.status.MessageStatus;
import shop.rns.smsbroker.dlx.DlxProcessingErrorHandler;
import shop.rns.smsbroker.dto.broker.ReceiveMessageDto;
import shop.rns.smsbroker.dto.message.MessageResultDto;
import shop.rns.smsbroker.dto.sms.SmsMessageDto;

import java.io.IOException;

import static shop.rns.smsbroker.utils.rabbitmq.RabbitUtil.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class MessageConsumer {
    private final DlxProcessingErrorHandler dlxProcessingErrorHandler;
    private final RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper;

    // RECEIVE
    @RabbitListener(queues = "q.sms.lg.work", concurrency = "3", ackMode = "MANUAL")
    public void receiveMessage(Message message, Channel channel) {
        try{
            ReceiveMessageDto receiveMessageDto = objectMapper.readValue(new String(message.getBody()), ReceiveMessageDto.class);

            SmsMessageDto messageDto = receiveMessageDto.getSmsMessageDto();
            System.out.println("메시지 내용: " + messageDto.getContent());

            MessageResultDto messageResultDto = receiveMessageDto.getMessageResultDto();
            dlxProcessingErrorHandler.handleErrorProcessingMessage(message, channel);

//            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
//
//            sendResponseToSendServer(messageResultDto);
        }catch (IOException e){
            log.warn("Error processing message:" + new String(message.getBody()) + ":" + e.getMessage());
            dlxProcessingErrorHandler.handleErrorProcessingMessage(message, channel);
        }
    }

    // RESPONSE TO SEND SERVER
    public void sendResponseToSendServer(final MessageResultDto messageResultDto){
        changeMassageStatusSuccess(messageResultDto);

        rabbitTemplate.convertAndSend(RECEIVE_EXCHANGE_NAME, LG_RECEIVE_ROUTING_KEY, messageResultDto);
    }

    public MessageResultDto changeMassageStatusSuccess(MessageResultDto messageResultDto){
        messageResultDto.setMessageStatus(MessageStatus.SUCCESS);
        return messageResultDto;
    }
}
