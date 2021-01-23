/*
 * Copyright (C) 2021, Logical Clocks AB. All rights reserved
 */

package io.hops.hopsworks.featurestore;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.hops.hopsworks.provenance.state.ProvStateControllerEE;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
@TransactionAttribute(TransactionAttributeType.NEVER)
public class KeywordsUsedCache {
  // This is a temporary cached used by Hopsworks to avoid hitting elasticsearch with each
  // Query. We use Guava cache with a single key instead of the memoize utilities, so that
  // we can explicitly invalidate the cache when keywords are added or removed
  // The source of truth will remain elastic.
  @EJB
  private ProvStateControllerEE provStateControllerEE;

  private static final String KEY = "key";
  private LoadingCache<String, List<String>> cache;

  @PostConstruct
  public void init() {
    cache = Caffeine.newBuilder()
        .expireAfterWrite(2, TimeUnit.MINUTES)
        .build(k -> provStateControllerEE.keywordAggregation());
  }

  public List<String> getUsedKeywords() throws ExecutionException {
    return cache.get(KEY);
  }

  public void invalidateCache() {
    cache.invalidate(KEY);
  }
}
