package selectionwheel;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** Checks that spins are driven by the injected RandomGenerator (no window is shown). */
class MainWheelRandomTest {

    @Test
    void sameSeedGivesSameSpins() {
        MainWheel a = new MainWheel(new Random(42));
        MainWheel b = new MainWheel(new Random(42));
        for (int i = 0; i < 20; i++) {
            assertThat(a.nextSpinSpeed()).isEqualTo(b.nextSpinSpeed());
            assertThat(a.nextSpinDirection()).isEqualTo(b.nextSpinDirection());
        }
    }

    @Test
    void speedAndDirectionFollowGeneratorExtremes() {
        RandomGenerator low = new RandomGenerator() {
            public long nextLong() { return 0; }
            public double nextDouble() { return 0.0; }
            public boolean nextBoolean() { return false; }
        };
        MainWheel w = new MainWheel(low);
        assertThat(w.nextSpinSpeed()).isEqualTo(180.0);
        assertThat(w.nextSpinDirection()).isEqualTo(-1);
    }

    @Test
    void speedStaysInRange() {
        MainWheel w = new MainWheel(new Random(7));
        for (int i = 0; i < 1000; i++) {
            assertThat(w.nextSpinSpeed()).isBetween(180.0, 360.0);
        }
    }

    @Test
    void rejectsNullGenerator() {
        assertThatNullPointerException().isThrownBy(() -> new MainWheel(null));
    }
}
