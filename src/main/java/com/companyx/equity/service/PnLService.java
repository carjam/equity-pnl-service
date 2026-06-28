package com.companyx.equity.service;

import com.companyx.equity.error.InvalidInputException;
import com.companyx.equity.error.TransactionNotFoundException;
import com.companyx.equity.error.UnexpectedValueException;
import com.companyx.equity.error.UserNotFoundException;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.repository.TransactionRepository;
import com.companyx.equity.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class PnLService {
    private final String CASH = "cash";
    private final int ROUNDING_SCALE = 6;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final FinhubRepository finhubRepository;
    private final CorporateActionService corporateActionService;

    /**
     * Retrieves the realized, unrealized PnL, quantity, and basis for a date range
     *
     * A superior implementation would integrate w/ a messaging queue to ingest Transactions as they settle near-realtime
     * The endpoint would process each Transaction and persist a Position.
     * With that in place, getStartPositions() & getEndPositions() could be replaced by DB record lookup
     *    realized = End realized ITD - Start realized ITD
     *    unrealized = (Position Quantity * Price @ Date) -  Position basis
     *
     * Position endPos = start positions + buys - sales;
     *  long = negative value, positive quantity
     *  short = positive value, negative quantity
     */
    public Map<String, Position> getPositions(String uid, Date start, Date end) throws JsonProcessingException {
        validateDateRange(start, end);
        
        Optional<User> user = userRepository.findByUid(uid);
        if(!user.isPresent())
            throw new UserNotFoundException(uid);

        Map<String, Position> positions = getStartPositions(user.get(), start);
        HashMap<String, Position> startPositions = deepCopyPositions(positions);
        positions = getEndPositions(user.get(), start, end, positions);

        positions = calculateRealized(startPositions, positions);
        positions = applyCorporateActions(user.get(), positions, startPositions, start, end);
        positions = calculateUnrealized(positions, end);
        log.debug("Final Position: {}", positions);
        return positions;
    }
    
    /**
     * Deep copies a map of positions using copy constructor
     */
    private HashMap<String, Position> deepCopyPositions(Map<String, Position> positions) {
        HashMap<String, Position> copy = new HashMap<>();
        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            copy.put(entry.getKey(), new Position(entry.getValue()));
        }
        return copy;
    }
    
    /**
     * Validates that start date is before or equal to end date
     */
    private void validateDateRange(Date start, Date end) {
        if (start == null || end == null) {
            throw new InvalidInputException("Start and end dates cannot be null");
        }
        if (start.after(end)) {
            throw new InvalidInputException("Start date must be before or equal to end date");
        }
    }

    /**
     * Primes the starting positions from inception to provided start date
     *  realized = ITD
     *  unrealized = 0
     *
     * @param user
     * @param start
     * @return Map of symbol -> Positions
     * @throws JsonProcessingException
     */
    private Map<String, Position> getStartPositions(User user, Date start) throws JsonProcessingException {
        List<Transaction> priorTrans = transactionRepository.findAllBefore(user.getId(), start);
        log.debug("{} transactions from EPOCH to {}", priorTrans.size(), start);

        Map<String, Position> positions = new HashMap<>();
        applyTransactions(user, positions, priorTrans);
        log.debug("Start Position: {}", positions);
        return positions;
    }

    /**
     * Applies transactions within a date range to the starting positions
     *
     * @param user
     * @param start
     * @param end
     * @param startPositions
     * @return Map of symbol -> Positions
     * @throws JsonProcessingException
     */
    private Map<String, Position> getEndPositions(User user, Date start, Date end, Map<String, Position> startPositions)
            throws JsonProcessingException {
        List<Transaction> transactions = transactionRepository.findAllBetween(user.getId(), start, end);
        log.debug("{} transactions from {} to {}", transactions.size(), start, end);
        startPositions = applyTransactions(user, startPositions, transactions);
        return startPositions;
    }

    //TODO: refactor to simplify by virtue of symmetry
    private Map<String, Position> applyTransactions(User user, Map<String, Position> positions, List<Transaction> transactions)
            throws JsonProcessingException {
        for(Transaction transaction : transactions) {
            validateTransaction(transaction);
            
            String sym = transaction.getSymbol();
            if(TransactionType.CASH_TRANS.contains(transaction.getTransactionType().getDescription()))
                sym = CASH;

            Position cashPos = positions.containsKey(CASH) ? positions.get(CASH) : new Position(user, transaction.getTimestamp(), CASH);
            Position startPos = positions.containsKey(sym) ? positions.get(sym) : new Position(user, transaction.getTimestamp(), sym);

            BigDecimal transPrice, transVal, startPrice, startVal, endVal, cash, realized;
            BigDecimal startQuant, transQuant, endQuant;
            startVal = startPos.getValue();
            startQuant = startPos.getQuantity();
            startPrice = startQuant.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : startVal.divide(startQuant, ROUNDING_SCALE, RoundingMode.HALF_UP).abs();
            cash = transaction.getValue();
            log.debug("Processing transaction - startVal: {}, startQuant: {}, startPrice: {}, cash: {}",
                    startVal, startQuant, startPrice, cash);

            switch(transaction.getTransactionType().getDescription()) {
                case TransactionType.DEPOSIT:
                    if(!sym.equals(CASH))
                        throw new UnexpectedValueException(sym + " encountered when " + CASH + " expected.");
                    cashPos.setValue(cashPos.getValue().add(cash));
                    positions.put(CASH, cashPos);
                    break;
                case TransactionType.WITHDRAWAL:
                    if(!sym.equals(CASH))
                        throw new UnexpectedValueException(sym + " encountered when " + CASH + " expected.");
                    cashPos.setValue(cashPos.getValue().subtract(cash));
                    positions.put(CASH, cashPos);
                    break;
                case TransactionType.BUY:
                    //trans inputs always >= 0
                    transVal = transaction.getValue();
                    transQuant = transaction.getQuantity();
                    transPrice = transQuant.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                            : transVal.divide(transQuant, ROUNDING_SCALE, RoundingMode.HALF_UP);
                    endQuant = startQuant.add(transQuant);

                    //long -> long
                    if((endQuant.compareTo(BigDecimal.ZERO) > 0) && (startQuant.compareTo(BigDecimal.ZERO) > 0)) {
                        endVal = startVal.subtract(transVal);
                        realized = BigDecimal.ZERO;
                    // short -> short
                    } else if((endQuant.compareTo(BigDecimal.ZERO) < 0) && (startQuant.compareTo(BigDecimal.ZERO) < 0)) {
                        endVal = startPrice.multiply(endQuant).multiply(new BigDecimal(-1));
                        realized = transQuant.multiply(startPrice.subtract(transPrice));
                    //short -> long
                    } else {
                        endVal = transPrice.multiply(endQuant).multiply(new BigDecimal(-1));
                        realized = startVal.add(startQuant.multiply(transPrice)); // basis - (startQ * transP)
                    }

                    startPos.setValue(endVal);
                    startPos.setQuantity(endQuant);
                    startPos.setRealized(startPos.getRealized().add(realized)); //ITD cumulative
                    positions.put(sym, startPos);

                    cashPos.setValue(cashPos.getValue().subtract(cash));
                    positions.put(CASH, cashPos);
                    break;
                case TransactionType.SALE:
                    //trans inputs always >= 0
                    transVal = transaction.getValue();
                    transQuant = transaction.getQuantity();
                    transPrice = transQuant.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                            : transVal.divide(transQuant, ROUNDING_SCALE, RoundingMode.HALF_UP);
                    endQuant = startQuant.subtract(transaction.getQuantity());

                    //long -> long
                    if((endQuant.compareTo(BigDecimal.ZERO) > 0) && (startQuant.compareTo(BigDecimal.ZERO) > 0)) {
                        endVal = startPrice.multiply(endQuant).multiply(new BigDecimal(-1));
                        realized = transQuant.multiply(transPrice.subtract(startPrice));
                    //short -> short
                    } else if((endQuant.compareTo(BigDecimal.ZERO) < 0) && (startQuant.compareTo(BigDecimal.ZERO) < 0)) {
                        endVal = startVal.add(transVal);
                        realized = BigDecimal.ZERO;
                    //long -> short
                    } else {
                        endVal = transPrice.multiply(endQuant).multiply(new BigDecimal(-1));
                        realized = startVal.add(startQuant.multiply(transPrice)); // (startQ * transP) - basis
                    }

                    startPos.setValue(endVal);
                    startPos.setQuantity(endQuant);
                    startPos.setRealized(startPos.getRealized().add(realized)); //ITD cumulative
                    positions.put(sym, startPos);

                    cashPos.setValue(cashPos.getValue().add(cash));
                    positions.put(CASH, cashPos);
                    break;
                default:
                    throw new UnexpectedValueException("Unknown transaction type " + transaction.getTransactionType().getDescription());
            }
            log.debug("End transaction: {}, End positions: {}", transaction, positions);
        }
        return positions;
    }
    
    /**
     * Validates transaction has valid quantities and values
     */
    private void validateTransaction(Transaction transaction) {
        if (transaction.getQuantity() != null && transaction.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException("Transaction quantity cannot be negative");
        }
        if (transaction.getValue() != null && transaction.getValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException("Transaction value cannot be negative");
        }
    }

    /**
     * Replaces the ITD realized PnL with the date range value
     *  realized = end ITD - start ITD = cumulative realized over period start to end
     *
     * @param start
     * @param end
     * @return sym -> Position
     */
    private Map<String, Position> calculateRealized(Map<String, Position> start, Map<String, Position> end) {
        for(String sym : end.keySet()) {
            if(!start.containsKey(sym) || sym.equals(CASH))
                continue; // all transactions for this security were within period
            Position position = end.get(sym);
            BigDecimal realized = position.getRealized().subtract(start.get(sym).getRealized());
            position.setRealized(realized);
            end.put(sym, position);
        }
        return end;
    }

    /**
     * Applies corporate actions to equity positions before unrealized P&L is calculated.
     * Splits and stock dividends use full position history through the period end date.
     * Cash dividend income is limited to the requested P&L date range.
     */
    private Map<String, Position> applyCorporateActions(User user, Map<String, Position> positions,
                                                         Map<String, Position> startPositions,
                                                         Date start, Date end) {
        LocalDate periodStart = toLocalDate(start);
        LocalDate periodEnd = toLocalDate(end);

        for (String symbol : new ArrayList<>(positions.keySet())) {
            if (CASH.equals(symbol)) {
                continue;
            }

            Position position = positions.get(symbol);
            if (position == null || position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            LocalDate historyStart = getEarliestTransactionDate(user.getId(), symbol);
            Position adjusted = corporateActionService.applyPositionAdjustments(
                    position, symbol, historyStart, periodEnd);

            CorporateActionService.ComplexAdjustmentResult complex =
                    corporateActionService.applyComplexAdjustments(
                            adjusted, symbol, historyStart, periodEnd, this::lookupHistoricalPrice);

            adjusted = complex.getPosition();
            adjusted.setRealized(adjusted.getRealized().add(complex.getAdditionalRealized()));

            // Dividend income is quantity-timeline-aware: use start-of-period shares and
            // replay period transactions so each ex-date gets the correct holder quantity.
            BigDecimal startQty = Optional.ofNullable(startPositions.get(symbol))
                    .map(Position::getQuantity).orElse(BigDecimal.ZERO);
            List<Transaction> periodTx = transactionRepository.findAllByUserAndSymbol(
                    user.getId(), symbol, start, end);
            BigDecimal dividendIncome = corporateActionService.calculateDividendIncome(
                    startQty, periodTx, symbol, periodStart, periodEnd);

            adjusted.setRealized(adjusted.getRealized().add(dividendIncome));

            if (!adjusted.getSymbol().equals(symbol)) {
                positions.remove(symbol);
            }
            positions.put(adjusted.getSymbol(), adjusted);
            complex.getAdditionalPositions().forEach(positions::put);
        }

        return positions;
    }

    private BigDecimal lookupHistoricalPrice(String symbol, LocalDate date) {
        try {
            Date sqlDate = java.sql.Date.valueOf(date);
            List<BigDecimal> prices = finhubRepository.getCandle(symbol, sqlDate, sqlDate).getClose();
            if (prices == null || prices.size() != 1) {
                throw new UnexpectedValueException(symbol + " had " + (prices == null ? 0 : prices.size())
                        + " prices for " + date);
            }
            return prices.get(0);
        } catch (JsonProcessingException e) {
            throw new UnexpectedValueException("Failed to fetch price for " + symbol + " on " + date);
        }
    }

    private LocalDate getEarliestTransactionDate(Long userId, String symbol) {
        return transactionRepository.findEarliestByUserAndSymbol(userId, symbol)
                .map(this::toLocalDate)
                .orElse(LocalDate.of(1970, 1, 1));
    }

    private LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Calculates unrealized
     *  unrealized = (end Price * quantity) - basis
     *
     * @param positions
     * @param end
     * @return sym -> Position
     * @throws JsonProcessingException
     */
    private Map<String, Position> calculateUnrealized(Map<String, Position> positions, Date end) throws JsonProcessingException {
        LocalDate endDate = toLocalDate(end);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        for(String sym : positions.keySet()) {
            if(sym.equals(CASH))
                continue;
            log.debug("Calculating unrealized for {}", sym);
            Position position = positions.get(sym);
            BigDecimal quantity = position.getQuantity();
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                position.setUnrealized(BigDecimal.ZERO);
                positions.put(sym, position);
                continue;
            }

            BigDecimal price;
            if(!endDate.isBefore(today)) {
                price = finhubRepository.getMark(sym).getCurrentPrice();
            } else {
                List<BigDecimal> prices = finhubRepository.getCandle(sym, end, end).getClose();
                if(prices.size() != 1)
                    throw new UnexpectedValueException(sym + " had " + prices.size() + " prices for " + end);
                price = prices.get(0);
            }
            BigDecimal basis = position.getValue();
            BigDecimal unrealized = price.multiply(quantity).add(basis);
            position.setUnrealized(unrealized);
            position.setPrice(price);
            positions.put(sym, position);
        }
        return positions;
    }

    public Transaction getTransactionById(String uid, String id) {
        Optional<User> user = userRepository.findByUid(uid);
        if(!user.isPresent())
            throw new UserNotFoundException(uid);

        Long transactionId = Long.parseLong(id);
        return transactionRepository.findByUidAndId(user.get().getId(), transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(uid, transactionId));
    }

    public List<Transaction> getTransactionsByDates(String uid, Optional<String> from, Optional<String> to)
            throws ParseException {
        Optional<User> user = userRepository.findByUid(uid);
        if(!user.isPresent())
            throw new UserNotFoundException(uid);

        Date fromDate = null;
        Date toDate = null;
        
        try {
            if (from.isPresent()) {
                LocalDate localDate = LocalDate.parse(from.get(), DATE_FORMATTER);
                fromDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            }
            if (to.isPresent()) {
                LocalDate localDate = LocalDate.parse(to.get(), DATE_FORMATTER);
                toDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            }
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Invalid date format. Expected yyyy-MM-dd");
        }
        
        if (fromDate != null && toDate != null && fromDate.after(toDate)) {
            throw new InvalidInputException("From date must be before or equal to to date");
        }

        if (Objects.isNull(fromDate) && Objects.isNull(toDate))
            return transactionRepository.findAllByUser(user.get().getId());
        else if (Objects.isNull(fromDate))
            return transactionRepository.findAllBefore(user.get().getId(), toDate);
        else
            return transactionRepository.findAllBetween(user.get().getId(), fromDate, toDate);
    }

    /**
     * Returns all transactions for the authenticated user and a specific symbol,
     * for the entire history (inception-to-date). Used by tax-lot analysis so that
     * FIFO matching starts from the first purchase, not just the query window.
     */
    public List<Transaction> getAllTransactionsBySymbol(String uid, String symbol) {
        User user = userRepository.findByUid(uid).orElseThrow(() -> new UserNotFoundException(uid));
        return transactionRepository.findAllByUser(user.getId()).stream()
                .filter(t -> symbol.equalsIgnoreCase(t.getSymbol()))
                .sorted(Comparator.comparing(Transaction::getTimestamp))
                .toList();
    }
}
