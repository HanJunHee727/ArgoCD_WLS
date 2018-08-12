// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("SameParameterValue")
class ChartITBase {
  private static List<ProcessedChart> charts = new ArrayList<>();

  ProcessedChart getChart(String chartName) {
    return getChart(chartName, Collections.emptyMap());
  }

  ProcessedChart getChart(String chartName, Map<String, Object> valueOverrides) {
    for (ProcessedChart chart : charts) {
      if (chart.matches(chartName, valueOverrides)) {
        return chart;
      }
    }

    ProcessedChart chart = new ProcessedChart(chartName, valueOverrides);
    charts.add(chart);
    return chart;
  }
}
