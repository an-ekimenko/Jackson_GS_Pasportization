package gs_passportization;

import java.io.File;

import java.io.IOException;
import java.io.InputStream;

import java.io.UnsupportedEncodingException;

import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import gs_passportization.config.GSPApiConfig;
import gs_passportization.hibernate.GSP_DAO;
import gs_passportization.jackson_json.GSP_DataDeserializer;
import gs_passportization.model.GSP_Data;
import gs_passportization.model.GSP_Entity;
import my.CertUpdater;


import java.util.Base64;
import java.nio.file.Paths;
import java.net.URI;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class GSP_App {

	private static GSP_Data rrData;
	private static List<GSP_Data> rrDataList = new ArrayList();
	private static List<GSP_Entity> rrEntities;

	static {
		String relativeCacertsPath = "/lib/security/cacerts".replace("/", File.separator);
		String keyStoreFileName = System.getProperty("java.home") + relativeCacertsPath;
        System.setProperty("javax.net.ssl.trustStore", keyStoreFileName);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }
	
	private static Logger logger = null;
	
	public static void main(String[] args) throws OAuthSystemException, OAuthProblemException, IOException {

		try {
		    /**
		     * Get the URL and convert it to a URI to decode spaces 
		     * and remove redundant slashes automatically.
		     */ 
		    URI uri = GSP_App.class.getProtectionDomain()
		    		                          .getCodeSource()
		    		                          .getLocation()
		    		                          .toURI();
		        
		    // Paths.get(uri) handles path conversion safely across all operating systems.
		    Path currentPath = Paths.get(uri);

		    String pathStr = currentPath.toString();
		    if (!pathStr.contains(".jar")) {
		    	// If it’s a JAR, take its parent directory (the target folder).
		        pathStr = pathStr.substring(0, pathStr.lastIndexOf(java.io.File.separator + "target"));
		    } if (pathStr.contains(java.io.File.separator + "target")) {
			     // If running in an IDE/Maven context, navigate up from the 'target' directory.
			     pathStr = pathStr.substring(0, pathStr.lastIndexOf(java.io.File.separator + "target"));
			 }

		    // Construct cross-platform log file paths.
		    String commonsLog = Paths.get(pathStr, "logs", "commons.log").toString();
			String p6spyLog = Paths.get(pathStr, "logs", "p6spy.log").toString();
		    
		    System.out.println("commonLogFilename -> " + commonsLog);
		    System.setProperty("commonLogFilename", commonsLog);
		    
		    System.out.println("p6spyLogFilename -> " + p6spyLog);
		    System.setProperty("p6spyLogFilename", p6spyLog);
		    
		} catch (Exception e) {
		    e.printStackTrace();
		}

		// Reconfigure Log4j2 with the specified XML configuration.
		LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
		try {
		    loggerContext.setConfigLocation(GSP_App.class.getClassLoader().getResource("Log4j2.xml").toURI());
		} catch (URISyntaxException e) { 
		    e.printStackTrace(); 
		}

		loggerContext.reconfigure();

		// Verify and print active loggers and configuration details.
		System.out.print("\nLoggers defined in Log4j2.xml -> ");		
		Map<String, LoggerConfig> map = loggerContext.getConfiguration().getLoggers();
		for(String key : map.keySet()) {
		    System.out.print("[" + map.get(key).getName() + "]; ");
		}
		System.out.println();

		org.apache.logging.log4j.core.config.Configuration conf = loggerContext.getConfiguration();
		if (conf instanceof DefaultConfiguration) {
		    System.out.println("\nConfiguration is instance of DefaultConfiguration");
		} else {
		    System.out.println("\nConfiguration is instance of XmlConfiguration");
		}

		logger = LogManager.getLogger(GSP_App.class); 
		System.out.println("\nConfiguration properties:");

		Map<String, String> props = conf.getProperties();
		for (String key : props.keySet()) {
		    System.out.println("\t" + key + " -> " + props.get(key));
		}
		
        try {
            
            // Load credentials from properties.yaml using Jackson YAML Dataformat
            logger.info("Loading infrastructure properties from configuration file...");
            
            YAMLFactory yamlFactory = new YAMLFactory();
            ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
            
            // Locate and read the properties file from the application classpath resources
            InputStream configStream = null;
			try {
				configStream = GSP_App.class.getClassLoader().getResourceAsStream("properties.yaml");
			} catch (Exception e) {
				e.printStackTrace();
			}
            if (configStream == null) {
                throw new java.io.FileNotFoundException("Configuration file 'properties.yaml' not found in classpath.");
            }
            
            JsonNode rootNode = yamlMapper.readTree(configStream);
            JsonNode apiNode = rootNode.get("gs_passportization-api");
            
            if (apiNode == null) {
                throw new IllegalArgumentException("Missing required configuration section [gs_passportization-api]"
                		+ " in properties.yaml");
            }
            
            // Map the targeted JSON/YAML node configuration values directly to the POJO container
            GSPApiConfig config = yamlMapper.treeToValue(apiNode, GSPApiConfig.class);

            // Construct the final target URL string
            String endpoint = config.getEndpoint();
            String urlString = endpoint;
            
            // Initialize the authentication header payload variable
            String authHeader = null;
            
            GSPApiConfig.AuthConfig auth = config.getAuth();
            
            // Obtain a preconfigured SSLContext from a custom certificate manager.
            SSLContext sslContext = SSLContext.getDefault(); 
      
            // Verify if authentication configurations are present
            if (auth == null || auth.getType() == null) {
                logger.warn("Authentication configuration is missing. Proceeding without auth headers.");
            } else { 
                /**
                 * Dynamically generate appropriate authentication headers and dispatch credentials 
                 * to the certificate manager to ensure secure API resource access.
                 */
                String authType = auth.getType().toLowerCase();
                switch (authType) {
                case "basic":
                    String username = auth.getUsername();
                    String password = auth.getPassword();

                    // Generate standard HTTP Basic Authentication token
                    String authString = username + ":" + password;
                    String encodedAuth = Base64.getEncoder()
                                               .encodeToString(authString.getBytes(StandardCharsets.UTF_8));
                   
                    authHeader = "Basic " + encodedAuth;

                    // Propagate credentials to the dynamic SSL certificate lifecycle manager
                    CertUpdater.updateByBasicAuth(urlString, username + ":" + password, "BASIC");
                    break;

                case "token":
                    String token = auth.getToken();

                    // Prepare the standard Bearer token authorization header
                    authHeader = "Bearer " + token;

                    // Pass the token signature to the dynamic SSL certificate lifecycle manager
                    CertUpdater.updateByBasicAuth(urlString, token, "TOKEN");
                    break;

                case "none":
                    // Handle unauthenticated public access contexts explicitly
                    logger.debug("Executing request without authentication headers [auth type: none]");
                    break;

                default:
                    // Guard clause against unsupported or obsolete authentication protocols
                    logger.warn("Unsupported authentication type detected: '{}'. No auth headers applied.", authType);
                    break;
                }
            }

            // Bind the socket factory to the dynamically initialized hot-swappable SSLContext
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
            PoolingHttpClientConnectionManager connManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            // Establish deterministic connect and response read timeouts (30 seconds)
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(30))
                    .setResponseTimeout(Timeout.ofSeconds(30))
                    .build();

            // Configure resilient fault tolerance via an exponential backoff retry strategy (5 attempts)
            DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(
                    5, 
                    TimeValue.ofSeconds(30)
            );
            
            // Build the scoped HTTP client instance injected with enterprise-grade operational policies
            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connManager)
                    .setDefaultRequestConfig(requestConfig)
                    .setRetryStrategy(retryStrategy)
                    .build()) 
            {
                // Initialize the structural HTTP GET request
                ClassicHttpRequest request = ClassicRequestBuilder.get(urlString)
                        .setHeader("Accept", "application/json")
                        .build();

                if (authHeader != null) {
                    request.setHeader("Authorization", authHeader);
                }
                
                logger.info("Executing API request to: " + urlString);

                // Execute the request; transient failures are handled seamlessly inside the retry pipeline
                String responseBody = httpClient.execute(request, response -> {
                    int status = response.getCode();
                    System.out.println("\nHTTP response code = " + status);
                    
                    if (status >= 200 && status < 300) {
                        return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    } else {
                        throw new RuntimeException("The server returned an error code: " + status 
                                + ". Headers: " + response.getHeaders().toString());
                    }
                });
                
                // Display the formatted response layout for internal telemetry diagnostics
                //System.out.println("\nResponse body:\n" + responseBody.replace("},", "},\n"));

                // Parse the verified JSON payload into intermediate transfer objects
                rrDataList = parse_json(responseBody);

                
                /**
                 * Transform raw data set structures into persistent domain models via stream processing, 
                 * leaving only objects with unique IDs in the list.
                 */
                if (rrDataList.size() > 0) {
                    //  Initialize a thread-safe Set to track the IDs that have already been encountered.
                    Set<Integer> seenIds = ConcurrentHashMap.newKeySet();

                    rrEntities = rrDataList.stream()
                        // Filter the list, keeping only those objects whose ID has not been encountered before.
                        .filter(o -> seenIds.add(o.getId())) 
                        .map(o -> new GSP_Entity(
                                o.getId(),
                                "", // Gas station description field defaults to empty string
                                o.getAddress(),
                                o.getFuels(),
                                o.getLatitude(), 
                                o.getLongitude(),
                                o.getDistrict()
                        ))
                        .collect(Collectors.toList());
                }
                
                // Output mapped domain identities to system logs for audit trail tracking
                /*rrEntities.stream().forEach(x -> System.out.println(x.getId() + "; " +
                        x.getAddress() + "; " + x.getDistrict()));
                */

                // Forward structural entities to the database batch ingestion layer
                writeParsedJSONToDatabase(rrEntities);
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }

        System.exit(0);

    }
	
    /**
     * Parses the raw JSON response string into a list of GSP_Data DTOs.
     * Configured defensively to bypass unknown structural fields.
     */
    public static List<GSP_Data> parse_json(String json) throws JsonMappingException, JsonProcessingException {		
        
        ObjectMapper objectMapper = new ObjectMapper();

        // Prevent runtime failures if the upstream API introduces unexpected properties
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Register the custom deserializer for gas station data extraction
        SimpleModule module = new SimpleModule();
        module.addDeserializer(GSP_Data.class, new GSP_DataDeserializer());
        objectMapper.registerModule(module);
        
        return objectMapper.readValue(json, new TypeReference<List<GSP_Data>>() {});
    }
    
    /**
     * Persists the parsed and mapped Hibernate entities into the MS SQL staging database
     * using optimized batch processing operations.
     */
    public static void writeParsedJSONToDatabase(List<GSP_Entity> acnEntities) throws UnsupportedEncodingException {
        GSP_DAO dao = new GSP_DAO();
        // Execute a transaction-aware batch save/update operation
        dao.saveOrUpdateBatch(acnEntities);
    }

}
