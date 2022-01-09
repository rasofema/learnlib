/* Copyright (C) 2018
 * This file is part of the PhD research project entitled
 * 'Inferring models from Evolving Systems and Product Families'
 * developed by Carlos Diego Nascimento Damasceno at the
 * University of Sao Paulo (ICMC-USP).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.datastructure.observationtable;

import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.commons.util.comparison.CmpUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

import java.io.Serializable;
import java.util.*;

/**
 * Dynamic Observation table class.
 * <p>
 * A dynamic observation table (dOT) is an extension of the traditional OT proposed by Dana Angluin
 * inspired by Chaki et al.'s Dynamic L* algorithm, as described in the paper
 * "Verification of evolving software via component substitutability analysis".
 * <p>
 * A dOT is a two-dimensional table, with rows indexed by prefixes, and columns indexed by suffixes
 * which differs to the traditional OT on the way it is initialized.
 * <p>
 * A dOT can be initialized with initial prefix/suffix sets where, instead of asking MQs for them all,
 * prefixes are gradually concatenated to the set of initial suffixes and revalidated to the SUL.
 * Thus, redundant MQs can be avoided by prunning the tree representation of the set of prefixes for
 * <i>finding an well-formed cover subset</i> from the <code>initialShortPrefixes</code> set.
 *
 * Added to redundant prefixes, the <code>initialSuffixes</code> set may also include redundant suffixes.
 * Thus, to reduce the number of MQs after initialization, redundant suffixes are discarded by
 * <i>finding an experiment cover subset</i> from the <code>initialSuffixes</code> set so that the next steps
 * for restoring the properties of closedness and consistency do not pose MQs using unnecessary suffixes.
 *
 *
 * @param <I>
 *         input symbol type
 * @param <D>
 *         output domain type
 *
 * @author Carlos Diego Nascimento Damasceno (damascenodiego@usp.br)
 */
public final class DynamicObservationTable<I, D> implements MutableObservationTable<I, D>, Serializable {

    private static final Integer NO_ENTRY = null; // TODO: replace with primitive specialization
    private final List<RowImpl<I>> shortPrefixRows = new ArrayList<>();
    // private static final int NO_ENTRY = -1;
    private final List<RowImpl<I>> longPrefixRows = new ArrayList<>();
    private final List<RowImpl<I>> allRows = new ArrayList<>();
    private final List<List<D>> allRowContents = new ArrayList<>();
    private final List<RowImpl<I>> canonicalRows = new ArrayList<>();
    // private final TObjectIntMap<List<D>> rowContentIds = new TObjectIntHashMap<>(10, 0.75f, NO_ENTRY);
    private final Map<List<D>, Integer> rowContentIds = new HashMap<>(); // TODO: replace with primitive specialization
    private final Map<Word<I>, RowImpl<I>> rowMap = new HashMap<>();
    private final List<Word<I>> suffixes = new ArrayList<>();
    private final Set<Word<I>> suffixSet = new HashSet<>();
    private transient Alphabet<I> alphabet;
    private int numRows;
    private boolean initialConsistencyCheckRequired;

    /**
     * Constructor.
     *
     * @param alphabet
     *         the learning alphabet.
     */
    public DynamicObservationTable(Alphabet<I> alphabet) {
        this.alphabet = alphabet;
    }

    private static <I, D> void buildQueries(List<DefaultQuery<I, D>> queryList,
                                            Word<I> prefix,
                                            List<? extends Word<I>> suffixes) {
        for (Word<I> suffix : suffixes) {
            queryList.add(new DefaultQuery<>(prefix, suffix));
        }
    }

    public List<List<Row<I>>> initialize(List<Word<I>> initialShortPrefixes,
                                         List<Word<I>> initialSuffixes,
                                         MembershipOracle<I, D> oracle) {
        if (allRows.size() > 0) {
            throw new IllegalStateException("Called initialize, but there are already rows present");
        }

        if (!checkPrefixClosed(initialShortPrefixes)) {
            throw new IllegalArgumentException("Initial short prefixes are not prefix-closed");
        }

        if (!initialShortPrefixes.get(0).isEmpty()) {
            throw new IllegalArgumentException("First initial short prefix MUST be the empty word!");
        }

        // copy S_M and E_M to not change the objects passed as parameter
        List<Word<I>> t_initialShortPrefixes = new ArrayList<>(initialShortPrefixes);
        List<Word<I>> t_initialSuffixes = new ArrayList<>(initialSuffixes.size());

        // if has more than empty sequence, then include S_R \cdot I_u
        Alphabet<I> abc = this.alphabet;
        if(t_initialShortPrefixes.size()!=1){
            List<Word<I>> sr_cdot_i = new ArrayList<>(t_initialShortPrefixes.size()*abc.size());
            for (Word<I> pref : t_initialShortPrefixes){
                for (I i_u_symb: abc){
                    sr_cdot_i.add(pref.append(i_u_symb));
                }
            }
            for (Word<I> pref : sr_cdot_i){
                if(!t_initialShortPrefixes.contains(pref)){
                    t_initialShortPrefixes.add(pref);
                }
            }
        }

        // sort S_M for having EMPTY string at the first position and help on discarding redundant prefixes
        Collections.sort(t_initialShortPrefixes, new Comparator<Word<I>>() {
            @Override
            public int compare(Word<I> o1, Word<I> o2) { return CmpUtil.lexCompare(o1, o2, abc); }
        });

        // for loop to remove duplicated suffixes
        Set<Word<I>> t_suffixSet = new HashSet<>();
        for (Word<I> suffix : initialSuffixes) {
            // checks if there are duplicated suffixes
            if (t_suffixSet.add(suffix))  t_initialSuffixes.add(suffix);
        }

        // max number of suffixes and prefixes
        int numSuffixes = t_initialSuffixes.size();
        int numPrefixes = alphabet.size() * t_initialShortPrefixes.size() + 1;

        // temporary list of queries w/IO pairs
        List<DefaultQuery<I, D>> t_queries = new ArrayList<>(numPrefixes * numSuffixes);
        List<DefaultQuery<I, D>> t_all_queries = new ArrayList<>(numPrefixes * numSuffixes);

        // set of observed outputs (helps to identify states reached using other prefixes)
        Set<List<D>> t_observedOutputs = new HashSet<>(numPrefixes * numSuffixes);

        // list to keep the outputs of each row for each query posed
        List<D> t_outputs = null;

        // outputs obtained for all short rows included at the well-formed cover subset
        Map<Word<I>,List<D>> observationMap = new HashMap<>();

        // PASS 1: Gradually add short prefix rows while finding an well-formed cover subset from initialSuffixes
        for (int i = 0; i < t_initialShortPrefixes.size(); i++) {
            // queries to be posed
            t_queries.clear();

            // new t_outputs to be included at the observationMap
            t_outputs = new ArrayList<>();

            // prefix to be concatenated with E_M and posed as MQ
            Word<I> sp = t_initialShortPrefixes.get(i);
            buildQueries(t_queries, sp, t_initialSuffixes);
            oracle.processQueries(t_queries);

            // concatenate outputs to compare with those previously observed
            Iterator<DefaultQuery<I, D>> queryIt = t_queries.iterator();
            while (queryIt.hasNext()) t_outputs.add(queryIt.next().getOutput());

            // if NOT observed previously
            if(!t_observedOutputs.contains(t_outputs)){
                // Finally add sp to the set of short prefixes S_M
                createSpRow(sp);
                t_observedOutputs.add(t_outputs);
                observationMap.put(sp,t_outputs);

            }else if(i < t_initialShortPrefixes.size()){
                while (sp.isPrefixOf(t_initialShortPrefixes.get(i))){
                    i++;
                    if(i == t_initialShortPrefixes.size())  break;
                }
                i--;
            }
            t_all_queries.addAll(t_queries);
        }

        // Find an experiment cover subset from the initialSuffixes set
        List<Integer> experimentCoverSubset_id = findExperimentCover(observationMap,t_initialSuffixes);

        for (int i = 0; i < experimentCoverSubset_id .size(); i++) {
            if (suffixSet.add(t_initialSuffixes.get(experimentCoverSubset_id.get(i)))) {
                suffixes.add(t_initialSuffixes.get(experimentCoverSubset_id.get(i)));
            }
        }

        List<DefaultQuery<I, D>> queries = new ArrayList<>(numPrefixes * numSuffixes);

        // PASS 2: Add missing long prefix rows
        for (RowImpl<I> spRow : shortPrefixRows) {
            Word<I> sp = spRow.getLabel();
            for (int i = 0; i < alphabet.size(); i++) {
                I sym = alphabet.getSymbol(i);
                Word<I> lp = sp.append(sym);
                RowImpl<I> succRow = rowMap.get(lp);
                if (succRow == null) {
                    succRow = createLpRow(lp);
                    buildQueries(queries, lp, suffixes);
                }
                spRow.setSuccessor(i, succRow);
            }
        }

        oracle.processQueries(queries);

        int pos = 0;
        for (DefaultQuery<I, D> t_query:t_all_queries) {
            if(observationMap.containsKey(t_query.getPrefix()) && getSuffixes().contains(t_query.getSuffix())){
                queries.add(pos,t_query);
                pos++;
            }
        }

        // update number of prefixes to the size of the well-formed cover subset
        numPrefixes = alphabet.size() * getShortPrefixRows().size() + 1;

        // update number of suffixes to the size of the experiment cover subsets
        numSuffixes = getSuffixes().size();

        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        for (RowImpl<I> spRow : shortPrefixRows) {
            List<D> rowContents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, rowContents, numSuffixes);
            if (!processContents(spRow, rowContents, true)) {
                initialConsistencyCheckRequired = true;
            }
        }

        int distinctSpRows = numberOfDistinctRows();

        List<List<Row<I>>> unclosed = new ArrayList<>();

        for (RowImpl<I> spRow : shortPrefixRows) {
            for (int i = 0; i < alphabet.size(); i++) {
                RowImpl<I> succRow = spRow.getSuccessor(i);
                if (succRow.isShortPrefixRow()) {
                    continue;
                }
                List<D> rowContents = new ArrayList<>(numSuffixes);
                fetchResults(queryIt, rowContents, numSuffixes);
                if (processContents(succRow, rowContents, false)) {
                    unclosed.add(new ArrayList<>());
                }

                int id = succRow.getRowContentId();

                if (id >= distinctSpRows) {
                    unclosed.get(id - distinctSpRows).add(succRow);
                }
            }
        }

        return unclosed;
    }

    // find the experiment cover set using an approach similar to that for synchronizing trees
    List<Integer> findExperimentCover(Map<Word<I>, List<D>> observationMap, List<Word<I>> suffixes){

        List<Integer> out = new ArrayList<>();

        if(shortPrefixRows.size()==1){
            for (int i = 0; i < suffixes.size(); i++) {
                out.add(i);
            }
            return out;
        }

        // keeps the set of distinguished states and the suffixes used to do it
        List<DynamicDistinguishableStates<I,D>> toAnalyze = new ArrayList<>();

        // set of nodes found (used to find previously visited states)
        Set<Set<Set<Word<I>>>> nodesFound = new HashSet<>();

        // creates the first DynamicDistinguishableStates w/all states undistinguished
        Set<Set<Word<I>>> diff_states = new HashSet<>();
        diff_states.add(observationMap.keySet());

        // no suffixes applied
        Set<Integer> eSubset = new HashSet<>();

        toAnalyze.add(new DynamicDistinguishableStates<I,D>(observationMap, diff_states, eSubset));

        // current DynamicDistinguishableStates analyzed ( singleton is kept here )
        DynamicDistinguishableStates<I,D> item = toAnalyze.get(0);

        // the DynamicDistinguishableStates with the 'best' subset of E
        DynamicDistinguishableStates<I,D> best = toAnalyze.get(0);

        // ExperimentCover.find: Analysis begin"
        while (!toAnalyze.isEmpty()) {
            item = toAnalyze.remove(0);

            // Does item distinguish the largest number of states ?
            if(item.getDistinguishedStates().size()>best.getDistinguishedStates().size()) {
                // then keep it as the best option
                best = item;
            }

            // ExperimentCover.find: Singleton found!
            if(item.isSingleton()) {
                break; // Thus, stop here!!! :)
            }

            // get number of suffixes
            for (int sufIdx = 0; sufIdx < suffixes.size(); sufIdx++){
                if(item.getESubset().contains(sufIdx)) {
                    continue; // suffix already applied to this item
                }

                // new subset of states that may be distinguished by 'sufIdx'
                diff_states = new HashSet<>();

                // subset of suffixes (potential experiment cover)
                eSubset = new HashSet<>();

                Set<Set<Word<I>>> setOfPrefixes = item.getDistinguishedStates();
                for (Set<Word<I>> prefixes : setOfPrefixes) {
                    // maps the outputs to rows (used for keeping states equivalent given 'sufIdx')
                    Map<Integer,Set<Integer>> out2Rows = new TreeMap<>();
                    // look 'sufIdx' for each prefix
                    for (Word<I> pref : prefixes) {
                        D outStr = observationMap.get(pref).get(sufIdx);
                        // if outStr is new, then add sufIdx as an useful suffix
                        if(out2Rows.putIfAbsent(outStr.hashCode(), new HashSet<Integer>()) == null){
                            eSubset.add(sufIdx);
                        }
                        out2Rows.get(outStr.hashCode()).add(getRow(pref).getRowId());
                    }
                    // the subsets of states that are distinguished by 'sufIdx'
                    for (Set<Integer> sset: out2Rows.values()) {
                        Set<Word<I>> sset_word = new HashSet<>();
                        for (Integer sset_item: sset) {
                            sset_word.add(getShortPrefixRows().get(sset_item).getLabel());
                        }
                        diff_states.add(sset_word);
                    }

                }
                // if diff_states was previously visited, then discard! :(
                if(nodesFound.contains(diff_states)) continue;
                nodesFound.add(diff_states); // otherwise keep it!
                // create a new de.learnlib.datastructure.observationtable.DynamicDistinguishableStates
                DynamicDistinguishableStates new_diststates = new DynamicDistinguishableStates(observationMap);
                new_diststates.setDistinguishedStates(diff_states);
                // add previously applied suffixes to eSubset (i.e., { eSubset \cup 'sufIdx'}
                eSubset.addAll(item.getESubset());
                new_diststates.setESubset(eSubset);
                // add it to be analyzed later
                toAnalyze.add(new_diststates);
            }
        }
        // ExperimentCover.find: Analysis end!


        if(item.isSingleton()){
            // if item is singleton then return its suffixes
            out.addAll(item.getESubset());
        }else{
            // otherwise add the 'best' subset of E
            out.addAll(best.getESubset());
        }
        return out;
    }

    private static <I> boolean checkPrefixClosed(Collection<? extends Word<I>> initialShortPrefixes) {
        Set<Word<I>> prefixes = new HashSet<>(initialShortPrefixes);

        for (Word<I> pref : initialShortPrefixes) {
            if (!pref.isEmpty()) {
                if (!prefixes.contains(pref.prefix(-1))) {
                    return false;
                }
            }
        }

        return true;
    }

    private RowImpl<I> createSpRow(Word<I> prefix) {
        RowImpl<I> newRow = new RowImpl<>(prefix, numRows++, alphabet.size());
        allRows.add(newRow);
        rowMap.put(prefix, newRow);
        shortPrefixRows.add(newRow);
        return newRow;
    }

    private RowImpl<I> createLpRow(Word<I> prefix) {
        RowImpl<I> newRow = new RowImpl<>(prefix, numRows++);
        allRows.add(newRow);
        rowMap.put(prefix, newRow);
        int idx = longPrefixRows.size();
        longPrefixRows.add(newRow);
        newRow.setLpIndex(idx);
        return newRow;
    }

    /**
     * Fetches the given number of query responses and adds them to the specified output list. Also, the query iterator
     * is advanced accordingly.
     *
     * @param queryIt
     *         the query iterator
     * @param output
     *         the output list to write to
     * @param numSuffixes
     *         the number of suffixes (queries)
     */
    private static <I, D> void fetchResults(Iterator<DefaultQuery<I, D>> queryIt, List<D> output, int numSuffixes) {
        for (int j = 0; j < numSuffixes; j++) {
            DefaultQuery<I, D> qry = queryIt.next();
            output.add(qry.getOutput());
        }
    }

    private boolean processContents(RowImpl<I> row, List<D> rowContents, boolean makeCanonical) {
        Integer contentId; // TODO: replace with primitive specialization
        // int contentId;
        boolean added = false;
        contentId = rowContentIds.get(rowContents);
        if (contentId == NO_ENTRY) {
            contentId = numberOfDistinctRows();
            rowContentIds.put(rowContents, contentId);
            allRowContents.add(rowContents);
            added = true;
            if (makeCanonical) {
                canonicalRows.add(row);
            } else {
                canonicalRows.add(null);
            }
        }
        row.setRowContentId(contentId);
        return added;
    }

    public int numberOfDistinctRows() {
        return allRowContents.size();
    }

    public List<List<Row<I>>> addSuffix(Word<I> suffix, MembershipOracle<I, D> oracle) {
        return addSuffixes(Collections.singletonList(suffix), oracle);
    }

    public List<List<Row<I>>> addSuffixes(Collection<? extends Word<I>> newSuffixes, MembershipOracle<I, D> oracle) {
        int oldSuffixCount = suffixes.size();
        // we need a stable iteration order, and only List guarantees this
        List<Word<I>> newSuffixList = new ArrayList<>();
        for (Word<I> suffix : newSuffixes) {
            if (suffixSet.add(suffix)) {
                newSuffixList.add(suffix);
            }
        }

        if (newSuffixList.isEmpty()) {
            return Collections.emptyList();
        }

        int numNewSuffixes = newSuffixList.size();

        int numSpRows = shortPrefixRows.size();
        int rowCount = numSpRows + longPrefixRows.size();

        List<DefaultQuery<I, D>> queries = new ArrayList<>(rowCount * numNewSuffixes);

        for (RowImpl<I> row : shortPrefixRows) {
            buildQueries(queries, row.getLabel(), newSuffixList);
        }

        for (RowImpl<I> row : longPrefixRows) {
            buildQueries(queries, row.getLabel(), newSuffixList);
        }

        oracle.processQueries(queries);

        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        for (RowImpl<I> row : shortPrefixRows) {
            List<D> rowContents = allRowContents.get(row.getRowContentId());
            if (rowContents.size() == oldSuffixCount) {
                rowContentIds.remove(rowContents);
                fetchResults(queryIt, rowContents, numNewSuffixes);
                rowContentIds.put(rowContents, row.getRowContentId());
            } else {
                List<D> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
                newContents.addAll(rowContents.subList(0, oldSuffixCount));
                fetchResults(queryIt, newContents, numNewSuffixes);
                processContents(row, newContents, true);
            }
        }

        List<List<Row<I>>> unclosed = new ArrayList<>();
        numSpRows = numberOfDistinctRows();

        for (RowImpl<I> row : longPrefixRows) {
            List<D> rowContents = allRowContents.get(row.getRowContentId());
            if (rowContents.size() == oldSuffixCount) {
                rowContentIds.remove(rowContents);
                fetchResults(queryIt, rowContents, numNewSuffixes);
                rowContentIds.put(rowContents, row.getRowContentId());
            } else {
                List<D> newContents = new ArrayList<>(oldSuffixCount + numNewSuffixes);
                newContents.addAll(rowContents.subList(0, oldSuffixCount));
                fetchResults(queryIt, newContents, numNewSuffixes);
                if (processContents(row, newContents, false)) {
                    unclosed.add(new ArrayList<>());
                }

                int id = row.getRowContentId();
                if (id >= numSpRows) {
                    unclosed.get(id - numSpRows).add(row);
                }
            }
        }

        this.suffixes.addAll(newSuffixList);

        return unclosed;
    }

    public boolean isInitialConsistencyCheckRequired() {
        return initialConsistencyCheckRequired;
    }

    public List<List<Row<I>>> addShortPrefixes(List<? extends Word<I>> shortPrefixes, MembershipOracle<I, D> oracle) {
        List<Row<I>> toSpRows = new ArrayList<>();

        for (Word<I> sp : shortPrefixes) {
            RowImpl<I> row = rowMap.get(sp);
            if (row != null) {
                if (row.isShortPrefixRow()) {
                    continue;
                }
            } else {
                row = createSpRow(sp);
            }
            toSpRows.add(row);
        }

        return toShortPrefixes(toSpRows, oracle);
    }

    public List<List<Row<I>>> toShortPrefixes(List<Row<I>> lpRows, MembershipOracle<I, D> oracle) {
        List<RowImpl<I>> freshSpRows = new ArrayList<>();
        List<RowImpl<I>> freshLpRows = new ArrayList<>();

        for (Row<I> r : lpRows) {
            final RowImpl<I> row = allRows.get(r.getRowId());
            if (row.isShortPrefixRow()) {
                if (row.hasContents()) {
                    continue;
                }
                freshSpRows.add(row);
            } else {
                makeShort(row);
                if (!row.hasContents()) {
                    freshSpRows.add(row);
                }
            }

            Word<I> prefix = row.getLabel();

            for (int i = 0; i < alphabet.size(); i++) {
                I sym = alphabet.getSymbol(i);
                Word<I> lp = prefix.append(sym);
                RowImpl<I> lpRow = rowMap.get(lp);
                if (lpRow == null) {
                    lpRow = createLpRow(lp);
                    freshLpRows.add(lpRow);
                }
                row.setSuccessor(i, lpRow);
            }
        }

        int numSuffixes = suffixes.size();

        int numFreshRows = freshSpRows.size() + freshLpRows.size();
        List<DefaultQuery<I, D>> queries = new ArrayList<>(numFreshRows * numSuffixes);
        buildRowQueries(queries, freshSpRows, suffixes);
        buildRowQueries(queries, freshLpRows, suffixes);

        oracle.processQueries(queries);
        Iterator<DefaultQuery<I, D>> queryIt = queries.iterator();

        for (RowImpl<I> row : freshSpRows) {
            List<D> contents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, contents, numSuffixes);
            processContents(row, contents, true);
        }

        int numSpRows = numberOfDistinctRows();
        List<List<Row<I>>> unclosed = new ArrayList<>();

        for (RowImpl<I> row : freshLpRows) {
            List<D> contents = new ArrayList<>(numSuffixes);
            fetchResults(queryIt, contents, numSuffixes);
            if (processContents(row, contents, false)) {
                unclosed.add(new ArrayList<>());
            }

            int id = row.getRowContentId();
            if (id >= numSpRows) {
                unclosed.get(id - numSpRows).add(row);
            }
        }

        return unclosed;
    }

    private boolean makeShort(RowImpl<I> row) {
        if (row.isShortPrefixRow()) {
            return false;
        }

        int lastIdx = longPrefixRows.size() - 1;
        RowImpl<I> last = longPrefixRows.get(lastIdx);
        int rowIdx = row.getLpIndex();
        longPrefixRows.remove(lastIdx);
        if (last != row) {
            longPrefixRows.set(rowIdx, last);
            last.setLpIndex(rowIdx);
        }

        shortPrefixRows.add(row);
        row.makeShort(alphabet.size());

        if (row.hasContents()) {
            int cid = row.getRowContentId();
            if (canonicalRows.get(cid) == null) {
                canonicalRows.set(cid, row);
            }
        }
        return true;
    }

    private static <I, D> void buildRowQueries(List<DefaultQuery<I, D>> queryList,
                                               List<? extends Row<I>> rows,
                                               List<? extends Word<I>> suffixes) {
        for (Row<I> row : rows) {
            buildQueries(queryList, row.getLabel(), suffixes);
        }
    }

    public D cellContents(Row<I> row, int columnId) {
        List<D> contents = rowContents(row);
        return contents.get(columnId);
    }

    public List<D> rowContents(Row<I> row) {
        return allRowContents.get(row.getRowContentId());
    }

    public RowImpl<I> getRow(int rowId) {
        return allRows.get(rowId);
    }

    public int numberOfRows() {
        return shortPrefixRows.size() + longPrefixRows.size();
    }

    public List<Word<I>> getSuffixes() {
        return suffixes;
    }

    public boolean isInitialized() {
        return (allRows.size() > 0);
    }

    public Alphabet<I> getInputAlphabet() {
        return alphabet;
    }

    /**
     * This is an internal method used for de-serializing. Do not deliberately set input alphabets.
     *
     * @param alphabet
     *         the input alphabet corresponding to the previously serialized one.
     */
    public void setInputAlphabet(Alphabet<I> alphabet) {
        this.alphabet = alphabet;
    }

    @Override
    public Word<I> transformAccessSequence(Word<I> word) {
        Row<I> current = shortPrefixRows.get(0);

        for (I sym : word) {
            current = getRowSuccessor(current, sym);
            current = canonicalRows.get(current.getRowContentId());
        }

        return current.getLabel();
    }

    @Override
    public boolean isAccessSequence(Word<I> word) {
        Row<I> current = shortPrefixRows.get(0);

        for (I sym : word) {
            current = getRowSuccessor(current, sym);
            if (!isCanonical(current)) {
                return false;
            }
        }

        return true;
    }

    private boolean isCanonical(Row<I> row) {
        if (!row.isShortPrefixRow()) {
            return false;
        }
        int contentId = row.getRowContentId();
        return (canonicalRows.get(contentId) == row);
    }

    @Override
    public List<List<Row<I>>> addAlphabetSymbol(I symbol, final MembershipOracle<I, D> oracle) {

        if (this.alphabet.containsSymbol(symbol)) {
            return Collections.emptyList();
        }

        if (!alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(alphabet).addSymbol(symbol);
        }
        final int newAlphabetSize = this.alphabet.size();
        final int newSymbolIdx = this.alphabet.getSymbolIndex(symbol);

        final List<RowImpl<I>> shortPrefixes = shortPrefixRows;
        final List<RowImpl<I>> newLongPrefixes = new ArrayList<>(shortPrefixes.size());

        for (RowImpl<I> prefix : shortPrefixes) {
            prefix.ensureInputCapacity(newAlphabetSize);

            final Word<I> newLongPrefix = prefix.getLabel().append(symbol);
            final RowImpl<I> longPrefixRow = createLpRow(newLongPrefix);

            newLongPrefixes.add(longPrefixRow);
            prefix.setSuccessor(newSymbolIdx, longPrefixRow);
        }

        final int numLongPrefixes = newLongPrefixes.size();
        final int numSuffixes = this.numberOfSuffixes();
        final List<DefaultQuery<I, D>> queries = new ArrayList<>(numLongPrefixes * numSuffixes);

        buildRowQueries(queries, newLongPrefixes, suffixes);
        oracle.processQueries(queries);

        final Iterator<DefaultQuery<I, D>> queryIterator = queries.iterator();
        final List<List<Row<I>>> result = new ArrayList<>(numLongPrefixes);

        for (RowImpl<I> row : newLongPrefixes) {
            final List<D> contents = new ArrayList<>(numSuffixes);

            fetchResults(queryIterator, contents, numSuffixes);

            if (processContents(row, contents, false)) {
                result.add(Collections.singletonList(row));
            }
        }

        return result;
    }

    @Override
    public List<Row<I>> getShortPrefixRows() {
        return Collections.unmodifiableList(shortPrefixRows);
    }

    @Override
    public Collection<Row<I>> getLongPrefixRows() {
        return Collections.unmodifiableList(longPrefixRows);
    }
}
