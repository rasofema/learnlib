package de.learnlib.algorithms.ikv.mealy;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.acex.AcexAnalyzer;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealy;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealyState;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class IKearnsVaziraniMealy<I, O> extends KearnsVaziraniMealy<I, O> {

    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet
     * @param oracle
     *         the membership oracle
     */
    @GenerateBuilder
    public IKearnsVaziraniMealy(Alphabet<I> alphabet,
                              MembershipOracle<I, Word<O>> oracle,
                              AcexAnalyzer counterexampleAnalyzer,
                              KearnsVaziraniMealyState<I, O> startingState) {
        super(alphabet, oracle, true, counterexampleAnalyzer);
        super.discriminationTree = startingState.getDiscriminationTree();
        super.discriminationTree.setOracle(oracle);
        super.hypothesis = startingState.getHypothesis();
        super.stateInfos = startingState.getStateInfos();
    }

//    private void initialize() {
//        // Minimising the tree at the start allows us the make the tree smaller, limiting sift depth.
//        minimiseTree();
//
//        DefaultQuery<I, Boolean> nonCanonCex = analyseTree();
//        while (nonCanonCex != null) {
//            while (refineHypothesisSingle(nonCanonCex.getInput(), nonCanonCex.getOutput())) {}
//            nonCanonCex = analyseTree();
//        }
//    }
//
//    @Override
//    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ceQuery) {
//        if (hypothesis.size() == 0) {
//            throw new IllegalStateException("Not initialized");
//        }
//        Word<I> input = ceQuery.getInput();
//        Word<O> output = ceQuery.getOutput();
//        if (!refineHypothesisSingle(input, output)) {
//            return false;
//        }
//        if (repeatedCounterexampleEvaluation) {
//            while (refineHypothesisSingle(input, output)) {}
//        }
//
//        return true;
//    }

}
