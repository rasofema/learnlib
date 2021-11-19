package de.learnlib.algorithms.continuous.util;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.Alphabet;

public class PhiMetric<I> {
    private final Alphabet<I> alphabet;
    private final double alpha;

    public PhiMetric(Alphabet<I> alphabet, double alpha) {
        this.alphabet = alphabet;
        this.alpha = alpha;
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

    private List<Double> getRow(CompactDFA<I> dfa, Integer q) {
        Integer initial = dfa.getInitialState();
        List<Integer> states = dfa.getStates().stream()
            .filter(s -> !s.equals(initial))
            .collect(Collectors.toList());
        states.add(initial);

        List<Double> row = states.stream()
            .map(s -> coefficient(dfa, q, s))
            .collect(Collectors.toList());
        row.add(dfa.isAccepting(q) ? (1 - alpha) : 0);
        return row;
    }

    private double langValue(CompactDFA<I> dfa) {
        Integer initial = dfa.getInitialState();
        List<Integer> states = dfa.getStates().stream()
            .filter(s -> !s.equals(initial))
            .collect(Collectors.toList());
        states.add(initial);

        List<List<Double>> matrix = states.stream()
            .map(q -> getRow(dfa, q))
            .collect(Collectors.toList());

        double[] solution = new GaussianElimination(matrix).primal();
        return solution[solution.length - 1];
    }

    /**
     *  The {@code GaussianElimination} data type provides methods
     *  to solve a linear system of equations <em>Ax</em> = <em>b</em>,
     *  where <em>A</em> is an <em>m</em>-by-<em>n</em> matrix
     *  and <em>b</em> is a length <em>n</em> vector.
     *  <p>
     *  This is a bare-bones implementation that uses Gaussian elimination
     *  with partial pivoting.
     *  See <a href = "https://algs4.cs.princeton.edu/99scientific/GaussianEliminationLite.java.html">GaussianEliminationLite.java</a>
     *  for a stripped-down version that assumes the matrix <em>A</em> is square
     *  and nonsingular.
     *  For an industrial-strength numerical linear algebra library,
     *  see <a href = "http://math.nist.gov/javanumerics/jama/">JAMA</a>.
     *  <p>
     *  This computes correct results if all arithmetic performed is
     *  without floating-point rounding error or arithmetic overflow.
     *  In practice, there will be floating-point rounding error;
     *  partial pivoting helps prevent accumulated floating-point rounding
     *  errors from growing out of control (though it does not
     *  provide any guarantees).
     *  <p>
     *  For additional documentation, see
     *  <a href="https://algs4.cs.princeton.edu/99scientific">Section 9.9</a>
     *  <i>Algorithms, 4th Edition</i> by Robert Sedgewick and Kevin Wayne.
     *
     *  @author Robert Sedgewick
     *  @author Kevin Wayne
     */
    public static class GaussianElimination {
        private static final double EPSILON = 1e-8;

        private final int m;      // number of rows
        private final int n;      // number of columns
        private List<List<Double>> a;     // m-by-(n+1) augmented matrix

        /**
         * Solves the linear system of equations <em>Ax</em> = <em>b</em>,
         * where <em>A</em> is an <em>m</em>-by-<em>n</em> matrix and <em>b</em>
         * is a length <em>m</em> vector.
         *
         * @param  A the <em>m</em>-by-<em>n</em> constraint matrix
         * @throws IllegalArgumentException if the dimensions disagree, i.e.,
         *         the length of {@code b} does not equal {@code m}
         */
        public GaussianElimination(List<List<Double>> A) {
            m = A.size();
            n = A.get(0).size() - 1;
            a = A;

            forwardElimination();
        }

        // forward elimination
        private void forwardElimination() {
            for (int p = 0; p < Math.min(m, n); p++) {

                // find pivot row using partial pivoting
                int max = p;
                for (int i = p+1; i < m; i++) {
                    if (Math.abs(a.get(i).get(p)) > Math.abs(a.get(max).get(p))) {
                        max = i;
                    }
                }

                // swap
                swap(p, max);

                // singular or nearly singular
                if (Math.abs(a.get(p).get(p)) <= EPSILON) {
                    continue;
                }

                // pivot
                pivot(p);
            }
        }

        // swap row1 and row2
        private void swap(int row1, int row2) {
            List<Double> temp = a.get(row1);
            a.set(row1, a.get(row2));
            a.set(row2, temp);
        }

        // pivot on a[p][p]
        private void pivot(int p) {
            for (int i = p+1; i < m; i++) {
                double alpha = a.get(i).get(p) / a.get(p).get(p);
                for (int j = p; j <= n; j++) {
                    a.get(i).set(j, a.get(i).get(j) - alpha * a.get(p).get(j));
                }
            }
        }

        /**
         * Returns a solution to the linear system of equations <em>Ax</em> = <em>b</em>.
         *
         * @return a solution <em>x</em> to the linear system of equations
         *         <em>Ax</em> = <em>b</em>; {@code null} if no such solution
         */
        public double[] primal() {
            // back substitution
            double[] x = new double[n];
            for (int i = Math.min(n-1, m-1); i >= 0; i--) {
                double sum = 0.0;
                for (int j = i+1; j < n; j++) {
                    sum += a.get(i).get(j) * x[j];
                }

                if (Math.abs(a.get(i).get(i)) > EPSILON)
                    x[i] = (a.get(i).get(n) - sum) / a.get(i).get(i);
                else if (Math.abs(a.get(i).get(n) - sum) > EPSILON)
                    return null;
            }

            // redundant rows
            for (int i = n; i < m; i++) {
                double sum = 0.0;
                for (int j = 0; j < n; j++) {
                    sum += a.get(i).get(j) * x[j];
                }
                if (Math.abs(a.get(i).get(n) - sum) > EPSILON)
                    return null;
            }
            return x;
        }
    }

/******************************************************************************
 *  Copyright 2002-2020, Robert Sedgewick and Kevin Wayne.
 *
 *  This file is part of algs4.jar, which accompanies the textbook
 *
 *      Algorithms, 4th edition by Robert Sedgewick and Kevin Wayne,
 *      Addison-Wesley Professional, 2011, ISBN 0-321-57351-X.
 *      http://algs4.cs.princeton.edu
 *
 *
 *  algs4.jar is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  algs4.jar is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with algs4.jar.  If not, see http://www.gnu.org/licenses.
 ******************************************************************************/
}
