/* Copyright (C) 2013-2022 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
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
package de.learnlib.oracle.membership;

import java.util.Collection;

import de.learnlib.exception.LimitException;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.Query;
import net.automatalib.word.Word;

/**
 * A wrapper around a system under learning (SUL).
 * <p>
 * This membership oracle is <b>not</b> thread-safe.
 *
 * @author Falk Howar
 * @author Malte Isberner
 */
public class LimitOracle<I, D> implements MembershipOracle<I, D> {

    private final Long queryLimit;
    private Long queryCount;
    private final MembershipOracle<I, D> delegate;

    public LimitOracle(Long queryLimit, MembershipOracle<I, D> delegate) {
        this.queryLimit = queryLimit;
        this.queryCount = (long) 0;
        this.delegate = delegate;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, D>> queries) {
        for (Query<I, D> q : queries) {
            if (queryCount >= queryLimit) {
                throw new LimitException();
            }
            queryCount++;
            q.answer(delegate.answerQuery(q.getPrefix(), q.getSuffix()));
        }
    }

    @Override
    public D answerQuery(Word<I> prefix, Word<I> suffix) {
        if (queryCount >= queryLimit) {
            throw new LimitException();
        }
        queryCount++;
        return delegate.answerQuery(prefix, suffix);
    }

}
