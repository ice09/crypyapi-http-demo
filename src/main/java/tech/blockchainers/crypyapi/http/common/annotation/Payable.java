package tech.blockchainers.crypyapi.http.common.annotation;

import tech.blockchainers.crypyapi.http.common.annotation.enums.Currency;
import tech.blockchainers.crypyapi.http.common.annotation.enums.StableCoin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Payable {

    Currency currency();
    int equivalentValueInWei();
    StableCoin[] accepted();
    String service();

}
