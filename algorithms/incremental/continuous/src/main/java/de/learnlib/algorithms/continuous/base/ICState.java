package de.learnlib.algorithms.continuous.base;

import java.util.HashSet;
import java.util.Set;

import net.automatalib.words.Word;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ICState<I, D> {
    public Boolean accepting;
    public Word<I> accessSequence;
    public final Set<ICTransition<I, D>> incoming = new HashSet<>();

    public ICState() {
        this((Word<I>) null);
    }

    public ICState(ICState<I, D> state) {
        this.accessSequence = state.accessSequence != null
            ? Word.fromWords(state.accessSequence)
            : null;

        this.accepting = state.accepting;
        this.incoming.addAll(state.incoming);
    }

    public ICState(@Nullable Word<I> accessSequence) {
        this.accepting = null;
        this.accessSequence = accessSequence;
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
