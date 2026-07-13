package dev.querylab.bench;

import dev.querylab.common.model.Transaction;
import dev.querylab.common.search.SearchResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

/**
 * Full round trip — build + execute (count + data query) + map — against a
 * 100k-row Postgres. SampleTime mode because latency distribution matters more
 * than the mean for anything involving a network hop.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class EndToEndSearchBenchmark {

    @Param({"simple", "complex"})
    public String scenario;

    @Benchmark
    public SearchResult<Transaction> search(EngineState engines) {
        return engines.port().search(BenchScenarios.byName(scenario));
    }
}
