package com.open.spring.mvc.groups;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

@Service
public class FlaskPersonaService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final String FLASK_URL = "http://localhost:8001/api/persona";
    
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
            // Call Flask API
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                Map.class
            );
            
            Map<String, Object> body = response.getBody();
            return (List<Map<String, Object>>) body.get("groups");
            
        } catch (Exception e) {
            System.err.println("Error calling Flask: " + e.getMessage());
            return null;
        }
    }
}