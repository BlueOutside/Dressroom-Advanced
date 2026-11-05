package dev.denver.dressroomadvanced;

import java.util.ArrayList;

public final class ChargingStateTest {
    public static final class BackportAnimator {
        public ArrayList<Runnable> mListeners = new ArrayList<>();
        boolean cancelled, fail;

        public void addListener(Runnable listener) {
            mListeners.add(listener);
        }

        public void removeAllListeners() {
            mListeners.clear();
        }

        public void cancel() {
            cancelled = true;
            for (Runnable listener : mListeners) listener.run();
            if (fail) throw new IllegalStateException("cancel");
        }
    }

    private static int checks;

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Charging check " + checks);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        check(ChargingState.percent(18, 100) == 18);
        check(ChargingState.percent(9, 20) == 45);
        check(ChargingState.percent(1, 3) == 33);
        check(ChargingState.percent(-1, 100) == -1);
        check(ChargingState.percent(40, 0) == -1);
        check(ChargingState.percent(Integer.MAX_VALUE, 100) == 100);
        check(ChargingState.translation(200, 0, 25) == -150);
        check(ChargingState.translation(200, 1, 25) == 150);
        check(ChargingState.translation(200, 0, 100) == 0);
        check(ChargingState.translation(200, 0, -1) == -200);
        BackportAnimator animator = new BackportAnimator();
        Runnable dismiss =
                () -> {
                    throw new AssertionError("Native dismiss callback fired");
                };
        animator.addListener(dismiss);
        ChargingState.pause(animator);
        check(animator.cancelled);
        check(animator.mListeners.size() == 1 && animator.mListeners.get(0) == dismiss);
        animator.fail = true;
        try {
            ChargingState.pause(animator);
            throw new AssertionError("Failure lost");
        } catch (ReflectiveOperationException expected) {
            checks++;
        }
        check(animator.mListeners.size() == 1 && animator.mListeners.get(0) == dismiss);
        System.out.println("Charging state: " + checks + " checks passed");
    }
}
