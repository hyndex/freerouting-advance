package app.freerouting.tests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParallelAutorouterTest extends TestBasedOnAnIssue {
  @Test
  void test_parallel_autorouter_instances() {
    var job = GetRoutingJob("Issue229-display-8-digit-hc595.dsn");
    job.routerSettings.optimizer.parallelAutorouterInstances = 2;

    job = RunRoutingJob(job, job.routerSettings);

    var statsAfter = GetBoardStatistics(job);

    assertEquals(0, statsAfter.connections.incompleteCount, "The incomplete count should be 0");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}
