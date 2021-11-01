package de.learnlib.algorithms.continuous.base;

import java.util.HashSet;
import java.util.Set;

import net.automatalib.words.Word;

public final class ICState<I, D> {
    public Boolean accepting;
    public Word<I> accessSequence;
    public final Set<ICTransition<I, D>> incoming;

    public ICState(Word<I> accessSequence) {
        this.accepting = null;
        this.accessSequence = accessSequence.trimmed();
        this.incoming = new HashSet<>();
    }
    public void addIncoming(ICTransition<I, D> trans) {
        incoming.add(trans);
    }

    public void removeIncoming(ICTransition<I, D> trans) {
        incoming.remove(trans);
    }

    public void clearIncoming() {
        incoming.clear();
    }

    public boolean isAccepting() {
        return accepting;
    }
}
