package de.learnlib.oracle.membership;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.Query;
import net.automatalib.word.Word;

public class ProbabilisticOracle<I, O> implements MembershipOracle<I, Word<O>> {
    private final MembershipOracle<I, Word<O>> oracle;
    private final int minimumAttempts, maximumAttempts;
    private final double minimumFraction;

    public ProbabilisticOracle(MembershipOracle<I, Word<O>> oracle, int minimumAttempts,
            double minimumFraction, int maximumAttempts) {
        this.oracle = oracle;
        if (minimumAttempts > maximumAttempts) {
            throw new RuntimeException("minimum number of attempts should not be greater than maximum");
        }
        if (minimumFraction > 1 || minimumFraction < 0.5) {
            throw new RuntimeException("Minimum fraction should be in interval [0.5, 1]");
        }
        this.minimumAttempts = minimumAttempts;
        this.minimumFraction = minimumFraction;
        this.maximumAttempts = maximumAttempts;
    }

    @Override
    public Word<O> answerQuery(Word<I> inputWord) {
        Counter<List<O>> responseCounter = new Counter<>();
        boolean finished;
        do {
            List<O> output = this.oracle.answerQuery(inputWord).asList();
            responseCounter.count(output);
            finished = responseCounter.getTotalNumber() >= this.minimumAttempts
                    && (responseCounter.getHighestFrequencyFraction() >= this.minimumFraction
                            || responseCounter.getObjectsCounted() == 1);
        } while (!finished && (responseCounter.getTotalNumber() < this.maximumAttempts));

        List<O> mostFrequent = responseCounter.getMostFrequent();
        return Word.fromList(mostFrequent);
    }

    @Override
    public Word<O> answerQuery(Word<I> prefix, Word<I> suffix) {
        return answerQuery(prefix.concat(suffix)).suffix(suffix.length());
    }

    @Override
    public void processQuery(Query<I, Word<O>> query) {
        query.answer(answerQuery(query.getPrefix(), query.getSuffix()));
    }

    @Override
    public void processQueries(Collection<? extends Query<I, Word<O>>> collection) {
        for (Query<I, Word<O>> query : collection) {
            processQuery(query);
        }
    }

    public class Counter<T> {
        private final Map<T, Integer> map;
        private int highestFrequency;
        private T mostFrequent;
        private int totalNumber;

        public Counter() {
            this.map = new HashMap<>();
            reset();
        }

        public void count(T obj) {
            Integer currentFrequency = map.get(obj);
            if (currentFrequency == null) {
                currentFrequency = 0;
            }
            int newFrequency = currentFrequency + 1;
            map.put(obj, newFrequency);
            if (newFrequency > highestFrequency) {
                highestFrequency = newFrequency;
                this.mostFrequent = obj;
            }
            totalNumber++;
        }

        public T getMostFrequent() {
            return this.mostFrequent;
        }

        public double getHighestFrequencyFraction() {
            if (totalNumber == 0) {
                throw new RuntimeException("Cannot calculate highest frequency without adding any objects");
            }
            return ((double) this.highestFrequency) / this.totalNumber;
        }

        public void reset() {
            this.map.clear();
            this.totalNumber = 0;
            this.highestFrequency = 0;
            this.mostFrequent = null;
        }

        public int getTotalNumber() {
            return this.totalNumber;
        }

        @Override
        public String toString() {
            List<Entry<T, Integer>> entries = new ArrayList<>(this.map.entrySet());
            entries.sort((arg0, arg1) -> -Integer.compare(arg0.getValue(), arg1.getValue()));
            StringBuilder sb = new StringBuilder();
            for (Entry<T, Integer> entry : entries) {
                sb.append(entry.getValue()).append(": ").append(entry.getKey()).append("\n");
            }
            return sb.toString();
        }

        public int getObjectsCounted() {
            return this.map.size();
        }
    }
}