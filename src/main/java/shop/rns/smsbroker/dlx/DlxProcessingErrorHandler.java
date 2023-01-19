package shop.rns.smsbroker.dlx;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.Date;

import static shop.rns.smsbroker.utils.rabbitmq.RabbitUtil.*;

@Log4j2
@RequiredArgsConstructor
public class DlxProcessingErrorHandler {
    private RabbitTemplate rabbitTemplate;

    private int maxBrokerRetryCount = 2;
    private int maxRetryCount = 4;

    public DlxProcessingErrorHandler(int maxBrokerRetryCount, int maxRetryCount){
        this.maxBrokerRetryCount = maxBrokerRetryCount;
        this.maxRetryCount = maxRetryCount;
    }

    public boolean handleErrorProcessingMessage(Message message, Channel channel){
        RabbitmqHeader rabbitmqHeader = new RabbitmqHeader(message.getMessageProperties().getHeaders());

        try{
            // 모든 중계사를 다 돌았을 경우
            if(rabbitmqHeader.getFailedRetryCount() >= maxRetryCount){
                // 자신의 Dead Queue로 보내기
                log.warn("[DEAD] Error at " + new Date() + "on retry " + rabbitmqHeader.getFailedRetryCount()
                + " for message " + message);

                channel.basicPublish(DEAD_EXCHANGE_NAME, message.getMessageProperties().getReceivedRoutingKey(), null, message.getBody());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

            // 다른 중계사를 다 돌지 않았을 경우
            }else if(rabbitmqHeader.getFailedRetryCount() > maxBrokerRetryCount){
                // KT 중계사 WAIT로 보내기
                log.warn("[RE-SEND KT BROKER] Error at " + new Date() + "on retry " + rabbitmqHeader.getFailedRetryCount()
                        + " for message " + message);

                channel.basicPublish(DLX_EXCHANGE_NAME, KT_WAIT_ROUTING_KEY, null, message.getBody());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }
            // 자신의 WAIT QUEUE로 넣기
            else{
                log.debug("[DLX] Error at " + new Date() + " on retry " + rabbitmqHeader.getFailedRetryCount()
                        + " for message " + message);

                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            }

            return true;
        } catch (IOException e) {
            log.warn("[HANDLER-FAILED] Error at " + new Date() + " on retry " + rabbitmqHeader.getFailedRetryCount()
                    + " for message " + message);
        }

        return false;
    }
}
