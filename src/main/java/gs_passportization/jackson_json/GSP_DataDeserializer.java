package gs_passportization.jackson_json;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import gs_passportization.model.GSP_Data;

/**
 * Jackson JSON deserializer for {@link GSP_Data} objects.
 */
public class GSP_DataDeserializer extends StdDeserializer<GSP_Data> {
	
	private static final Logger logger = LoggerFactory.getLogger(GSP_DataDeserializer.class);

    public GSP_DataDeserializer() { 
        this(null); 
    }

    public GSP_DataDeserializer(Class<?> vc) { 
        super(vc); 
    }

    @Override 
    public GSP_Data deserialize(JsonParser jp, DeserializationContext ctxt) 
            throws IOException, JsonProcessingException {
        
        JsonNode tree = jp.getCodec().readTree(jp);
        GSP_Data rrData = new GSP_Data();
    	
        // Parse identifiers and coordinates safely
        rrData.setId(parseIntSafe(tree.path("complex").asText()));
        rrData.setLatitude(parseDoubleSafe(tree.path("latitude").asText()));
        rrData.setLongitude(parseDoubleSafe(tree.path("longitude").asText()));

        // Fallback to empty string if fields are missing or empty
        rrData.setAddress(tree.path("mailaddress").asText());
        rrData.setDistrict(tree.path("district").asText());
        
        // Handle "fuels" array format if applicable
        if (tree.has("fuels") && tree.get("fuels").isArray()) {
            String fuelsString = StreamSupport
                    .stream(tree.get("fuels").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.joining(", "));
            
            rrData.setFuels(fuelsString);
        }

        return rrData;
    }
    
    /**
     * Safely parses an Integer from a dirty string.
     * Returns null if the string is empty or invalid.
     */
    private Integer parseIntSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null; // Return null for exact state mapping
        }
        
        // Retain only digits and an optional leading minus sign
        String numbersOnly = value.replaceAll("[^0-9\\-]", "");
        
        if (numbersOnly.isEmpty() || numbersOnly.equals("-")) {
            // TODO: Replace System.err with proper logger.warn(...)
        	logger.warn("Warning - Unable to extract a number from the string '" + value + "'");
            //System.err.println("Warning - Unable to extract a number from the string '" + value + "'");
            return null; 
        }
        
        try {
            return Integer.parseInt(numbersOnly);
        } catch (NumberFormatException e) {
            System.err.println("Error - The number '" + numbersOnly + "' exceeds Integer range");
            return null;
        }
    }
    
    /**
     * Safely parses a Double from a dirty string using regex.
     * Returns null if no valid floating-point number is found.
     */
    private Double parseDoubleSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null; // Return null for exact state mapping
        }
        
        // Match optional minus, digits, dot, and decimal digits
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+\\.\\d+").matcher(value);
        String numbersOnly = matcher.find() ? matcher.group() : "";
        
        if (numbersOnly.isEmpty() || numbersOnly.equals("-")) {
        	logger.warn("Warning - Unable to extract a number from the string '" + value + "'");
            //System.err.println("Warning - Unable to extract a number from the string '" + value + "'");
            return null; 
        }
        
        try {
            return Double.parseDouble(numbersOnly);
        } catch (NumberFormatException e) {
            System.err.println("Error - The number '" + numbersOnly + "' exceeds Double range");
            return null;
        }
    }
}

