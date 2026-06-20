package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.corporateaction.SymbolChange;
import org.springframework.stereotype.Service;

/**
 * Applies ticker symbol changes without altering economics.
 */
@Service
public class SymbolMappingService {

    public Position applySymbolChange(Position position, SymbolChange symbolChange) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        if (symbolChange == null) {
            throw new IllegalArgumentException("Symbol change cannot be null");
        }

        Position adjusted = new Position(position);
        adjusted.setSymbol(symbolChange.getNewSymbol());
        return adjusted;
    }
}
