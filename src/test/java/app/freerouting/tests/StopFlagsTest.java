package app.freerouting.tests;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StopFlagsTest extends TestBasedOnAnIssue {
  @Test
  void test_autorouter_stop_only() throws Exception {
    RoutingJob job = GetRoutingJob("Issue026-J2_reference.dsn");
    job.routerSettings.optimizer.enabled = true;

    scheduler.enqueueJob(job);
    job.state = RoutingJobState.READY_TO_START;

    while (job.thread == null) {
      Thread.sleep(50);
    }
    Thread.sleep(200);
    job.thread.request_stop_auto_router();

    job = RunRoutingJob(job, job.routerSettings);
    assertEquals(RoutingJobState.COMPLETED, job.state, "Job should finish when only the autorouter is stopped");
    assertNotNull(GetBoardStatistics(job));
  }

  @Test
  void test_request_stop_cancels_job() throws Exception {
    RoutingJob job = GetRoutingJob("Issue026-J2_reference.dsn");
    job.routerSettings.setRunRouter(false);
    job.routerSettings.optimizer.enabled = true;

    scheduler.enqueueJob(job);
    job.state = RoutingJobState.READY_TO_START;

    while (job.thread == null) {
      Thread.sleep(50);
    }
    Thread.sleep(200);
    job.thread.requestStop();

    job = RunRoutingJob(job, job.routerSettings);
    assertEquals(RoutingJobState.CANCELLED, job.state, "Job should be cancelled when stop is requested");
  }
}
