package app.freerouting.settings;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class RouterOptimizerSettings implements Serializable
{
  @SerializedName("enabled")
  public transient boolean enabled = false;
  @SerializedName("algorithm")
  public String algorithm = "freerouting-optimizer";
  @SerializedName("max_passes")
  public int maxPasses = 100;
  @SerializedName("max_threads")
  public int maxThreads = Math.max(1, Runtime
      .getRuntime()
      .availableProcessors() - 1);
  @SerializedName("improvement_threshold")
  public float optimizationImprovementThreshold = 0.01f;
  @SerializedName("parallel_router_instances")
  public int parallelAutorouterInstances = 1;
  /**
   * Additional ripup cost factor applied during the first optimization pass.
   */
  @SerializedName("initial_ripup_cost_factor")
  public int initialRipupCostFactor = 10;
  /**
   * Reduction factor used when routing traces during optimization.
   */
  @SerializedName("trace_ripup_reduction")
  public double traceRipupReduction = 0.6;
  public transient BoardUpdateStrategy boardUpdateStrategy = BoardUpdateStrategy.GREEDY;
  public transient String hybridRatio = "1:1";
  public transient ItemSelectionStrategy itemSelectionStrategy = ItemSelectionStrategy.PRIORITIZED;
}