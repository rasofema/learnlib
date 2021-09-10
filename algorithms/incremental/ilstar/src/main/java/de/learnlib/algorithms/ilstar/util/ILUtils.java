package de.learnlib.algorithms.ilstar.util;

import net.automatalib.automata.fsa.DFA;
import net.automatalib.automata.fsa.impl.compact.CompactDFA;
import net.automatalib.util.automata.fsa.DFAs;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class ILUtils {
    public static <S, I, T> /*double*/ void computeDistance(float x, DFA<S, I> aut1, DFA<S, I> aut2, Alphabet<I> alphabet) {
        CompactDFA<I> andAut = DFAs.and(aut1, aut2, alphabet);



    }

    private static <I> double alphaDistribution(float alpha, Word<I> word, Alphabet<I> alphabet) {
        return (1 - alpha) * Math.pow((alpha / alphabet.size()), word.size());
    }
}
