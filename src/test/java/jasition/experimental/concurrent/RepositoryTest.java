package jasition.experimental.concurrent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RepositoryTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Repository<Long, VersionedString> repo;

    @Mock
    private Function<Long, Long> nextVersionProducer;

    private VersionedString initial;

    @Before
    public void setUp() {
        initial = new VersionedString(10l, "initial");
        when(nextVersionProducer.apply(anyLong())).thenAnswer(
                invocationOnMock -> (Long) invocationOnMock.getArgument(0) + 1);

        repo = initRepo(this.initial);
    }

    private Repository<Long, VersionedString> initRepo(VersionedString initial) {
        return new Repository<>(nextVersionProducer, Long::compare, initial);
    }

    @Test
    public void returnInitialValue() {
        assertThat(repo.get()).isEqualTo(initial);
    }

    @Test
    public void replaceInitialValueWithNewValue() {
        VersionedString newValue = new VersionedString(11l, "new value");

        repo.set(s -> newValue);

        assertThat(repo.get()).isEqualTo(newValue);
    }

    @Test
    public void throwExceptionWhenReplaceInitialValueWithNewValueUseJumpedVersion() {
        VersionedString newValue = new VersionedString(12l, "new value");
        expectedException.expect(IllegalArgumentException.class);

        repo.set(s -> newValue);
    }

    @Test
    public void rebaseAndRetryReplaceInitialValueWhenNewValueHasOutOfDateVersion() {
        VersionedString first = new VersionedString(9l, "new value 1");
        VersionedString second = new VersionedString(11l, "new value 2");
        Function<VersionedString, VersionedString> updateFunction = Mockito.mock(Function.class);
        when(updateFunction.apply(initial))
                .thenReturn(first)
                .thenReturn(second);

        repo.set(updateFunction);

        assertThat(repo.get()).isEqualTo(second);
    }

    @Test
    public void giveUpTryingAfterMaxTryCountReached() {
        repo.setMaxTry(3);
        VersionedString first = new VersionedString(7l, "new value 7");
        VersionedString second = new VersionedString(8l, "new value 8");
        VersionedString third = new VersionedString(9l, "new value 9");
        Function<VersionedString, VersionedString> updateFunction = Mockito.mock(Function.class);
        when(updateFunction.apply(initial))
                .thenReturn(first)
                .thenReturn(second)
                .thenReturn(third);

        expectedException.expect(RuntimeException.class);

        repo.set(updateFunction);
    }

    @Test
    public void exceptionThrownWhenSetMaxTryToNegative() {
        expectedException.expect(IllegalArgumentException.class);

        repo.setMaxTry(-1);
    }

    static class VersionedString implements Versioned<Long> {
        final Long version;
        final String value;

        VersionedString(Long version, String value) {
            this.version = version;
            this.value = value;
        }

        @Override
        public Long getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return "VersionedString{" +
                    "version=" + version +
                    ", value='" + value + '\'' +
                    '}';
        }
    }
}