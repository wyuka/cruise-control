/*
 * Copyright 2020 LinkedIn Corp. Licensed under the BSD 2-Clause License (the "License"). See License in the project root for license information.
 */

package com.linkedin.kafka.cruisecontrol.monitor.sampling;

import java.util.Map;
import java.util.Set;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.TopicPartition;

import com.linkedin.cruisecontrol.metricdef.MetricDef;
import com.linkedin.kafka.cruisecontrol.config.BrokerCapacityConfigResolver;
import com.linkedin.kafka.cruisecontrol.config.constants.MonitorConfig;
import com.linkedin.kafka.cruisecontrol.exception.SamplingException;
import com.linkedin.kafka.cruisecontrol.metricsreporter.metric.CruiseControlMetric;

import static com.linkedin.kafka.cruisecontrol.monitor.sampling.MetricFetcherManager.BROKER_CAPACITY_CONFIG_RESOLVER_OBJECT_CONFIG;

/**
 * This is a base implementation of a MetricSampler that can be overriden by concrete Metric Sampler
 * implementations. It takes care of the common logic of initializing a {@link CruiseControlMetricsProcessor},
 * and then using it to record every individual {@link CruiseControlMetric}, and finally convert all
 * of these into {@link com.linkedin.kafka.cruisecontrol.monitor.sampling.MetricSampler.Samples}.
 */
abstract public class AbstractMetricSampler implements MetricSampler {
    private CruiseControlMetricsProcessor _metricsProcessor;

    @Override
    public void configure(Map<String, ?> configs) {
        BrokerCapacityConfigResolver capacityResolver =
            (BrokerCapacityConfigResolver) configs.get(BROKER_CAPACITY_CONFIG_RESOLVER_OBJECT_CONFIG);
        if (capacityResolver == null) {
            throw new IllegalArgumentException(
                "Metrics reporter sampler configuration is missing broker capacity config resolver object.");
        }
        boolean allowCpuCapacityEstimation = (Boolean) configs.get(
            MonitorConfig.SAMPLING_ALLOW_CPU_CAPACITY_ESTIMATION_CONFIG);
        _metricsProcessor = new CruiseControlMetricsProcessor(capacityResolver, allowCpuCapacityEstimation);
    }

    @Override
    public Samples getSamples(Cluster cluster, Set<TopicPartition> assignedPartitions, long startTimeMs,
        long endTimeMs, SamplingMode mode, MetricDef metricDef, long timeout) throws SamplingException {
        MetricSamplerOptions metricSamplerOptions = new MetricSamplerOptions(
            cluster, assignedPartitions, startTimeMs, endTimeMs, mode, metricDef, timeout);
        return getSamples(metricSamplerOptions);
    }

    @Override
    public Samples getSamples(MetricSamplerOptions metricSamplerOptions) throws SamplingException {
        int totalMetricsAdded = addMetrics(metricSamplerOptions);

        try {
            if (totalMetricsAdded > 0) {
                return _metricsProcessor.process(metricSamplerOptions.cluster(),
                    metricSamplerOptions.assignedPartitions(), metricSamplerOptions.mode());
            } else {
                return MetricSampler.EMPTY_SAMPLES;
            }
        } finally {
            _metricsProcessor.clear();
        }
    }

    /**
     * This method will be called to add all the {@link CruiseControlMetric}s
     * for a cluster in one sampling period.
     *
     * Concrete metric sampler implementations can implement this method according to
     * their corresponding business logic of fetching metrics for the cluster.
     *
     * @param metricSamplerOptions Object that encapsulates all the options to be used for sampling metrics.
     * @return Total number of metrics sampled.
     */
    abstract protected int addMetrics(MetricSamplerOptions metricSamplerOptions) throws SamplingException;
    /**
     * This method records a single individual metric obtained from the cluster during a sampling period.
     * The {@link #addMetrics(MetricSamplerOptions)} method implemented in the conrete metric sampler
     * class will call this method for every metric it obtains for a single sampling period for the cluster.
     *
     * @param metric Individual CruiseControlMetric being recorded by the Metric Sampler.
     */
    protected void addMetric(CruiseControlMetric metric) {
        this._metricsProcessor.addMetric(metric);
    }
}
