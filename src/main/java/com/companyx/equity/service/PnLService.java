package com.companyx.equity.service;

import com.companyx.equity.error.UnexpectedValueException;
import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.model.TransactionType;
import com.companyx.equity.model.User;
import com.companyx.equity.repository.FinhubRepository;
import com.companyx.equity.repository.TransactionRepository;
import com.companyx.equity.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PnLService {
    private final String CASH = "cash";
    private final int ROUNDING_SCALE = 6;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    FinhubRepository finhubRepository;

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
        Optional<User> user = userRepository.findByUid(uid);
        if(!user.isPresent())
            throw new RuntimeException("User not found: " + uid);

        Map<String, Position> positions = getStartPositions(user.get(), start);
        //clone positions to calculate realized later
        Gson gson = new Gson();
        String jsonString = gson.toJson(positions);
        Type type = new TypeToken<HashMap<String, Position>>(){}.getType();
        HashMap<String, Position> startPositions = gson.fromJson(jsonString, type);
        positions = getEndPositions(user.get(), start, end, positions);

        positions = calculateRealized(startPositions, positions);
        positions = calculateUnrealized(positions, end);
        log.info(new Timestamp(System.currentTimeMillis()) + " "
                + this.getClass() + ":"
                + new Throwable().getStackTrace()[0].getMethodName()
                + "\nFinal Position: " + positions
        );
        return positions;
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
        log.info(new Timestamp(System.currentTimeMillis()) + " "
                + this.getClass() + ":"
                + new Throwable().getStackTrace()[0].getMethodName()
                + "\n" + priorTrans.size() + " transactions"
                + "\n" + " from EPOCH to " + start
        );

        Map<String, Position> positions = new HashMap<>(); //(basis, quantity) tuple
        applyTransactions(user, positions, priorTrans);
        log.info(new Timestamp(System.currentTimeMillis()) + " "
                + this.getClass() + ":"
                + new Throwable().getStackTrace()[0].getMethodName()
                + "\nStart Position: " + positions
        );
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
        //get transactions in scope & calculate new basis, quantity, and cumulative realized
        List<Transaction> transactions = transactionRepository.findAllBetween(user.getId(), start, end);
        log.info(new Timestamp(System.currentTimeMillis()) + " "
                + this.getClass() + ":"
                + new Throwable().getStackTrace()[0].getMethodName()
                + "\n" + transactions.size() + " transactions"
                + "\n" + " from " + start + " to " + end
        );
        startPositions = applyTransactions(user, startPositions, transactions);
        return startPositions;
    }

    //TODO: refactor to simplify by virtue of symmetry
    private Map<String, Position> applyTransactions(User user, Map<String, Position> positions, List<Transaction> transactions)
            throws JsonProcessingException {
        for(Transaction transaction : transactions) {
            String sym = transaction.getSymbol();
            if(TransactionType.CASH_TRANS.contains(transaction.getTransactionType().getDescription()))
                sym = CASH;

            Position cashPos = positions.containsKey(CASH) ? positions.get(CASH) : new Position(user, transaction.getTimestamp(), CASH);
            Position startPos = positions.containsKey(sym) ? positions.get(sym) : new Position(user, transaction.getTimestamp(), sym);

            BigDecimal transPrice, transVal, startPrice, startVal, endVal, cash, realized;
            BigInteger startQuant, transQuant, endQuant;
            startVal = startPos.getValue();
            startQuant = startPos.getQuantity();
            startPrice = startQuant.equals(BigInteger.ZERO) ? BigDecimal.ZERO
                    : startVal.divide(new BigDecimal(startQuant), ROUNDING_SCALE, RoundingMode.HALF_UP).abs();
            cash = transaction.getValue();
            log.info(new Timestamp(System.currentTimeMillis()) + " "
                    + this.getClass() + ":"
                    + new Throwable().getStackTrace()[0].getMethodName()
                    + "\n### startVal: " + startVal
                    + "\n### startQuant: " + startQuant
                    + "\n### startPrice: " + startPrice
                    + "\n### cash: " + cash
            );

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
                    transPrice = transQuant.equals(BigInteger.ZERO) ? BigDecimal.ZERO
                            : transVal.divide(new BigDecimal(transQuant), ROUNDING_SCALE, RoundingMode.HALF_UP);
                    endQuant = startQuant.add(transQuant);

                    //long -> long
                    if((endQuant.compareTo(BigInteger.ZERO) > 0) && (startQuant.compareTo(BigInteger.ZERO) > 0)) {
                        endVal = startVal.subtract(transVal);
                        realized = BigDecimal.ZERO;
                    // short -> short
                    } else if((endQuant.compareTo(BigInteger.ZERO) < 0) && (startQuant.compareTo(BigInteger.ZERO) < 0)) {
                        endVal = startPrice.multiply(new BigDecimal(endQuant)).multiply(new BigDecimal(-1));
                        realized = new BigDecimal(transQuant).multiply(startPrice.subtract(transPrice));
                    //short -> long
                    } else {
                        endVal = transPrice.multiply(new BigDecimal(endQuant)).multiply(new BigDecimal(-1));
                        realized = startVal.add(new BigDecimal(startQuant).multiply(transPrice)); // basis - (startQ * transP)
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
                    transPrice = transQuant.equals(BigInteger.ZERO) ? BigDecimal.ZERO
                            : transVal.divide(new BigDecimal(transQuant), ROUNDING_SCALE, RoundingMode.HALF_UP);
                    endQuant = startQuant.subtract(transaction.getQuantity());

                    //long -> long
                    if((endQuant.compareTo(BigInteger.ZERO) > 0) && (startQuant.compareTo(BigInteger.ZERO) > 0)) {
                        endVal = startPrice.multiply(new BigDecimal(endQuant)).multiply(new BigDecimal(-1));
                        realized = new BigDecimal(transQuant).multiply(transPrice.subtract(startPrice));
                    //short -> short
                    } else if((endQuant.compareTo(BigInteger.ZERO) < 0) && (startQuant.compareTo(BigInteger.ZERO) < 0)) {
                        endVal = startVal.add(transVal);
                        realized = BigDecimal.ZERO;
                    //long -> short
                    } else {
                        endVal = transPrice.multiply(new BigDecimal(endQuant)).multiply(new BigDecimal(-1));
                        realized = startVal.add(new BigDecimal(startQuant).multiply(transPrice)); // (startQ * transP) - basis
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
            log.info(new Timestamp(System.currentTimeMillis()) + " "
                    + this.getClass() + ":"
                    + new Throwable().getStackTrace()[0].getMethodName()
                    + "\n### End transaction: " + transaction.toString()
                    + "\n### End positions: " + positions
            );
        }
        return positions;
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
     * Calculates unrealized
     *  unrealized = (end Price * quantity) - basis
     *
     * @param positions
     * @param end
     * @return sym -> Position
     * @throws JsonProcessingException
     */
    private Map<String, Position> calculateUnrealized(Map<String, Position> positions, Date end) throws JsonProcessingException {
        for(String sym : positions.keySet()) {
            if(sym.equals(CASH))
                continue;
            log.info(new Timestamp(System.currentTimeMillis()) + " "
                    + this.getClass() + ":"
                    + new Throwable().getStackTrace()[0].getMethodName()
                    + "\nCalculating unrealized for " +  sym
            );
            Date today = new Date();
            BigDecimal price;
            //TODO: add biz day logic for holidays and weekends - SwapMon
            if(end.compareTo(today) >= 0) {
                price = finhubRepository.getMark(sym).getCurrentPrice();
            } else {
                List<BigDecimal> prices = finhubRepository.getCandle(sym, end, end).getClose();
                if(prices.size() != 1)
                    throw new UnexpectedValueException(sym + " had " + prices.size() + " prices for " + end);
                price = prices.get(0);
            }
            BigDecimal basis = positions.get(sym).getValue();
            BigInteger quantity = positions.get(sym).getQuantity();
            //(end price * quantity) - basis
            BigDecimal unrealized = (price.multiply(new BigDecimal(quantity))).add(basis);
            Position position = positions.get(sym);
            position.setUnrealized(unrealized);
            position.setPrice(price);
            positions.put(sym, position);
        }
        return positions;
    }

    public Transaction getTransactionById(String uid, String id) {
        Optional<User> user = userRepository.findByUid(uid);
        if(!user.isPresent())
            throw new RuntimeException("User not found: " + uid);

        Integer transactionId = Integer.parseInt(id);
        return transactionRepository.findByUidAndId(user.get().getId(), transactionId).get();
    }

    public List<Transaction> getTransactionsByDates(String uid, Optional<String> from, Optional<String> to)
            throws ParseException {
        Optional<User> user = userRepository.findByUid(uid);
        if(!user.isPresent())
            throw new RuntimeException("User not found: " + uid);

        Date fromDate = null;
        Date toDate = null;
        if (from.isPresent()) {
            fromDate = new SimpleDateFormat("yyyy-MM-dd").parse(from.get());
        }
        if (to.isPresent()) {
            toDate = new SimpleDateFormat("yyyy-MM-dd").parse(to.get());
        }

        if (Objects.isNull(fromDate) && Objects.isNull(toDate))
            return transactionRepository.findAll();
        else if (Objects.isNull(fromDate))
            return transactionRepository.findAllBefore(user.get().getId(), toDate);
        else
            return transactionRepository.findAllBetween(user.get().getId(), fromDate, toDate);
    }
}
