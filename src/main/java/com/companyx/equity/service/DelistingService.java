package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Delisting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Closes positions when a security is delisted.
 */
@Slf4j
@Service
public class DelistingService {

    public Position applyDelisting(Position position, Delisting delisting) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        if (delisting == null) {
            throw new IllegalArgumentException("Delisting cannot be null");
        }
        if (position.getQuantity().equals(BigInteger.ZERO)) {
            return new Position(position);
        }

        Position adjusted = new Position(position);
        BigDecimal additionalRealized = adjusted.getValue();
        adjusted.setRealized(adjusted.getRealized().add(additionalRealized).setScale(6, RoundingMode.HALF_UP));
        adjusted.setQuantity(BigInteger.ZERO);
        adjusted.setValue(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        adjusted.setUnrealized(BigDecimal.ZERO);

        log.debug("Delisting {}: realized loss={}", delisting.getSymbol(), additionalRealized);
        return adjusted;
    }
}
