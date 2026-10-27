package com.logiopt.service;

import com.logiopt.exception.InvalidStrategyException;
import com.logiopt.model.Recommendation;
import com.logiopt.model.RouteEvaluation;
import com.logiopt.strategy.BalancedStrategy;
import com.logiopt.strategy.CheapestStrategy;
import com.logiopt.strategy.FastestStrategy;
import com.logiopt.strategy.GreenestStrategy;
import com.logiopt.strategy.OptimizationStrategy;
import com.logiopt.util.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of the four optimization strategies keyed CHEAPEST, FASTEST, GREENEST, BALANCED.
 */
public class RecommendationService {

    private final Map<String, OptimizationStrategy> strategies = new LinkedHashMap<>();

    /**
     * Creates the registry with the default strategies and Balanced weights.
     */
    public RecommendationService() {
        register(new CheapestStrategy());
        register(new FastestStrategy());
        register(new GreenestStrategy());
        register(new BalancedStrategy());
    }

    private void register(OptimizationStrategy strategy) {
        strategies.put(strategy.getKey(), strategy);
    }

    /**
     * Looks up a strategy by key (trimmed, case-insensitive). There is no fallback.
     *
     * @param key strategy key
     * @return matching strategy
     * @throws InvalidStrategyException if the key is null, blank or unknown
     */
    public OptimizationStrategy requireStrategy(String key) throws InvalidStrategyException {
        if (Strings.isBlank(key)) {
            throw new InvalidStrategyException("Strategy key is required. Valid keys: " + strategies.keySet());
        }
        OptimizationStrategy strategy = strategies.get(key.trim().toUpperCase(Locale.ROOT));
        if (strategy == null) {
            throw new InvalidStrategyException("Unknown strategy '" + key + "'. Valid keys: " + strategies.keySet());
        }
        return strategy;
    }

    /**
     * Returns all strategies in registration order (feeds the strategy dropdown).
     *
     * @return unmodifiable list of strategies
     */
    public List<OptimizationStrategy> getStrategies() {
        return Collections.unmodifiableList(new ArrayList<>(strategies.values()));
    }

    /**
     * Runs the named strategy on the given evaluations.
     *
     * @param key         strategy key
     * @param evaluations evaluations to rank
     * @return recommendation
     * @throws InvalidStrategyException if the key is invalid
     */
    public Recommendation recommend(String key, List<RouteEvaluation> evaluations) throws InvalidStrategyException {
        return requireStrategy(key).recommend(evaluations);
    }
}
