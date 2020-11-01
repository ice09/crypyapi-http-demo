package tech.blockchainers.crypyapi.http.common.proxy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentDto {

    private String amount;
    private String serviceAddress;
    private String trxHash;
    private String trxId;
    private String trxIdHex;
    private String address;
    private String signedTrxId;

}