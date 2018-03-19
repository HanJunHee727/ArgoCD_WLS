/* Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved. */
package oracle.kubernetes.operator;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.ThreadedWatcher;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;

/**
 * This test class verifies the behavior of the DomainWatcher.
 */
public class DomainWatcherTest extends WatcherTestBase implements WatchingEventDestination<Domain> {


  private static final int INITIAL_RESOURCE_VERSION = 456;

  @Override
  public void eventCallback(Watch.Response<Domain> response) {
    recordCallBack(response);
  }


  @Test
  public void initialRequest_specifiesStartingResourceVersion() throws Exception {
      sendInitialRequest(INITIAL_RESOURCE_VERSION);

      assertThat(StubWatchFactory.getRecordedParameters().get(0),
                      hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)));
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
      return (T) new Domain().withMetadata(metaData);
  }

  @Override
  protected ThreadedWatcher createWatcher(String nameSpace, AtomicBoolean stopping, int initialResourceVersion) {
      return DomainWatcher.create(nameSpace, Integer.toString(initialResourceVersion), this, stopping);
  }
}
