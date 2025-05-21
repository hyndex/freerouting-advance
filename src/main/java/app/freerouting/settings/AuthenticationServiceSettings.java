package app.freerouting.settings;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class AuthenticationServiceSettings implements Serializable {
  @SerializedName("endpoint")
  public String endpoint = "http://localhost:37865";
}
