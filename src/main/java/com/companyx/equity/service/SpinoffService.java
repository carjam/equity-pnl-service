package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.Spinoff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Applies spinoff events and allocates cost basis between parent and spun-off positions.
 */
@Slf4j
@Service
public class SpinoffService {

    private static final int SCALE = 6;

    public record SpinoffResult(Position parentPosition, Position spinoffPosition) {
    }

    public SpinoffResult applySpinoff(
            Position parentPosition,
            Spinoff spinoff,
            BigDecimal parentPrice,
            BigDecimal spunoffPrice
    ) {
        if (parentPosition == null) {
            throw new IllegalArgumentException("Parent position cannot be null");
        }
        if (spinoff == null) {
            throw new IllegalArgumentException("Spinoff cannot be null");
        }
        if (parentPrice == null || spunoffPrice == null) {
            throw new IllegalArgumentException("Market prices are required for spinoff basis allocation");
        }

        var parentQty = parentPosition.getQuantity();
        var spunoffQty = spinoff.spunoffQuantity(parentQty);
        var totalBasis = parentPosition.getValue();

        var parentMarketValue = parentPrice.multiply(new BigDecimal(parentQty.abs()));
        var spunoffMarketValue = spunoffPrice.multiply(new BigDecimal(spunoffQty.abs()));
        var totalMarketValue = parentMarketValue.add(spunoffMarketValue);

        var parentBasis = totalMarketValue.compareTo(BigDecimal.ZERO) == 0
                ? totalBasis
                : totalBasis.multiply(parentMarketValue.divide(totalMarketValue, SCALE, RoundingMode.HALF_UP));
        var spunoffBasis = totalBasis.subtract(parentBasis);

        Position adjustedParent = copy(parentPosition);
        adjustedParent.setValue(parentBasis.setScale(SCALE, RoundingMode.HALF_UP));

        Position spinoffPosition = copy(parentPosition);
        spinoffPosition.setSymbol(spinoff.getSpunoffSymbol());
        spinoffPosition.setQuantity(spunoffQty);
        spinoffPosition.setValue(spunoffBasis.setScale(SCALE, RoundingMode.HALF_UP));
        spinoffPosition.setUnrealized(BigDecimal.ZERO);
        spinoffPosition.setRealized(BigDecimal.ZERO);

        log.debug("Spinoff {} -> {}: parentBasis={}, spinoffBasis={}",
                spinoff.getSymbol(), spinoff.getSpunoffSymbol(), parentBasis, spunoffBasis);

        return new SpinoffResult(adjustedParent, spinoffPosition);
    }

    private Position copy(Position original) {
        return new Position(original);
    }
}
