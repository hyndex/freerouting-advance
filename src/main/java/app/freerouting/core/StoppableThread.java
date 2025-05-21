package app.freerouting.core;

import app.freerouting.datastructures.Stoppable;

/**
 * Used for running an interactive action in a separate thread, that can be stopped by the user.
 */
public abstract class StoppableThread extends Thread implements Stoppable
{
  /**
   * Indicates that the entire routing job should stop. When this flag is set the
   * autorouter as well as any subsequent optimizer passes will be aborted.
   */
  private boolean stop_requested = false;

  /**
   * Indicates that only the autorouter should be aborted. The optimizer is still
   * allowed to continue running. {@code stop_requested} implies this flag but it
   * can be set independently when only the autorouter needs to stop.
   */
  private boolean stop_auto_router = false;

  /**
   * Creates a new instance of InteractiveActionThread
   */
  protected StoppableThread()
  {
  }

  protected abstract void thread_action();

  @Override
  public void run()
  {
    thread_action();
  }

  // Request the thread to stop including the fanout, autorouter and optimizer tasks
  @Override
  public synchronized void requestStop()
  {
    stop_requested = true;
    stop_auto_router = true;
  }

  @Override
  public synchronized boolean isStopRequested()
  {
    return stop_requested;
  }

  /**
   * Request the thread to stop only the autorouter. The optimizer and other
   * tasks are allowed to continue.
   */
  public synchronized void request_stop_auto_router()
  {
    stop_auto_router = true;
  }

  /**
   * Returns {@code true} if the autorouter should be aborted.
   */
  public synchronized boolean is_stop_auto_router_requested()
  {
    return stop_auto_router;
  }
}