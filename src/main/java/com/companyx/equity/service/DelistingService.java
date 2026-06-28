package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Delisting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
        if (position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return new Position(position);
        }

        Position adjusted = new Position(position);
        BigDecimal qty = adjusted.getQuantity();
        BigDecimal basis = adjusted.getValue(); // negative for longs

        BigDecimal additionalRealized;
        if (delisting.isWorthless()) {
            // Worthless delisting: entire basis is a realized loss
            additionalRealized = basis;
            log.debug("Delisting {} worthless: realized loss={}", delisting.getSymbol(), basis);
        } else {
            // Cash-out delisting: proceeds offset the basis
            BigDecimal proceeds = delisting.getCashPerShare().multiply(qty.abs());
            additionalRealized = proceeds.add(basis); // basis is negative, so this is proceeds − |basis|
            log.debug("Delisting {} at ${}/share: proceeds={}, basis={}, realized={}",
                    delisting.getSymbol(), delisting.getCashPerShare(), proceeds, basis, additionalRealized);
        }

        adjusted.setRealized(adjusted.getRealized().add(additionalRealized).setScale(6, RoundingMode.HALF_UP));
        adjusted.setQuantity(BigDecimal.ZERO);
        adjusted.setValue(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        adjusted.setUnrealized(BigDecimal.ZERO);

        return adjusted;
    }
}
