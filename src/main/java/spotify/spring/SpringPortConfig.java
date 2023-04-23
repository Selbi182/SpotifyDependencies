package spotify.spring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

import spotify.api.SpotifyDependenciesSettings;

@Configuration
public class SpringPortConfig implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

  private Integer port;

  @Value("${server.port:#{null}}")
  private String serverPortFromApplicationProperties;

  private final SpotifyDependenciesSettings spotifyDependenciesSettings;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  SpringPortConfig(SpotifyDependenciesSettings spotifyDependenciesSettings) {
    this.spotifyDependenciesSettings = spotifyDependenciesSettings;
  }

  public int getPort() {
    if (port == null) {
      port = serverPortFromApplicationProperties != null
         ? Integer.parseInt(serverPortFromApplicationProperties)
         : spotifyDependenciesSettings.port();
    }
    return port;
  }

  public void customize(ConfigurableServletWebServerFactory factory) {
    factory.setPort(getPort());
  }

}