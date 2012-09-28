package org.gridkit.nimble.statistics.simple;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.gridkit.nimble.statistics.FlushableStatsReporter;

@SuppressWarnings("serial")
public class SimpleStatsProducer implements FlushableStatsReporter, Serializable {
    private Map<String, SummaryStatistics> valStats;

    public SimpleStatsProducer() {
        this.valStats = new HashMap<String, SummaryStatistics>();
    }

    public static SimpleStatsProducer newInstance(final SimpleStatsAggregator aggr) {
        return new SimpleStatsProducer() {
            @Override
            public void flush() {
                aggr.add(produce());
                super.flush();
            }
        };
    }
    
    @Override
    public void report(Map<String, Object> stats) {
        for (Map.Entry<String, Object> value : stats.entrySet()) {
            if (value.getValue() instanceof Number) {
                SummaryStatistics statSum = getValStats(value.getKey());
                statSum.addValue(((Number)value.getValue()).doubleValue());
            }
        }
    }
        
    private SummaryStatistics getValStats(String name) {
        SummaryStatistics stats = valStats.get(name);
        
        if (stats == null) {
            stats = new SummaryStatistics();
            valStats.put(name, stats);
        }
        
        return stats;
    }

    public SimpleStats produce() {
        Map<String, StatisticalSummary> result = new HashMap<String, StatisticalSummary>();
        
        for (Map.Entry<String, SummaryStatistics> entry : valStats.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getSummary());
        }
        
        return new SimpleStats(result);
    }
    
    @Override
    public void flush() {
        valStats.clear();
    }
}