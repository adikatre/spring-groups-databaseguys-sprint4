package com.open.spring.mvc.groups;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.*;

@Service
public class FlaskPersonaService {
    
    private final RestTemplate restTemplate;
    private final String FLASK_URL = "http://localhost:8001/api/persona";
    
    public FlaskPersonaService() {
        // Configure RestTemplate with timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 seconds
        factory.setReadTimeout(30000);    // 30 seconds
        this.restTemplate = new RestTemplate(factory);
    }
    
    /**
     * Call Flask to form optimal groups based on personas
     */
    public List<Map<String, Object>> formGroups(List<String> userUids, int groupSize) {
        String url = FLASK_URL + "/form-groups";
        
        // Build request
        Map<String, Object> request = new HashMap<>();
        request.put("user_uids", userUids);
        request.put("group_size", groupSize);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        try {
            System.out.println("Calling Flask API: " + url);
            System.out.println("Request: " + request);
            
            // Call Flask API
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                Map.class
            );
            
            System.out.println("Flask response status: " + response.getStatusCode());
            
            Map<String, Object> body = response.getBody();
            if (body == null) {
                System.err.println("Flask returned null body");
                return null;
            }
            
            List<Map<String, Object>> groups = (List<Map<String, Object>>) body.get("groups");
            System.out.println("Received " + (groups != null ? groups.size() : 0) + " groups from Flask");
            
            return groups;
            
        } catch (Exception e) {
            System.err.println("Error calling Flask: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}