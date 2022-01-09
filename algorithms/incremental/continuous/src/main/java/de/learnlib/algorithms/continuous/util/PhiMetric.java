package de.learnlib.algorithms.continuous.util;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import Jama.Matrix;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.commons.util.Pair;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.Alphabet;

public class PhiMetric<I> {
    private final Alphabet<I> alphabet;
    private final double alpha;
    boolean isBinary;

    public PhiMetric(Alphabet<I> alphabet, double alpha, boolean isBinary) {
        this.alphabet = alphabet;
        this.alpha = alpha;
        this.isBinary = isBinary;
    }

    public double diff(CompactDFA<I> dfa1, CompactDFA<I> dfa2) {
        return langValue(DFAs.minimize(DFAs.xor(DFAs.minimize(dfa1), DFAs.minimize(dfa2), alphabet)));
    }

    public double sim(CompactDFA<I> dfa1, CompactDFA<I> dfa2) {
        return 1 - diff(dfa1, dfa2);
    }

    private double coefficient(CompactDFA<I> dfa, Integer q, Integer qPrime) {
        double coef = 0.0;
        for (I a : alphabet) {
            if (Objects.equals(dfa.getTransition(q, a), qPrime)) {
                coef -= alpha;
            }
        }
        coef = coef / alphabet.size();
        if (q.equals(qPrime)) {
            coef += 1;
        }
        return coef;
    }

    private Pair<double[], Double> getRow(CompactDFA<I> dfa, Integer q) {
        Integer initial = dfa.getInitialState();
        List<Integer> states = dfa.getStates().stream()
            .filter(s -> !s.equals(initial))
            .collect(Collectors.toList());
        states.add(initial);

        double[] finalRow = new double[states.size()];
        for (int i = 0; i < states.size(); i++) {
            finalRow[i] = coefficient(dfa, q, states.get(i));
        }
        return Pair.of(finalRow, dfa.isAccepting(q) ? (1 - alpha) : 0);
    }

    private Pair<double[][], double[][]> buildSystem(CompactDFA<I> dfa) {
        Integer initial = dfa.getInitialState();
        List<Integer> states = dfa.getStates().stream()
            .filter(s -> !s.equals(initial))
            .collect(Collectors.toList());
        states.add(initial);

        double[][] A = new double[states.size()][];
        double[][] B = new double[states.size()][];
        for (int i = 0; i < states.size(); i++) {
            Pair<double[], Double> row = getRow(dfa, states.get(i));
            A[i] = row.getFirst();
            B[i] = new double[]{row.getSecond()};
        }
        return Pair.of(A, B);

    }

    private double langValue(CompactDFA<I> dfa) {
        if (isBinary) {
            return DFAs.acceptsEmptyLanguage(dfa) ? 0 : 1;
        }
        Pair<double[][], double[][]> system = buildSystem(dfa);
        Matrix A = new Matrix(system.getFirst());
        Matrix B = new Matrix(system.getSecond());
        Matrix x = A.solve(B);
        return x.get(dfa.getStates().size() - 1,0);
    }
}
