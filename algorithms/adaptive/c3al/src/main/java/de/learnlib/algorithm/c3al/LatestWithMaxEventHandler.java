package de.learnlib.algorithm.c3al;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.query.Query;
import net.automatalib.automaton.concept.Output;

/*
 * The EventHandler defines the termination criteria, and hypothesis selection criteria for C3AL.
 * It is provided with every hypothesis generated by C3AL (through hypEvent) and every query posed by C3Al
 * (through queryEvent). If either of these functions return a non-null hypothesis, C3AL will terminate,
 * accepting it as the final hypothesis to be returned.
 */
public class LatestWithMaxEventHandler<M extends Output<I, D>, I, D> implements EventHandler<M, I, D> {

    Integer maxQueries;
    Integer queryCount = 0;
    M latestHyp;

    public LatestWithMaxEventHandler(Integer maxQueries) {
        this.maxQueries = maxQueries;
    }

    @Nullable
    public M hypEvent(@Nullable M hyp) {
        if (hyp != null) {
            latestHyp = hyp;
        }
        return null;
    }

    @Nullable
    public M queryEvent(Query<I, D> query) {
        queryCount += 1;
        if (queryCount >= maxQueries) {
            return latestHyp;
        }
        return null;
    }
}