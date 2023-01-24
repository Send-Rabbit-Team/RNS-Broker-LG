package shop.rns.smsbroker.dlx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import shop.rns.smsbroker.dto.broker.ReceiveMessageDto;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static shop.rns.smsbroker.utils.rabbitmq.RabbitUtil.*;

@Log4j2
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class DlxProcessingErrorHandler {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    private int maxBrokerRetryCount = 2;
    private int maxRetryCount = 4;

    public boolean handleErrorProcessingMessage(Message message, Channel channel){
        RabbitmqHeader rabbitmqHeader = new RabbitmqHeader(message.getMessageProperties().getHeaders());

        try{
            // 모든 중계사를 다 돌았을 경우
            if(rabbitmqHeader.getFailedRetryCount() >= maxRetryCount){
                // 해당 브로커의 Dead Queue로 보내기
                ReceiveMessageDto receiveMessageDto = objectMapper.readValue(new String(message.getBody()), ReceiveMessageDto.class);
                long brokerId = receiveMessageDto.getMessageResultDto().getBrokerId();
                String brokerName = (brokerId == 1)? "kt" :(brokerId == 2)? "skt": "lg";
                log.warn("[DEAD] Error at " + new Date() + "on retry " + rabbitmqHeader.getFailedRetryCount()
                        + " for message " + message);

                rabbitTemplate.convertAndSend(DEAD_EXCHANGE_NAME, "sms.dead." + brokerName, message);
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

            // 다른 중계사를 다 돌지 않았을 경우
            }else if(rabbitmqHeader.getFailedRetryCount() >= maxBrokerRetryCount){
                // KT 중계사 WAIT로 보내기
                log.warn("[RE-SEND OTHER BROKER] Error at " + new Date() + "on retry " + rabbitmqHeader.getFailedRetryCount()
                        + " for message " + message);

                rabbitTemplate.convertAndSend(DLX_EXCHANGE_NAME, KT_WAIT_ROUTING_KEY, message, m -> {
                    // x-death.count +1 증가
                    Map<String, Object> headers = m.getMessageProperties().getHeaders();
                    List<Map<String,Object>> xDeathHeaders = (List<Map<String, Object>>) headers.get("x-death");

                    int idx = 0;
                    for(Map<String, Object> x : xDeathHeaders){
                        Optional<Object> count = Optional.ofNullable(x.get("count"));
                        int finalIdx = idx;
                        count.ifPresent(c -> xDeathHeaders.get(finalIdx).put("x-death", count));

                        idx++;
                    }
                    m.getMessageProperties().getHeaders().put("x-death", xDeathHeaders);
                    return m;
                });

                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

//                channel.basicPublish(DLX_EXCHANGE_NAME, KT_WAIT_ROUTING_KEY, null, message.getBody());
//                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }
            // 자신의 WAIT QUEUE로 넣기
            else{
                log.debug("[RE-QUEUE] Error at " + new Date() + " on retry " + rabbitmqHeader.getFailedRetryCount()
                        + " for message " + message);

                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            }

            return true;
        } catch (IOException e) {
            log.warn("[HANDLER-FAILED] Error at " + new Date() + " on retry " + rabbitmqHeader.getFailedRetryCount()
                    + " for message " + message);
        }

        return false;
    }
}
