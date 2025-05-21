package app.freerouting.api;

import app.freerouting.management.authentication.AuthenticationService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

public class BaseController
{
  @Context
  private HttpHeaders httpHeaders;

  public BaseController()
  {
  }

  protected UUID AuthenticateUser()
  {
    String userIdString = httpHeaders.getHeaderString("Freerouting-Profile-ID");
    String userEmailString = httpHeaders.getHeaderString("Freerouting-Profile-Email");

    if (((userIdString == null) || (userIdString.isEmpty())) && ((userEmailString == null) || (userEmailString.isEmpty())))
    {
      throw new WebApplicationException(
          Response
              .status(Response.Status.UNAUTHORIZED)
              .entity("{\"error\":\"Freerouting-Profile-ID or Freerouting-Profile-Email HTTP request header must be set.\"}")
              .build());
    }

    UUID userId = null;

    // We need to get the userId from the e-mail address first
    if ((userIdString != null) && (!userIdString.isEmpty()))
    {
      try
      {
        userId = UUID.fromString(userIdString);
      } catch (IllegalArgumentException e)
      {
        // We couldn't parse the userId, so we fall back to e-mail address
      }
    }

    AuthenticationService authService = new AuthenticationService(app.freerouting.Freerouting.globalSettings.authServiceSettings.endpoint);

    if ((userEmailString != null) && (!userEmailString.isEmpty()) && (userId == null))
    {
      userId = authService.resolveUserId(userEmailString);
    }

    if (userId == null)
    {
      throw new WebApplicationException(
          Response
              .status(Response.Status.UNAUTHORIZED)
              .entity("{\"error\":\"The user couldn't be authenticated based on the Freerouting-Profile-ID or Freerouting-Profile-Email HTTP request header values.\"}")
              .build());
    }

    if (!authService.validateUser(userId, userEmailString))
    {
      throw new WebApplicationException(
          Response
              .status(Response.Status.UNAUTHORIZED)
              .entity("{\"error\":\"Authentication failed.\"}")
              .build());
    }

    return userId;
  }
}