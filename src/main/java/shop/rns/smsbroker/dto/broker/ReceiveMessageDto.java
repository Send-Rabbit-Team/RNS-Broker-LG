package shop.rns.smsbroker.dto.broker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import shop.rns.smsbroker.dto.message.MessageResultDto;
import shop.rns.smsbroker.dto.sms.SmsMessageDto;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ReceiveMessageDto {
    private SmsMessageDto smsMessageDto;

    private MessageResultDto messageResultDto;
}
