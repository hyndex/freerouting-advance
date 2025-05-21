package app.freerouting.tests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class OptimizerRipupSettingsTest extends TestBasedOnAnIssue {
  @Test
  void test_ripup_settings_affect_routing() {
    var job1 = GetRoutingJob("Issue029-hw48na.dsn");
    job1.routerSettings.optimizer.enabled = true;
    job1.routerSettings.optimizer.initialRipupCostFactor = 1;
    job1.routerSettings.optimizer.traceRipupReduction = 1.0;
    job1 = RunRoutingJob(job1, job1.routerSettings);
    var stats1 = GetBoardStatistics(job1);

    var job2 = GetRoutingJob("Issue029-hw48na.dsn");
    job2.routerSettings.optimizer.enabled = true;
    job2.routerSettings.optimizer.initialRipupCostFactor = 20;
    job2.routerSettings.optimizer.traceRipupReduction = 0.3;
    job2 = RunRoutingJob(job2, job2.routerSettings);
    var stats2 = GetBoardStatistics(job2);

    assertNotEquals(stats1.traces.totalLength, stats2.traces.totalLength,
        "Different ripup settings should lead to different routing results");
  }
}
