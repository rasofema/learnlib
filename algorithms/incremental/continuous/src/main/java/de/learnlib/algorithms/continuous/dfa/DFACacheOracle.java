package de.learnlib.algorithms.continuous.dfa;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.Query;
import net.automatalib.words.Word;

public class DFACacheOracle<I> implements MembershipOracle.DFAMembershipOracle<I> {
    private final MembershipOracle.DFAMembershipOracle<I> oracle;
    private final Map<Word<I>, Boolean> cache = new HashMap<>();

    public DFACacheOracle(MembershipOracle.DFAMembershipOracle<I> delegate) {
        this.oracle = delegate;
    }

    @Override
    public Boolean answerQuery(Word<I> input) {
        return cache.computeIfAbsent(input, oracle::answerQuery);
    }

    @Override
    public Boolean answerQuery(Word<I> prefix, Word<I> suffix) {
        return answerQuery(prefix.concat(suffix));
    }

    @Override
    public void processQuery(Query<I, Boolean> query) {
        query.answer(answerQuery(query.getInput()));
    }

    @Override
    public void processQueries(Collection<? extends Query<I, Boolean>> queries) {
        for (Query<I, Boolean> query : queries) {
            processQuery(query);
        }
    }

    @Override
    public MembershipOracle<I, Boolean> asOracle() {
        return this;
    }

    @Override
    public void processBatch(Collection<? extends Query<I, Boolean>> batch) {
        processQueries(batch);
    }
}
