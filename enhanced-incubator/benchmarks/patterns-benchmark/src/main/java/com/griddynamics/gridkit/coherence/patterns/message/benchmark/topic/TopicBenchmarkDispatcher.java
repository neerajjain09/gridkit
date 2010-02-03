package com.griddynamics.gridkit.coherence.patterns.message.benchmark.topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.griddynamics.gridkit.coherence.patterns.benchmark.Dispatcher;
import com.griddynamics.gridkit.coherence.patterns.benchmark.MessageExecutionMark;
import com.griddynamics.gridkit.coherence.patterns.benchmark.stats.Accamulator;
import com.griddynamics.gridkit.coherence.patterns.benchmark.stats.InvocationServiceStats;
import com.griddynamics.gridkit.coherence.patterns.message.benchmark.MessageBenchmarkStats;
import com.griddynamics.gridkit.coherence.patterns.message.benchmark.PatternFacade;
import com.oracle.coherence.common.identifiers.Identifier;
import com.tangosol.net.Invocable;
import com.tangosol.net.Member;

public class TopicBenchmarkDispatcher extends Dispatcher<MessageExecutionMark,
														 InvocationServiceStats<MessageBenchmarkStats>,
														 TopicBenchmarkParams>
{
	protected final PatternFacade facade;
	
	protected Invocable invocableWorker;
	
	public TopicBenchmarkDispatcher(Set<Member> members, PatternFacade facade)
	{
		super(members, facade.getInvocationService());
		
		this.facade = facade;
	}

	@Override
	protected void prepare(TopicBenchmarkParams benchmarkParams) throws Exception
	{
		if ((benchmarkParams.getTopicsCount() < 1) || (benchmarkParams.getTopicsPerMember() < 1) ||
			(benchmarkParams.getTopicsCount() < benchmarkParams.getTopicsPerMember() + 1))
		{
			throw new IllegalArgumentException("Wrong topicsCount(" + benchmarkParams.getTopicsCount() + ") or topicsPerMember(" + benchmarkParams.getTopicsPerMember() + ")");
		}
		
		List<Identifier> topics = new ArrayList<Identifier>(benchmarkParams.getTopicsCount());
		
		for (int i = 0; i < benchmarkParams.getTopicsCount(); ++i)
		{
			topics.add(facade.createTopic("topic-"+i));
		}
		
		Map<Member, List<Identifier>> workDistribution = distributeTopics(topics, benchmarkParams);
		
		invocableWorker = new TopicBenchmarkWorker(benchmarkParams, topics, workDistribution);
	}
	
	protected Map<Member, List<Identifier>> distributeTopics(List<Identifier> topics, TopicBenchmarkParams benchmarkParams)
	{
		Map<Member, List<Identifier>> result = new HashMap<Member, List<Identifier>>();
		
		List<Integer> load = new ArrayList<Integer>(benchmarkParams.getTopicsCount());
		for (int i = 0; i < benchmarkParams.getTopicsCount(); ++i)
		{
			load.add(0);
		}
		
		for(Member m : members)
		{
			List<Integer> indexes = findMinimalValues(load, benchmarkParams);
			
			List<Identifier> m_receive = new ArrayList<Identifier>(benchmarkParams.getTopicsPerMember());
			
			for (Integer i : indexes)
			{
				m_receive.add(topics.get(i));
				load.set(i, load.get(i) + 1);
			}
			
			result.put(m, m_receive);
		}
		
		return result;
	}
	
	protected List<Integer> findMinimalValues(List<Integer> l, TopicBenchmarkParams benchmarkParams)
	{
		List<Integer> res = new ArrayList<Integer>(benchmarkParams.getTopicsPerMember());
		
		List<Integer>        list = new ArrayList<Integer>(l);
		List<Integer> sorted_list = new ArrayList<Integer>(l); Collections.sort(sorted_list);
		
		for(int i = 0; i < benchmarkParams.getTopicsPerMember(); ++i)
		{
			Integer min = sorted_list.get(0);
			sorted_list.remove(0);
			
			int min_index = list.indexOf(min);
			
			list.set(min_index, Integer.MAX_VALUE);
			res.add(min_index);
		}
		
		return res;
	}
	
	@Override
	protected void calculateExecutionStatistics()
	{
		dispatcherResult.setJavaMsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.JavaMsExtractor()));
		
		dispatcherResult.setJavaNsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.JavaNsExtractor()));
		
		dispatcherResult.setCoherenceMsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.CoherenceMsExtractor()));
		
		dispatcherResult.setExecutionMarksProcessed(getDispatcherResultSise());
	}

	protected MessageBenchmarkStats calculateExecutionStatisticsInternal(MessageExecutionMark.MessageExecutionMarkTimeExtractor te)
	{
		Accamulator     latency = new Accamulator();
		
		Accamulator    sendTime = new Accamulator();
		Accamulator receiveTime = new Accamulator();
		
		int n = 0;
		
		for (List<MessageExecutionMark> l : workersResult)
		{
			for(MessageExecutionMark m : l)
			{
				n++;
				
				sendTime.add(te.getSendTime(m));
				receiveTime.add(te.getReceiveTime(m));
				
				latency.add(te.getReceiveTime(m) - te.getSendTime(m));
			}
		}
		
		MessageBenchmarkStats res = new MessageBenchmarkStats();
		
		res.setTotalTime((receiveTime.getMax() - sendTime.getMin()) / TimeUnit.SECONDS.toMillis(1));
		res.setThroughput(n / res.getTotalTime());
		
		res.setAverageLatency (latency.getMean());
		res.setLatencyVariance(latency.getVariance());
		res.setMinLatency     (latency.getMin());
		res.setMaxLatency     (latency.getMax());
		
		return res;
	}

	@Override
	protected InvocationServiceStats<MessageBenchmarkStats> createDispatcherResult()
	{
		return new InvocationServiceStats<MessageBenchmarkStats>();
	}

	@Override
	protected List<List<MessageExecutionMark>> createWorkersResult()
	{
		return new ArrayList<List<MessageExecutionMark>>();
	}

	@Override
	protected Invocable getInvocableWorker()
	{
		return invocableWorker;
	}
}
