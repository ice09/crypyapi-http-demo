package tech.blockchainers.crypyapi.http.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tech.blockchainers.crypyapi.http.common.rest.ServiceControllerProxy;

import javax.annotation.security.RolesAllowed;
import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
@Slf4j
/**
 * Role verification Aspect to avoid dependency on Spring Security.
 * Can handle external (production, Keycloak) and internal (dev-Profile) User Principals.
 */
public class PaymentCorrelationAspect {

    @Around("@annotation(tech.blockchainers.crypyapi.http.common.annotation.Payable)")
    public Object validateMethodCall(final ProceedingJoinPoint joinPoint) throws Throwable {
        String signedTrxId = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest().getHeader("CPA-Signed-Identifier");
        String trxHash = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest().getHeader("CPA-Transaction-Hash");

        ServiceControllerProxy serviceControllerProxy = (ServiceControllerProxy) joinPoint.getTarget();
        boolean serviceCallAllowed = serviceControllerProxy.isServiceCallAllowed(trxHash, signedTrxId);
        if (serviceCallAllowed) {
            return joinPoint.proceed();
        }
        throw new SecurityException("Payment required.");
    }

}
